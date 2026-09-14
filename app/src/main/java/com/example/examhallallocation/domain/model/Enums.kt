package com.example.examhallallocation.domain.model

/** Academic year used for seating pools. Sections are never part of allocation. */
enum class StudentYear(val value: Int, val label: String) {
    YEAR_1(1, "1st Year"),
    YEAR_2(2, "2nd Year"),
    YEAR_3(3, "3rd Year"),
    YEAR_4(4, "4th Year");

    companion object {
        fun fromValue(value: Int): StudentYear? = entries.firstOrNull { it.value == value }
    }
}

/** Application roles. Determined by authenticated account data, never by UI selection. */
enum class UserRole(val label: String) {
    NORMAL_TEACHER("Teacher"),
    HOD("HOD"),
    EXAM_CELL_COORDINATOR("Exam Cell Coordinator"),
    ADMIN("Admin");
}

/** Exam phases as defined by the college rules. */
enum class ExamPhase(val index: Int, val label: String) {
    PHASE_1(1, "Phase 1"),
    PHASE_2(2, "Phase 2"),
    PHASE_3(3, "Phase 3");
}

enum class ArrangementStatus(val label: String) {
    DRAFT("Draft"),
    APPROVED("Approved");
}
