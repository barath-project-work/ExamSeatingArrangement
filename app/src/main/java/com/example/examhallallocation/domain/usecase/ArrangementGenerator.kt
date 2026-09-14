package com.example.examhallallocation.domain.usecase

import com.example.examhallallocation.domain.model.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Result of a generation run: either a complete arrangement for the date or a human-readable failure. */
sealed interface GenerationResult {
    data class Success(val arrangement: Arrangement) : GenerationResult
    data class Failure(val errors: List<String>) : GenerationResult
}

/**
 * Arrangement generation engine implementing the college rules:
 * - Students per year are ordered by configured position; fixed 15-position batches with
 *   gaps preserved (a missing position is never back-filled by shifting later students).
 * - Phase 1 (2nd + 3rd + 4th): up to 12 halls, randomized mixed-year pairings.
 * - Phase 2 (2nd + 3rd): up to 8 halls.
 * - Phase 3 (3rd only): exactly 2 halls, no mixing.
 * - Hall exam capacity (normally 30) is never exceeded; occupancy may be lower.
 * - Invigilation: HOD excluded, coordinator max one duty day in the whole exam,
 *   one hall per teacher per day, workload balanced, consecutive days minimized,
 *   randomized among equally eligible teachers.
 */
@Singleton
class ArrangementGenerator(private val random: Random) {

    @Inject
    constructor() : this(Random.Default)

    companion object {
        const val BATCH_SIZE = 15
        const val PHASE_1_MAX_HALLS = 12
        const val PHASE_2_MAX_HALLS = 8
        const val PHASE_3_HALLS = 2
    }

    /** A 15-position batch of students of one year. Gaps in positions are preserved. */
    data class StudentBatch(
        val year: StudentYear,
        val startPosition: Int,
        val endPosition: Int,
        val students: List<Student>,
    )

    fun generate(
        examName: String,
        date: String,
        phase: ExamPhase,
        students: List<Student>,
        halls: List<Hall>,
        teachers: List<Teacher>,
        semesters: Map<StudentYear, Int>,
        existingArrangements: List<Arrangement>,
    ): GenerationResult {
        validateInputs(students, halls, teachers)?.let { return GenerationResult.Failure(it) }

        val activeStudents = students.filter { it.active }
        val activeHalls = halls.filter { it.active }
            .sortedWith(compareBy({ it.block }, { it.roomNumber }))
        val activeTeachers = teachers.filter { it.active }

        val yearPools = activeStudents
            .groupBy { it.year }
            .mapValues { (_, list) -> list.sortedBy { it.position } }

        val applicableYears = when (phase) {
            ExamPhase.PHASE_1 -> listOf(StudentYear.YEAR_2, StudentYear.YEAR_3, StudentYear.YEAR_4)
            ExamPhase.PHASE_2 -> listOf(StudentYear.YEAR_2, StudentYear.YEAR_3)
            ExamPhase.PHASE_3 -> listOf(StudentYear.YEAR_3)
        }.filter { (yearPools[it]?.size ?: 0) > 0 }

        if (applicableYears.isEmpty()) {
            return GenerationResult.Failure(
                listOf("No active students found for the selected date. Check student records and exam setup.")
            )
        }

        val batchesByYear = applicableYears.associateWith { year -> createBatches(yearPools.getValue(year)) }
        val hallPlan = planHalls(phase, batchesByYear, activeHalls)
        if (hallPlan.isEmpty()) {
            return GenerationResult.Failure(
                listOf(
                    "Not enough active examination halls to seat all students. " +
                        "Active halls available: ${activeHalls.size}."
                )
            )
        }

        val hallAssignments = hallPlan.flatMapIndexed { index, entry ->
            entry.blocks.map { block ->
                HallAssignment(
                    id = "ha_${date}_${entry.hall.id}_${block.year.name}",
                    hallId = entry.hall.id,
                    year = block.year,
                    semester = semesters[block.year] ?: defaultSemester(block.year),
                    startPosition = block.startPosition,
                    endPosition = block.endPosition,
                    studentIds = block.students.map { it.id },
                )
            }
        }

        val occupiedHalls = hallPlan.map { it.hall }
        val invigilatorAssignments = allocateInvigilators(date, occupiedHalls, activeTeachers, existingArrangements)
        if (invigilatorAssignments.size < occupiedHalls.size) {
            return GenerationResult.Failure(
                listOf(
                    "Not enough teachers available for invigilation on $date. " +
                        "Required: ${occupiedHalls.size}. Check that teachers are active and the HOD is not counted."
                )
            )
        }

        val arrangement = Arrangement(
            id = "arr_$date",
            examName = examName,
            date = date,
            phase = phase,
            hallAssignments = hallAssignments,
            invigilatorAssignments = invigilatorAssignments,
            status = ArrangementStatus.DRAFT,
        )

        val validation = ArrangementValidator.validate(
            arrangement, students, halls, teachers, existingArrangements + arrangement
        )
        if (!validation.isValid) return GenerationResult.Failure(validation.errors)

        return GenerationResult.Success(arrangement)
    }

