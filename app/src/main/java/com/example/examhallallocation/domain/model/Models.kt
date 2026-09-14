package com.example.examhallallocation.domain.model

data class Student(
    val id: String,
    val registerNumber: String,
    val name: String,
    val year: StudentYear,
    val section: String,
    /** 1-based position in the year's ordered student list. Gaps are meaningful and preserved. */
    val position: Int,
    val active: Boolean = true,
) {
    /**
     * Extracts the student's numeric roll number (typically 1 to 120).
     * Parses trailing digits of the register number (e.g. 110325104001 -> 1, 110325104120 -> 120),
     * falling back to position.
     */
    fun extractRollNumber(): Int {
        val digits = registerNumber.filter { it.isDigit() }
        if (digits.isNotEmpty()) {
            if (digits.length >= 6) {
                val suffix3 = digits.takeLast(3).toIntOrNull()
                if (suffix3 != null && suffix3 > 0) return suffix3
            } else {
                val num = digits.toIntOrNull()
                if (num != null && num > 0) return num
            }
        }
        return if (position > 0) position else 999999
    }
}

/**
 * Strict academic ordering:
 * 1. Year ascending: 1st Year -> 2nd Year -> 3rd Year -> 4th Year
 * 2. Roll number ascending: strictly 1 to 120 in sequential order
 * 3. Register number string fallback
 * 4. Name fallback
 */
val StudentOrderComparator: Comparator<Student> = compareBy<Student>(
    { it.year.value },
    { it.extractRollNumber() },
    { it.registerNumber },
    { it.name }
)


data class Teacher(
    val id: String,
    val name: String,
    val username: String,
    val role: UserRole,
    val active: Boolean = true,
)

data class Subject(
    val id: String,
    val code: String,
    val name: String,
    val year: StudentYear,
    val semester: Int,
    val department: String = "CSE",
    val active: Boolean = true,
)

data class Exam(
    val id: String,
    val examName: String,
    val date: String, // ISO yyyy-MM-dd
    val year: StudentYear,
    val semester: Int,
    val subjectCode: String,
    val subjectName: String,
    val session: String = "FN",
    val timing: String = "8:40 a.m. TO 10:10 a.m.",
    val department: String = "CSE",
    val studentCount: Int = 0,
)

data class Hall(
    val id: String,
    val roomNumber: String,
    val block: String,
    val floor: Int,
    /** Maximum students during exams (normally 30). One bench = one student. */
    val capacity: Int,
    val active: Boolean = true,
)

/**
 * One (hall, year) seating block inside a day arrangement. A hall normally contains
 * exactly two such blocks from two different years; occupancy may be lower than 15+15.
 */
data class HallAssignment(
    val id: String,
    val hallId: String,
    val year: StudentYear,
    val semester: Int,
    /** Inclusive position range in the year's ordered list; gaps preserved, not shifted. */
    val startPosition: Int,
    val endPosition: Int,
    val studentIds: List<String>,
    val studentCount: Int = studentIds.size,
)

data class InvigilatorAssignment(
    val id: String,
    val hallId: String,
    val teacherId: String,
)

/** One generated day: a date, its phase, hall blocks and invigilator duties. */
data class Arrangement(
    val id: String,
    val examName: String,
    val date: String, // ISO yyyy-MM-dd
    val phase: ExamPhase,
    val hallAssignments: List<HallAssignment>,
    val invigilatorAssignments: List<InvigilatorAssignment>,
    val status: ArrangementStatus,
)

data class UserSession(
    val teacherId: String,
    val name: String,
    val username: String,
    val role: UserRole,
)
