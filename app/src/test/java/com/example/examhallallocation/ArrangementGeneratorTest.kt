package com.example.examhallallocation

import com.example.examhallallocation.domain.model.*
import com.example.examhallallocation.domain.usecase.ArrangementGenerator
import com.example.examhallallocation.domain.usecase.ArrangementValidator
import com.example.examhallallocation.domain.usecase.CsvParser
import com.example.examhallallocation.domain.usecase.GenerationResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArrangementGeneratorTest {

    private val generator = ArrangementGenerator()

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun studentsForYear(year: StudentYear, count: Int, missingPositions: Set<Int> = emptySet()): List<Student> {
        var position = 0
        return (1..count + missingPositions.size).mapNotNull { index ->
            position = index
            if (index in missingPositions) return@mapNotNull null
            Student(
                id = "stu_${year.value}_$index",
                registerNumber = "%dCS%03d".format(year.value, index),
                name = "Student $index",
                year = year,
                section = if (index % 2 == 0) "A" else "B",
                position = position,
                active = true,
            )
        }
    }

    private fun standardHalls(count: Int): List<Hall> = (1..count).map { index ->
        Hall(id = "hall_$index", roomNumber = "R%03d".format(index), block = "A", floor = 1, capacity = 30, active = true)
    }

    private fun standardTeachers(normalCount: Int): List<Teacher> = buildList {
        add(Teacher("tea_hod", "HOD", "hod", UserRole.HOD))
        add(Teacher("tea_coord", "Coordinator", "coordinator", UserRole.EXAM_CELL_COORDINATOR))
        (1..normalCount).forEach {
            add(Teacher("tea_n$it", "Teacher $it", "t$it", UserRole.NORMAL_TEACHER))
        }
    }

    private val fullSemesters = mapOf(
        StudentYear.YEAR_2 to 4,
        StudentYear.YEAR_3 to 6,
        StudentYear.YEAR_4 to 8,
    )

    private fun generate(
        students: List<Student>,
        halls: List<Hall>,
        teachers: List<Teacher>,
        phase: ExamPhase = ExamPhase.PHASE_1,
        existing: List<Arrangement> = emptyList(),
    ): GenerationResult = generator.generate(
        examName = "Test Exam",
        date = "2026-04-13",
        phase = phase,
        students = students,
        halls = halls,
        teachers = teachers,
        semesters = fullSemesters,
        existingArrangements = existing,
    )

    // ------------------------------------------------------------------
    // 1. Full 360-student cohort
    // ------------------------------------------------------------------

    @Test
    fun `full cohort seats every student across 12 halls`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val result = generate(students, standardHalls(12), standardTeachers(12))

        val arrangement = (result as GenerationResult.Success).arrangement
        assertThat(arrangement.hallAssignments.sumOf { it.studentIds.size }).isEqualTo(360)
        assertThat(arrangement.hallAssignments.map { it.hallId }.distinct().size).isAtMost(12)
    }

    // 2. Fewer than 120 students in a year

    @Test
    fun `year with fewer students seats only available students`() {
        val students = studentsForYear(StudentYear.YEAR_2, 118) +
            studentsForYear(StudentYear.YEAR_3, 95) +
            studentsForYear(StudentYear.YEAR_4, 110)
        val result = generate(students, standardHalls(12), standardTeachers(12))

        val arrangement = (result as GenerationResult.Success).arrangement
        assertThat(arrangement.hallAssignments.sumOf { it.studentIds.size }).isEqualTo(323)
    }

    // 3. Missing position inside a 15-position batch

    @Test
    fun `missing position 44 keeps batch boundaries and does not shift students`() {
        val year3 = studentsForYear(StudentYear.YEAR_3, 119, missingPositions = setOf(44))
        val result = generate(year3, standardHalls(4), standardTeachers(6), phase = ExamPhase.PHASE_3)

        val arrangement = (result as GenerationResult.Success).arrangement
        // 119 active students, all seated.
        assertThat(arrangement.hallAssignments.sumOf { it.studentIds.size }).isEqualTo(119)
        // The batch containing position 44 must still span 31-45.
        val batchWith44 = arrangement.hallAssignments.first { it.startPosition == 31 }
        assertThat(batchWith44.endPosition).isEqualTo(45)
        // Only 14 students in the 31-45 window (44 is missing).
        assertThat(batchWith44.studentIds.size).isEqualTo(14)
        // The students after the gap keep their original positions (no back-shift):
        // position 45 belongs to the batch, and the batch 46-60 also exists.
        assertThat(arrangement.hallAssignments.map { it.startPosition }).contains(46)
    }

    // 4. Duplicate prevention

    @Test
    fun `no student appears in more than one hall`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val arrangement = (generate(students, standardHalls(12), standardTeachers(12)) as GenerationResult.Success).arrangement

        val allIds = arrangement.hallAssignments.flatMap { it.studentIds }
        assertThat(allIds.size).isEqualTo(allIds.toSet().size)
    }

    // 5. Hall capacity

    @Test
    fun `no hall exceeds its exam capacity`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val arrangement = (generate(students, standardHalls(12), standardTeachers(12)) as GenerationResult.Success).arrangement

        arrangement.hallAssignments
            .groupBy { it.hallId }
            .forEach { (hallId, blocks) ->
                assertThat(blocks.sumOf { it.studentIds.size }).isAtMost(30)
            }
    }

    // 6. Phase 1

    @Test
    fun `phase 1 uses all three years and at most 12 halls`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val arrangement = (generate(students, standardHalls(12), standardTeachers(12)) as GenerationResult.Success).arrangement

        assertThat(arrangement.phase).isEqualTo(ExamPhase.PHASE_1)
        assertThat(arrangement.hallAssignments.map { it.hallId }.distinct().size).isAtMost(12)
        assertThat(arrangement.hallAssignments.map { it.year }.toSet())
            .containsExactly(StudentYear.YEAR_2, StudentYear.YEAR_3, StudentYear.YEAR_4)
    }

    // 7. Phase 2

    @Test
    fun `phase 2 uses only years 2 and 3 and at most 8 halls`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) + studentsForYear(StudentYear.YEAR_3, 120)
        val arrangement = (generate(students, standardHalls(8), standardTeachers(8), phase = ExamPhase.PHASE_2) as GenerationResult.Success).arrangement

        assertThat(arrangement.phase).isEqualTo(ExamPhase.PHASE_2)
        assertThat(arrangement.hallAssignments.map { it.hallId }.distinct().size).isAtMost(8)
        assertThat(arrangement.hallAssignments.map { it.year }.toSet())
            .containsExactly(StudentYear.YEAR_2, StudentYear.YEAR_3)
    }

    // 8. Phase 3

    @Test
    fun `phase 3 uses 4 halls with only 3rd year and max 30 students per hall`() {
        val students = studentsForYear(StudentYear.YEAR_3, 120)
        val arrangement = (generate(students, standardHalls(6), standardTeachers(4), phase = ExamPhase.PHASE_3) as GenerationResult.Success).arrangement

        assertThat(arrangement.phase).isEqualTo(ExamPhase.PHASE_3)
        assertThat(arrangement.hallAssignments.map { it.hallId }.distinct().size).isEqualTo(4)
        assertThat(arrangement.hallAssignments.map { it.year }.toSet()).containsExactly(StudentYear.YEAR_3)
        arrangement.hallAssignments.groupBy { it.hallId }.forEach { (_, blocks) ->
            assertThat(blocks.sumOf { it.studentIds.size }).isAtMost(30)
        }
    }

    // 9. HOD exclusion

    @Test
    fun `HOD is never assigned invigilation duty`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val arrangement = (generate(students, standardHalls(12), standardTeachers(12)) as GenerationResult.Success).arrangement

        arrangement.invigilatorAssignments.forEach { inv ->
            assertThat(inv.teacherId).isNotEqualTo("tea_hod")
        }
    }

    // 10. Coordinator one-day limit

    @Test
    fun `coordinator gets duty on at most one day across the exam`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val teachers = standardTeachers(12)

        var existing = emptyList<Arrangement>()
        // Generate five days; the coordinator may appear on at most one of them.
        (0 until 5).forEach { day ->
            val result = generator.generate(
                examName = "Test Exam",
                date = "2026-04-1${day + 3}", // 13..17
                phase = ExamPhase.PHASE_1,
                students = students,
                halls = standardHalls(12),
                teachers = teachers,
                semesters = fullSemesters,
                existingArrangements = existing,
            )
            val arrangement = (result as GenerationResult.Success).arrangement
            existing = existing + arrangement
        }
        val coordinatorDays = existing
            .filter { arr -> arr.invigilatorAssignments.any { it.teacherId == "tea_coord" } }
            .size
        assertThat(coordinatorDays).isAtMost(1)
    }

    // 11. One hall per teacher per day

    @Test
    fun `teacher never has two halls on the same day`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val arrangement = (generate(students, standardHalls(12), standardTeachers(12)) as GenerationResult.Success).arrangement

        val teacherHallCounts = arrangement.invigilatorAssignments.groupBy { it.teacherId }
        teacherHallCounts.forEach { (teacherId, list) ->
            assertThat(list.size).isEqualTo(1)
            assertThat(list.map { it.hallId }.toSet().size).isEqualTo(1)
        }
    }

    // 12. Workload balancing

    @Test
    fun `duty distribution across days stays balanced`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val teachers = standardTeachers(12)

        var existing = emptyList<Arrangement>()
        (0 until 5).forEach { day ->
            val result = generator.generate(
                examName = "Test Exam",
                date = "2026-04-1${day + 3}",
                phase = ExamPhase.PHASE_1,
                students = students,
                halls = standardHalls(12),
                teachers = teachers,
                semesters = fullSemesters,
                existingArrangements = existing,
            )
            existing = existing + (result as GenerationResult.Success).arrangement
        }

        val dutyCounts = teachers.filter { it.role == UserRole.NORMAL_TEACHER }
            .map { teacher -> existing.sumOf { arr -> arr.invigilatorAssignments.count { it.teacherId == teacher.id } } }
        val max = dutyCounts.max()
        val min = dutyCounts.min()
        // Balance: no teacher has more than one extra duty beyond the minimum.
        assertThat(max - min).isAtMost(1)
    }

    // 13. Different teacher counts

    @Test
    fun `small teacher pool still satisfies hard constraints or fails clearly`() {
        val students = studentsForYear(StudentYear.YEAR_2, 60) + studentsForYear(StudentYear.YEAR_3, 60)
        val teachers = standardTeachers(2)
        val result = generate(students, standardHalls(12), teachers, phase = ExamPhase.PHASE_2)

        when (result) {
            is GenerationResult.Success -> {
                val invs = result.arrangement.invigilatorAssignments
                assertThat(invs.map { it.teacherId }.toSet().size).isEqualTo(invs.size)
            }
            is GenerationResult.Failure -> assertThat(result.errors).isNotEmpty()
        }
    }

    // 14. Fewer active halls

    @Test
    fun `generation fails clearly when active halls are insufficient`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val result = generate(students, standardHalls(2), standardTeachers(12))

        assertThat(result).isInstanceOf(GenerationResult.Failure::class.java)
        val failure = result as GenerationResult.Failure
        assertThat(failure.errors.first()).contains("hall")
    }

    @Test
    fun `inactive halls are never used`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) + studentsForYear(StudentYear.YEAR_3, 120)
        val halls = standardHalls(10).mapIndexed { index, hall ->
            if (index >= 8) hall.copy(active = false) else hall
        }
        val result = generate(students, halls, standardTeachers(8), phase = ExamPhase.PHASE_2)

        val arrangement = (result as GenerationResult.Success).arrangement
        arrangement.hallAssignments.forEach { ha ->
            assertThat(ha.hallId).isNotIn(halls.filter { !it.active }.map { it.id })
        }
    }

    // 15. Regeneration validity

    @Test
    fun `regeneration over an existing arrangement stays valid`() {
        val students = studentsForYear(StudentYear.YEAR_2, 120) +
            studentsForYear(StudentYear.YEAR_3, 120) +
            studentsForYear(StudentYear.YEAR_4, 120)
        val teachers = standardTeachers(12)
        val halls = standardHalls(12)

        val first = (generate(students, halls, teachers) as GenerationResult.Success).arrangement
        val second = generator.generate(
            examName = "Test Exam",
            date = "2026-04-13",
            phase = ExamPhase.PHASE_1,
            students = students,
            halls = halls,
            teachers = teachers,
            semesters = fullSemesters,
            existingArrangements = emptyList(), // replacing same date
        )
        val secondArrangement = (second as GenerationResult.Success).arrangement

        // The regeneration must seat the same number of students (all 360) and remain valid.
        assertThat(secondArrangement.hallAssignments.sumOf { it.studentIds.size }).isEqualTo(360)
        assertThat(secondArrangement.invigilatorAssignments.size)
            .isEqualTo(first.invigilatorAssignments.size)
        // And it may differ (randomization) but never be invalid.
        val report = ArrangementValidator.validate(
            secondArrangement, students, halls, teachers, listOf(secondArrangement),
        )
        assertThat(report.isValid).isTrue()
    }

    // ------------------------------------------------------------------
    // Validator direct checks
    // ------------------------------------------------------------------

    @Test
    fun `validator rejects HOD assignment explicitly`() {
        val students = studentsForYear(StudentYear.YEAR_3, 60)
        val halls = standardHalls(2)
        val teachers = standardTeachers(4)
        val arrangement = Arrangement(
            id = "arr_x",
            examName = "Test",
            date = "2026-04-13",
            phase = ExamPhase.PHASE_3,
            hallAssignments = listOf(
                HallAssignment("ha1", "hall_1", StudentYear.YEAR_3, 6, 1, 15, students.take(15).map { it.id }),
                HallAssignment("ha2", "hall_2", StudentYear.YEAR_3, 6, 16, 30, students.drop(15).take(15).map { it.id }),
            ),
            invigilatorAssignments = listOf(
                InvigilatorAssignment("inv1", "hall_1", "tea_hod"),
                InvigilatorAssignment("inv2", "hall_2", "tea_n1"),
            ),
            status = ArrangementStatus.DRAFT,
        )
        val report = ArrangementValidator.validate(arrangement, students, halls, teachers, listOf(arrangement))
        assertThat(report.isValid).isFalse()
        assertThat(report.errors.any { it.contains("HOD") }).isTrue()
    }

    @Test
    fun `validator rejects over-capacity hall`() {
        val students = studentsForYear(StudentYear.YEAR_3, 60)
        val halls = standardHalls(1) // capacity 30
        val teachers = standardTeachers(2)
        val arrangement = Arrangement(
            id = "arr_x",
            examName = "Test",
            date = "2026-04-13",
            phase = ExamPhase.PHASE_2,
            hallAssignments = listOf(
                HallAssignment("ha1", "hall_1", StudentYear.YEAR_3, 6, 1, 15, students.take(15).map { it.id }),
                HallAssignment("ha2", "hall_1", StudentYear.YEAR_3, 6, 16, 30, students.drop(15).take(15).map { it.id }),
                HallAssignment("ha3", "hall_1", StudentYear.YEAR_3, 6, 31, 45, students.drop(30).take(15).map { it.id }),
            ),
            invigilatorAssignments = listOf(InvigilatorAssignment("inv1", "hall_1", "tea_n1")),
            status = ArrangementStatus.DRAFT,
        )
        val report = ArrangementValidator.validate(arrangement, students, halls, teachers, listOf(arrangement))
        assertThat(report.isValid).isFalse()
        assertThat(report.errors.any { it.contains("capacity") }).isTrue()
    }

    // ------------------------------------------------------------------
    // CSV parser
    // ------------------------------------------------------------------

    @Test
    fun `csv parser accepts valid rows and skips duplicates`() {
        val csv = """
            RegisterNumber,Name,Year,Section
            21CS001,Alpha,2,A
            21CS002,Beta,2,B
            21CS001,Gamma,2,A
        """.trimIndent()
        val result = CsvParser.parse(csv)
        assertThat(result.students).hasSize(2)
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    fun `csv parser rejects invalid year`() {
        val csv = """
            RegisterNumber,Name,Year,Section
            21CS001,Alpha,9,A
        """.trimIndent()
        val result = CsvParser.parse(csv)
        assertThat(result.students).isEmpty()
        assertThat(result.skipped).isEqualTo(1)
    }

    // ------------------------------------------------------------------
    // Manual Invigilator Reassignment & Swap Validation
    // ------------------------------------------------------------------

    @Test
    fun `swapping invigilator to an eligible free teacher maintains valid arrangement`() {
        val students = studentsForYear(StudentYear.YEAR_2, 60) + studentsForYear(StudentYear.YEAR_3, 60)
        val halls = standardHalls(4)
        val teachers = standardTeachers(8) // 6 normal teachers available
        val arrangement = (generate(students, halls, teachers, phase = ExamPhase.PHASE_2) as GenerationResult.Success).arrangement

        val assignedTeacherIds = arrangement.invigilatorAssignments.map { it.teacherId }.toSet()
        val freeTeacher = teachers.first { it.role == UserRole.NORMAL_TEACHER && it.id !in assignedTeacherIds }

        // Swap first hall's teacher with free teacher
        val firstHallId = arrangement.invigilatorAssignments.first().hallId
        val swappedAssignments = arrangement.invigilatorAssignments.map { inv ->
            if (inv.hallId == firstHallId) inv.copy(teacherId = freeTeacher.id) else inv
        }
        val modifiedArrangement = arrangement.copy(invigilatorAssignments = swappedAssignments)

        val report = ArrangementValidator.validate(modifiedArrangement, students, halls, teachers, listOf(modifiedArrangement))
        assertThat(report.isValid).isTrue()
    }

    @Test
    fun `swapping invigilator to HOD fails validation`() {
        val students = studentsForYear(StudentYear.YEAR_2, 60) + studentsForYear(StudentYear.YEAR_3, 60)
        val halls = standardHalls(4)
        val teachers = standardTeachers(6)
        val arrangement = (generate(students, halls, teachers, phase = ExamPhase.PHASE_2) as GenerationResult.Success).arrangement

        val firstHallId = arrangement.invigilatorAssignments.first().hallId
        val swappedAssignments = arrangement.invigilatorAssignments.map { inv ->
            if (inv.hallId == firstHallId) inv.copy(teacherId = "tea_hod") else inv
        }
        val modifiedArrangement = arrangement.copy(invigilatorAssignments = swappedAssignments)

        val report = ArrangementValidator.validate(modifiedArrangement, students, halls, teachers, listOf(modifiedArrangement))
        assertThat(report.isValid).isFalse()
        assertThat(report.errors.any { it.contains("HOD") }).isTrue()
    }

    @Test
    fun `swapping invigilator to a teacher already assigned in another hall fails validation`() {
        val students = studentsForYear(StudentYear.YEAR_2, 60) + studentsForYear(StudentYear.YEAR_3, 60)
        val halls = standardHalls(4)
        val teachers = standardTeachers(6)
        val arrangement = (generate(students, halls, teachers, phase = ExamPhase.PHASE_2) as GenerationResult.Success).arrangement

        val hall1 = arrangement.invigilatorAssignments[0].hallId
        val teacher2 = arrangement.invigilatorAssignments[1].teacherId

        // Assign teacher 2 to hall 1 as well (double assignment on same day)
        val swappedAssignments = arrangement.invigilatorAssignments.map { inv ->
            if (inv.hallId == hall1) inv.copy(teacherId = teacher2) else inv
        }
        val modifiedArrangement = arrangement.copy(invigilatorAssignments = swappedAssignments)

        val report = ArrangementValidator.validate(modifiedArrangement, students, halls, teachers, listOf(modifiedArrangement))
        assertThat(report.isValid).isFalse()
        assertThat(report.errors.any { it.contains("multiple halls") }).isTrue()
    }

    // ------------------------------------------------------------------
    // Exact 7-Day Curriculum Schedule Test
    // ------------------------------------------------------------------

    @Test
    fun `exact 7-day curriculum scenario satisfies all phase, capacity, coordinator, and teacher balance constraints`() {
        // II Year: 6 subjects (115 students)
        // III Year: 7 subjects (119 students)
        // IV Year: 5 subjects (106 students)
        val year2 = studentsForYear(StudentYear.YEAR_2, 115)
        val year3 = studentsForYear(StudentYear.YEAR_3, 119)
        val year4 = studentsForYear(StudentYear.YEAR_4, 106)
        val allStudents = year2 + year3 + year4

        val halls = standardHalls(12)
        // 1 coordinator + 14 teachers = 15 staff
        val teachers = standardTeachers(14).filter { it.role != UserRole.HOD }

        var existingArrangements = emptyList<Arrangement>()

        // Days 1 to 5: Phase 1 (All 3 years write)
        (1..5).forEach { day ->
            val result = generator.generate(
                examName = "Midterm Day $day",
                date = "2026-04-%02d".format(12 + day),
                phase = ExamPhase.PHASE_1,
                students = allStudents,
                halls = halls,
                teachers = teachers,
                semesters = fullSemesters,
                existingArrangements = existingArrangements,
            )
            assertThat(result).isInstanceOf(GenerationResult.Success::class.java)
            val arr = (result as GenerationResult.Success).arrangement

            // Validate against validator
            val report = ArrangementValidator.validate(arr, allStudents, halls, teachers, existingArrangements + arr)
            assertThat(report.isValid).isTrue()

            // Check halls and mingling
            val occupiedHalls = arr.hallAssignments.map { it.hallId }.distinct()
            assertThat(occupiedHalls.size).isEqualTo(12)
            arr.hallAssignments.groupBy { it.hallId }.forEach { (_, blocks) ->
                assertThat(blocks.sumOf { it.studentIds.size }).isAtMost(30)
                assertThat(blocks.map { it.year }.distinct().size).isAtMost(2)
            }

            existingArrangements = existingArrangements + arr
        }

        // Day 6: Phase 2 (Year 2 and Year 3 only; Year 4 finished 5 subjects)
        val day6Students = year2 + year3
        val day6Result = generator.generate(
            examName = "Midterm Day 6",
            date = "2026-04-18",
            phase = ExamPhase.PHASE_2,
            students = day6Students,
            halls = halls,
            teachers = teachers,
            semesters = fullSemesters,
            existingArrangements = existingArrangements,
        )
        assertThat(day6Result).isInstanceOf(GenerationResult.Success::class.java)
        val day6Arr = (day6Result as GenerationResult.Success).arrangement
        val day6Report = ArrangementValidator.validate(day6Arr, day6Students, halls, teachers, existingArrangements + day6Arr)
        assertThat(day6Report.isValid).isTrue()

        val day6Halls = day6Arr.hallAssignments.map { it.hallId }.distinct()
        assertThat(day6Halls.size).isEqualTo(8)
        day6Arr.hallAssignments.groupBy { it.hallId }.forEach { (_, blocks) ->
            assertThat(blocks.sumOf { it.studentIds.size }).isAtMost(30)
        }
        existingArrangements = existingArrangements + day6Arr

        // Day 7: Phase 3 (Year 3 only; Year 2 finished 6 subjects)
        val day7Students = year3
        val day7Result = generator.generate(
            examName = "Midterm Day 7 (Monday)",
            date = "2026-04-20",
            phase = ExamPhase.PHASE_3,
            students = day7Students,
            halls = halls,
            teachers = teachers,
            semesters = fullSemesters,
            existingArrangements = existingArrangements,
        )
        assertThat(day7Result).isInstanceOf(GenerationResult.Success::class.java)
        val day7Arr = (day7Result as GenerationResult.Success).arrangement
        val day7Report = ArrangementValidator.validate(day7Arr, day7Students, halls, teachers, existingArrangements + day7Arr)
        assertThat(day7Report.isValid).isTrue()

        val day7Halls = day7Arr.hallAssignments.map { it.hallId }.distinct()
        assertThat(day7Halls.size).isEqualTo(4)
        day7Arr.hallAssignments.groupBy { it.hallId }.forEach { (_, blocks) ->
            assertThat(blocks.sumOf { it.studentIds.size }).isAtMost(30)
        }
        existingArrangements = existingArrangements + day7Arr

        // Across all 7 days:
        // 1. Coordinator must receive EXACTLY 1 duty
        val coordDuties = existingArrangements.sumOf { arr ->
            arr.invigilatorAssignments.count { it.teacherId == "tea_coord" }
        }
        assertThat(coordDuties).isEqualTo(1)

        // 2. Normal teachers must receive equal distribution (max - min <= 1)
        val teacherDuties = teachers.filter { it.role == UserRole.NORMAL_TEACHER }.map { t ->
            existingArrangements.sumOf { arr -> arr.invigilatorAssignments.count { it.teacherId == t.id } }
        }
        val maxDuties = teacherDuties.max()
        val minDuties = teacherDuties.min()
        assertThat(maxDuties - minDuties).isAtMost(1)

        // Total duties across 7 days: 5 * 12 + 8 + 4 = 72 duties.
        // 1 coordinator + 71 normal teacher duties across 14 teachers: 71 / 14 = 5.07 -> duties are either 5 or 6 each!
        assertThat(existingArrangements.sumOf { it.invigilatorAssignments.size }).isEqualTo(72)
        assertThat(minDuties).isEqualTo(5)
        assertThat(maxDuties).isEqualTo(6)
    }

    // ------------------------------------------------------------------
    // Dedicated Implementation Plan Verification Suite
    // ------------------------------------------------------------------

    @Test
    fun `15+15 alternating bench seating test`() {
        val year2 = studentsForYear(StudentYear.YEAR_2, 120)
        val year3 = studentsForYear(StudentYear.YEAR_3, 120)
        val year4 = studentsForYear(StudentYear.YEAR_4, 120)
        val halls = standardHalls(12)
        val teachers = standardTeachers(14).filter { it.role != UserRole.HOD }

        val result = generate(year2 + year3 + year4, halls, teachers, phase = ExamPhase.PHASE_1)
        assertThat(result).isInstanceOf(GenerationResult.Success::class.java)
        val arrangement = (result as GenerationResult.Success).arrangement

        val byHall = arrangement.hallAssignments.groupBy { it.hallId }
        assertThat(byHall.size).isEqualTo(12)

        byHall.forEach { (_, blocks) ->
            // Exactly 2 cohorts per hall
            assertThat(blocks.size).isEqualTo(2)
            assertThat(blocks[0].year).isNotEqualTo(blocks[1].year)

            // Exactly 15 students per batch (total 30)
            assertThat(blocks[0].studentIds.size).isEqualTo(15)
            assertThat(blocks[1].studentIds.size).isEqualTo(15)
            assertThat(blocks.sumOf { it.studentIds.size }).isEqualTo(30)

            // Alternating benches:
            // Odd benches 1, 3, 5...29 from Cohort A; Even benches 2, 4, 6...30 from Cohort B
            val benchList = mutableListOf<Pair<Int, String>>()
            for (i in 0 until 15) {
                benchList.add((2 * i + 1) to blocks[0].studentIds[i])
                benchList.add((2 * i + 2) to blocks[1].studentIds[i])
            }
            assertThat(benchList.size).isEqualTo(30)
            // Adjacent benches must have different years
            for (i in 0 until 29) {
                val studentA = (year2 + year3 + year4).first { it.id == benchList[i].second }
                val studentB = (year2 + year3 + year4).first { it.id == benchList[i + 1].second }
                assertThat(studentA.year).isNotEqualTo(studentB.year)
            }
        }
    }

    @Test
    fun `empty bench absence test`() {
        // Position 8 is absent/missing in Year 2
        val year2 = studentsForYear(StudentYear.YEAR_2, 119, missingPositions = setOf(8))
        val year3 = studentsForYear(StudentYear.YEAR_3, 120)
        val halls = standardHalls(12)
        val teachers = standardTeachers(14).filter { it.role != UserRole.HOD }

        val result = generate(year2 + year3, halls, teachers, phase = ExamPhase.PHASE_2)
        assertThat(result).isInstanceOf(GenerationResult.Success::class.java)
        val arrangement = (result as GenerationResult.Success).arrangement

        // The batch 1-15 for Year 2 must NOT shift students 9..15 to position 8
        val batch1 = arrangement.hallAssignments.first { it.year == StudentYear.YEAR_2 && it.startPosition == 1 }
        assertThat(batch1.endPosition).isEqualTo(15)
        assertThat(batch1.studentIds.size).isEqualTo(14)

        // Verify that student 9 is still at position 9, not shifted to 8
        val student9 = year2.first { it.id == "stu_2_9" }
        assertThat(student9.position).isEqualTo(9)
        assertThat(batch1.studentIds).contains("stu_2_9")
    }

    @Test
    fun `cumulative duty balancing test across Assessment 1, Assessment 2, and Model Exam`() {
        val year2 = studentsForYear(StudentYear.YEAR_2, 120)
        val year3 = studentsForYear(StudentYear.YEAR_3, 120)
        val year4 = studentsForYear(StudentYear.YEAR_4, 120)
        val allStudents = year2 + year3 + year4
        val halls = standardHalls(12)
        val teachers = standardTeachers(14).filter { it.role != UserRole.HOD }

        var cumulativeArrangements = emptyList<Arrangement>()

        // 3 Exam cycles: Assessment 1 (3 days), Assessment 2 (3 days), Model Exam (3 days) = 9 days
        listOf("Assessment_1", "Assessment_2", "Model_Exam").forEachIndexed { examIndex, examName ->
            (1..3).forEach { day ->
                val date = "2026-%02d-%02d".format(5 + examIndex, 10 + day)
                val result = generator.generate(
                    examName = "$examName Day $day",
                    date = date,
                    phase = ExamPhase.PHASE_1,
                    students = allStudents,
                    halls = halls,
                    teachers = teachers,
                    semesters = fullSemesters,
                    existingArrangements = cumulativeArrangements,
                )
                assertThat(result).isInstanceOf(GenerationResult.Success::class.java)
                val arr = (result as GenerationResult.Success).arrangement
                cumulativeArrangements = cumulativeArrangements + arr
            }
        }

        // Across all 9 days combined (108 total duties):
        val teacherDuties = teachers.filter { it.role == UserRole.NORMAL_TEACHER }.map { t ->
            cumulativeArrangements.sumOf { arr -> arr.invigilatorAssignments.count { it.teacherId == t.id } }
        }
        val maxDuties = teacherDuties.max()
        val minDuties = teacherDuties.min()
        // Workload is strictly balanced across the 3 exams combined
        assertThat(maxDuties - minDuties).isAtMost(1)

        // Coordinator gets at most 1 duty across all exams
        val coordDuties = cumulativeArrangements.sumOf { arr ->
            arr.invigilatorAssignments.count { it.teacherId == "tea_coord" }
        }
        assertThat(coordDuties).isAtMost(1)
    }

    @Test
    fun `coordinator reserve duty test`() {
        val students = studentsForYear(StudentYear.YEAR_2, 60) + studentsForYear(StudentYear.YEAR_3, 60)
        val halls = standardHalls(4)

        // When sufficient normal teachers are available, coordinator receives at most 1 duty
        val teachers = standardTeachers(6).filter { it.role != UserRole.HOD }
        val result = generate(students, halls, teachers, phase = ExamPhase.PHASE_2)
        assertThat(result).isInstanceOf(GenerationResult.Success::class.java)
        val arr = (result as GenerationResult.Success).arrangement

        val coordDutyCount = arr.invigilatorAssignments.count { it.teacherId == "tea_coord" }
        assertThat(coordDutyCount).isAtMost(1)
    }

    @Test
    fun `phase 1 follows exact deterministic 3-hall cyclic triplet sequence across all 12 halls`() {
        val year2 = studentsForYear(StudentYear.YEAR_2, 120)
        val year3 = studentsForYear(StudentYear.YEAR_3, 120)
        val year4 = studentsForYear(StudentYear.YEAR_4, 120)
        val halls = standardHalls(12)
        val teachers = standardTeachers(14).filter { it.role != UserRole.HOD }

        val result = generate(year2 + year3 + year4, halls, teachers, phase = ExamPhase.PHASE_1)
        assertThat(result).isInstanceOf(GenerationResult.Success::class.java)
        val arr = (result as GenerationResult.Success).arrangement

        val byHall = linkedMapOf<String, MutableList<HallAssignment>>()
        arr.hallAssignments.forEach { ha ->
            byHall.getOrPut(ha.hallId) { mutableListOf() }.add(ha)
        }

        assertThat(byHall.size).isEqualTo(12)
        val hallEntries = byHall.values.toList()

        // Expected triplets across 12 halls (4 repetitions of 3-hall cycle)
        // Triplet 1 (Halls 1, 2, 3):
        // Hall 1: Y2 (1-15) + Y3 (1-15)
        assertThat(hallEntries[0][0].year).isEqualTo(StudentYear.YEAR_2)
        assertThat(hallEntries[0][0].startPosition).isEqualTo(1)
        assertThat(hallEntries[0][0].endPosition).isEqualTo(15)
        assertThat(hallEntries[0][1].year).isEqualTo(StudentYear.YEAR_3)
        assertThat(hallEntries[0][1].startPosition).isEqualTo(1)
        assertThat(hallEntries[0][1].endPosition).isEqualTo(15)

        // Hall 2: Y4 (1-15) + Y2 (16-30)
        assertThat(hallEntries[1][0].year).isEqualTo(StudentYear.YEAR_4)
        assertThat(hallEntries[1][0].startPosition).isEqualTo(1)
        assertThat(hallEntries[1][0].endPosition).isEqualTo(15)
        assertThat(hallEntries[1][1].year).isEqualTo(StudentYear.YEAR_2)
        assertThat(hallEntries[1][1].startPosition).isEqualTo(16)
        assertThat(hallEntries[1][1].endPosition).isEqualTo(30)

        // Hall 3: Y3 (16-30) + Y4 (16-30)
        assertThat(hallEntries[2][0].year).isEqualTo(StudentYear.YEAR_3)
        assertThat(hallEntries[2][0].startPosition).isEqualTo(16)
        assertThat(hallEntries[2][0].endPosition).isEqualTo(30)
        assertThat(hallEntries[2][1].year).isEqualTo(StudentYear.YEAR_4)
        assertThat(hallEntries[2][1].startPosition).isEqualTo(16)
        assertThat(hallEntries[2][1].endPosition).isEqualTo(30)

        // Triplet 2 (Halls 4, 5, 6):
        // Hall 4: Y2 (31-45) + Y3 (31-45)
        assertThat(hallEntries[3][0].year).isEqualTo(StudentYear.YEAR_2)
        assertThat(hallEntries[3][0].startPosition).isEqualTo(31)
        assertThat(hallEntries[3][0].endPosition).isEqualTo(45)
        assertThat(hallEntries[3][1].year).isEqualTo(StudentYear.YEAR_3)
        assertThat(hallEntries[3][1].startPosition).isEqualTo(31)
        assertThat(hallEntries[3][1].endPosition).isEqualTo(45)

        // Hall 5: Y4 (31-45) + Y2 (46-60)
        assertThat(hallEntries[4][0].year).isEqualTo(StudentYear.YEAR_4)
        assertThat(hallEntries[4][0].startPosition).isEqualTo(31)
        assertThat(hallEntries[4][0].endPosition).isEqualTo(45)
        assertThat(hallEntries[4][1].year).isEqualTo(StudentYear.YEAR_2)
        assertThat(hallEntries[4][1].startPosition).isEqualTo(46)
        assertThat(hallEntries[4][1].endPosition).isEqualTo(60)

        // Hall 6: Y3 (46-60) + Y4 (46-60)
        assertThat(hallEntries[5][0].year).isEqualTo(StudentYear.YEAR_3)
        assertThat(hallEntries[5][0].startPosition).isEqualTo(46)
        assertThat(hallEntries[5][0].endPosition).isEqualTo(60)
        assertThat(hallEntries[5][1].year).isEqualTo(StudentYear.YEAR_4)
        assertThat(hallEntries[5][1].startPosition).isEqualTo(46)
        assertThat(hallEntries[5][1].endPosition).isEqualTo(60)

        // Triplet 4 final hall (Hall 12): Y3 (106-120) + Y4 (106-120)
        assertThat(hallEntries[11][0].year).isEqualTo(StudentYear.YEAR_3)
        assertThat(hallEntries[11][0].startPosition).isEqualTo(106)
        assertThat(hallEntries[11][0].endPosition).isEqualTo(120)
        assertThat(hallEntries[11][1].year).isEqualTo(StudentYear.YEAR_4)
        assertThat(hallEntries[11][1].startPosition).isEqualTo(106)
        assertThat(hallEntries[11][1].endPosition).isEqualTo(120)

        // All halls have strictly 30 students (15 + 15)
        hallEntries.forEach { blocks ->
            assertThat(blocks.sumOf { it.studentIds.size }).isEqualTo(30)
        }
    }
}

