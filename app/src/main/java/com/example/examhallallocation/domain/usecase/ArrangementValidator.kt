package com.example.examhallallocation.domain.usecase

import com.example.examhallallocation.domain.model.*

/**
 * Explicit validation of a generated (or manually edited) arrangement.
 * Used both after generation and whenever the admin edits an assignment,
 * so manual edits can never bypass the college rules.
 */
object ArrangementValidator {

    data class Report(val errors: List<String>) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validate(
        arrangement: Arrangement,
        students: List<Student>,
        halls: List<Hall>,
        teachers: List<Teacher>,
        allArrangements: List<Arrangement>,
    ): Report {
        val errors = mutableListOf<String>()
        val studentById = students.associateBy { it.id }
        val hallById = halls.associateBy { it.id }
        val teacherById = teachers.associateBy { it.id }

        // --- Student level ---------------------------------------------------
        val seenStudents = mutableSetOf<String>()
        for (ha in arrangement.hallAssignments) {
            val hall = hallById[ha.hallId]
            if (hall == null) {
                errors.add("Hall for an assignment no longer exists.")
                continue
            }
            if (!hall.active) errors.add("Inactive hall ${hall.roomNumber} must not be used.")

            for (studentId in ha.studentIds) {
                val student = studentById[studentId]
                when {
                    student == null -> errors.add("Unknown student assigned in ${hall.roomNumber}.")
                    !student.active -> errors.add("Inactive student ${student.registerNumber} assigned in ${hall.roomNumber}.")
                    !seenStudents.add(studentId) ->
                        errors.add("Student ${student.registerNumber} is assigned to more than one hall on ${arrangement.date}.")
                }
            }

            val hallTotal = arrangement.hallAssignments
                .filter { it.hallId == ha.hallId }
                .sumOf { it.studentIds.size }
            // Phase 3 rooms seat two students per bench (see ArrangementGenerator.planPhase3).
            val capacityLimit = if (arrangement.phase == ExamPhase.PHASE_3) hall.capacity * 2 else hall.capacity
            if (hallTotal > capacityLimit) {
                errors.add("Hall ${hall.roomNumber} exceeds capacity: $hallTotal of $capacityLimit.")
            }

            // Batch boundaries must remain the fixed 15-position windows.
            if (ha.startPosition % 15 != 1 || ha.endPosition != ha.startPosition + 14) {
                errors.add(
                    "Batch boundaries in ${hall.roomNumber} (${ha.startPosition}-${ha.endPosition}) " +
                        "do not match the fixed 15-position batches."
                )
            }
        }

        // --- Year applicability per phase ------------------------------------
        val allowedYears = when (arrangement.phase) {
            ExamPhase.PHASE_1 -> setOf(StudentYear.YEAR_2, StudentYear.YEAR_3, StudentYear.YEAR_4)
            ExamPhase.PHASE_2 -> setOf(StudentYear.YEAR_2, StudentYear.YEAR_3)
            ExamPhase.PHASE_3 -> setOf(StudentYear.YEAR_3)
        }
        arrangement.hallAssignments
            .filter { it.year !in allowedYears }
            .forEach { errors.add("Year ${it.year.label} is not allowed in ${arrangement.phase.label}.") }

        // In phases 1 and 2 a hall receives at most one 15-position batch per year
        // (the 15+15 pattern). Phase 3 instead stacks several 3rd-year batches per hall.
        if (arrangement.phase != ExamPhase.PHASE_3) {
            arrangement.hallAssignments
                .groupBy { it.hallId }
                .forEach { (hallId, blocks) ->
                    val years = blocks.map { it.year }
                    if (years.size != years.toSet().size) {
                        errors.add("Hall ${hallById[hallId]?.roomNumber ?: hallId} contains a duplicated year block.")
                    }
                }
        }

        // --- Phase 3: exactly two halls ---------------------------------------
        if (arrangement.phase == ExamPhase.PHASE_3) {
            val occupied = arrangement.hallAssignments.map { it.hallId }.distinct().size
            if (occupied != ArrangementGenerator.PHASE_3_HALLS) {
                errors.add("Phase 3 must use exactly 2 halls, found $occupied.")
            }
        }

        // --- Invigilators ------------------------------------------------------
        val hodIds = teachers.filter { it.role == UserRole.HOD }.map { it.id }.toSet()
        arrangement.invigilatorAssignments
            .groupBy { it.hallId }
            .forEach { (hallId, list) ->
                if (list.size > 1) errors.add("Hall ${hallById[hallId]?.roomNumber ?: hallId} has more than one invigilator.")
            }

        arrangement.invigilatorAssignments
            .groupBy { it.teacherId }
            .forEach { (teacherId, list) ->
                if (list.size > 1) {
                    errors.add("Teacher ${teacherById[teacherId]?.name ?: teacherId} is assigned to multiple halls on ${arrangement.date}.")
                }
            }

        arrangement.invigilatorAssignments
            .filter { it.teacherId in hodIds }
            .forEach { errors.add("HOD must never receive invigilation duty.") }

        val coordinatorIds = teachers.filter { it.role == UserRole.EXAM_CELL_COORDINATOR }.map { it.id }.toSet()
        val coordinatorDays = allArrangements
            .filter { arr -> arr.invigilatorAssignments.any { it.teacherId in coordinatorIds } }
            .map { it.date }
            .toSet()
        if (coordinatorDays.size > 1) {
            errors.add("Exam Cell Coordinator received duty on ${coordinatorDays.size} days; only one day is allowed.")
        }

        // Every occupied hall must have exactly one invigilator.
        val occupiedHalls = arrangement.hallAssignments.map { it.hallId }.distinct()
        for (hallId in occupiedHalls) {
            val assigned = arrangement.invigilatorAssignments.count { it.hallId == hallId }
            if (assigned != 1) {
                errors.add("Hall ${hallById[hallId]?.roomNumber ?: hallId} needs exactly one invigilator, found $assigned.")
            }
        }

        // Teacher and hall referenced in invigilation must exist and be active.
        arrangement.invigilatorAssignments.forEach { inv ->
            val teacher = teacherById[inv.teacherId]
            val hall = hallById[inv.hallId]
            if (teacher == null || !teacher.active) errors.add("Invigilator assignment references an inactive or unknown teacher.")
            if (hall == null || !hall.active) errors.add("Invigilator assignment references an inactive or unknown hall.")
        }

        return Report(errors.distinct())
    }
}