    // ------------------------------------------------------------------
    // Batches
    // ------------------------------------------------------------------

    /**
     * Splits an ordered year list into fixed 15-position batches.
     * Example: with position 44 missing, the 31-45 batch still spans 31-45 and
     * contains positions 31..43 and 45 - later students are never shifted back.
     */
    fun createBatches(orderedStudents: List<Student>): List<StudentBatch> {
        if (orderedStudents.isEmpty()) return emptyList()
        val maxPosition = orderedStudents.maxOf { it.position }
        val batches = mutableListOf<StudentBatch>()
        var start = 1
        while (start <= maxPosition) {
            val end = start + BATCH_SIZE - 1
            val inBatch = orderedStudents.filter { it.position in start..end }
            if (inBatch.isNotEmpty()) {
                batches.add(StudentBatch(inBatch.first().year, start, end, inBatch))
            }
            start = end + 1
        }
        return batches
    }

    // ------------------------------------------------------------------
    // Hall planning
    // ------------------------------------------------------------------

    private data class HallPlanEntry(val hall: Hall, val blocks: MutableList<StudentBatch>)

    private fun planHalls(
        phase: ExamPhase,
        batchesByYear: Map<StudentYear, List<StudentBatch>>,
        activeHalls: List<Hall>,
    ): List<HallPlanEntry> = when (phase) {
        ExamPhase.PHASE_3 -> planPhase3(batchesByYear.getValue(StudentYear.YEAR_3), activeHalls)
        else -> planMixed(phase, batchesByYear, activeHalls)
    }

    /**
     * Phase 3: exactly two halls, 3rd-year batches split across them, no mixing.
     * Phase 3 rooms seat two students per bench (the college's day-7 practice), so the
     * per-hall limit is double the normal exam capacity - otherwise the whole 3rd year
     * could never fit in exactly two rooms.
     */
    private fun planPhase3(batches: List<StudentBatch>, halls: List<Hall>): List<HallPlanEntry> {
        if (batches.isEmpty() || halls.size < PHASE_3_HALLS) return emptyList()
        val chosen = halls.take(PHASE_3_HALLS)
        val plan = chosen.map { HallPlanEntry(it, mutableListOf()) }
        batches.forEachIndexed { index, batch ->
            plan[index % plan.size].blocks.add(batch)
        }
        return plan
    }

    /**
     * Phases 1 and 2: each hall receives batches from up to two different years.
     * To guarantee complete seating within the phase's hall limit, batches are drained
     * from the largest remaining year queues first (random among equal sizes), which
     * keeps year queues balanced and prevents a year from stranding leftover students.
     * A batch is only seated if the hall's exam capacity allows.
     */
    private fun planMixed(
        phase: ExamPhase,
        batchesByYear: Map<StudentYear, List<StudentBatch>>,
        halls: List<Hall>,
    ): List<HallPlanEntry> {
        val maxHalls = if (phase == ExamPhase.PHASE_1) PHASE_1_MAX_HALLS else PHASE_2_MAX_HALLS
        val queues = batchesByYear
            .filter { it.value.isNotEmpty() }
            .map { it.key to ArrayDeque(it.value) }
        if (queues.isEmpty()) return emptyList()

        val plan = mutableListOf<HallPlanEntry>()
        var hallIdx = 0
        while (queues.any { it.second.isNotEmpty() } && plan.size < maxHalls && hallIdx < halls.size) {
            val hall = halls[hallIdx]
            hallIdx++
            val entry = HallPlanEntry(hall, mutableListOf())

            var seated = 0
            repeat(2) {
                val fittingQueues = queues.filter { (year, queue) ->
                    queue.isNotEmpty() &&
                        entry.blocks.none { block -> block.year == year } &&
                        seated + queue.first().students.size <= hall.capacity
                }
                if (fittingQueues.isEmpty()) return@repeat
                val maxSize = fittingQueues.maxOf { it.second.size }
                val tied = fittingQueues.filter { it.second.size == maxSize }
                val chosen = tied[random.nextInt(tied.size)]
                val batch = chosen.second.removeFirst()
                entry.blocks.add(batch)
                seated += batch.students.size
            }
            if (entry.blocks.isEmpty()) break // no progress possible
            plan.add(entry)
        }
        // Any remaining batches mean the active halls cannot seat all students.
        if (queues.any { it.second.isNotEmpty() }) return emptyList()
        return plan
    }

    // ------------------------------------------------------------------
    // Invigilator allocation
    // ------------------------------------------------------------------

    private data class DutyStats(
        val totalDuties: Int,
        val hasDutyOnDate: Boolean,
        val consecutiveRun: Int,
        val coordinatorDaysUsed: Int,
    )

    private fun allocateInvigilators(
        date: String,
        halls: List<Hall>,
        teachers: List<Teacher>,
        existingArrangements: List<Arrangement>,
    ): List<InvigilatorAssignment> {
        if (halls.isEmpty()) return emptyList()

        val statsByTeacher = teachers.associate { it.id to computeStats(it, date, existingArrangements) }.toMutableMap()
        val assignments = mutableListOf<InvigilatorAssignment>()

        repeat(halls.size) { hallIndex ->
            val hall = halls[hallIndex]
            val candidates = teachers
                .filter { it.role != UserRole.HOD }
                .filter { !statsByTeacher.getValue(it.id).hasDutyOnDate }
                .filter { it.role != UserRole.EXAM_CELL_COORDINATOR || statsByTeacher.getValue(it.id).coordinatorDaysUsed == 0 }
            if (candidates.isEmpty()) return assignments

            val minTotal = candidates.minOf { statsByTeacher.getValue(it.id).totalDuties }
            val tied = candidates.filter { statsByTeacher.getValue(it.id).totalDuties == minTotal }
            val minRun = tied.minOf { statsByTeacher.getValue(it.id).consecutiveRun }
            val finalists = tied.filter { statsByTeacher.getValue(it.id).consecutiveRun == minRun }

            val chosen = finalists[random.nextInt(finalists.size)]
            assignments.add(InvigilatorAssignment(id = "inv_${date}_${hall.id}", hallId = hall.id, teacherId = chosen.id))

            val current = statsByTeacher.getValue(chosen.id)
            statsByTeacher[chosen.id] = current.copy(
                totalDuties = current.totalDuties + 1,
                hasDutyOnDate = true,
                consecutiveRun = current.consecutiveRun + 1,
                coordinatorDaysUsed = if (chosen.role == UserRole.EXAM_CELL_COORDINATOR) {
                    current.coordinatorDaysUsed + 1
                } else {
                    current.coordinatorDaysUsed
                },
            )
        }
        return assignments
    }

    private fun computeStats(teacher: Teacher, targetDate: String, arrangements: List<Arrangement>): DutyStats {
        var total = 0
        var hasDutyOnDate = false
        var coordinatorDays = 0
        var consecutiveRun = 0
        var previousDutyDate: LocalDate? = null

        for (arr in arrangements.sortedBy { it.date }) {
            val duties = arr.invigilatorAssignments.count { it.teacherId == teacher.id }
            if (duties == 0) continue
            total += duties
            if (arr.date == targetDate) hasDutyOnDate = true
            if (teacher.role == UserRole.EXAM_CELL_COORDINATOR) coordinatorDays++
            val parsed = runCatching { LocalDate.parse(arr.date) }.getOrNull()
            val prev = previousDutyDate
            consecutiveRun = if (parsed != null && prev != null && prev.plusDays(1) == parsed) consecutiveRun + 1 else 1
            if (parsed != null) previousDutyDate = parsed
        }
        return DutyStats(total, hasDutyOnDate, consecutiveRun, coordinatorDays)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun validateInputs(students: List<Student>, halls: List<Hall>, teachers: List<Teacher>): List<String>? {
        val errors = mutableListOf<String>()
        if (students.none { it.active }) errors.add("There are no active students. Add or activate students first.")
        if (halls.none { it.active }) errors.add("There are no active examination halls. Add or activate halls first.")
        if (teachers.none { it.active && it.role != UserRole.HOD }) {
            errors.add("No teachers are available for invigilation duty. The HOD is excluded from duties.")
        }
        return if (errors.isEmpty()) null else errors
    }

    private fun defaultSemester(year: StudentYear): Int = when (year) {
        StudentYear.YEAR_1 -> 2
        StudentYear.YEAR_2 -> 4
        StudentYear.YEAR_3 -> 6
        StudentYear.YEAR_4 -> 8
        else -> 4
    }
}
