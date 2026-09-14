package com.example.examhallallocation.data.remote

import com.example.examhallallocation.domain.model.Arrangement
import com.example.examhallallocation.domain.model.ArrangementStatus
import com.example.examhallallocation.domain.model.Exam
import com.example.examhallallocation.domain.model.ExamPhase
import com.example.examhallallocation.domain.model.Hall
import com.example.examhallallocation.domain.model.HallAssignment
import com.example.examhallallocation.domain.model.InvigilatorAssignment
import com.example.examhallallocation.domain.model.Student
import com.example.examhallallocation.domain.model.StudentYear
import com.example.examhallallocation.domain.model.Teacher
import com.example.examhallallocation.domain.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Mapping between domain models and Firestore documents. Field names are the single
 * source of truth for the cloud schema; documents are keyed by the same stable ids
 * the app already uses (stu_*, tea_*, hall_*, exam_*, arr_*), so Room and Firestore
 * stay 1:1 and sync is idempotent.
 */
object FirestoreData {

    const val STUDENTS = "students"
    const val TEACHERS = "teachers"
    const val EXAMS = "exams"
    const val HALLS = "halls"
    const val ARRANGEMENTS = "arrangements"
    const val SUB_HALL_ASSIGNMENTS = "hallAssignments"
    const val SUB_INVIGILATORS = "invigilators"

    // ------------------------------------------------------------------
    // Students
    // ------------------------------------------------------------------

    fun Student.toMap(): Map<String, Any> = mapOf(
        "registerNumber" to registerNumber,
        "name" to name,
        "year" to year.value,
        "section" to section,
        "position" to position,
        "active" to active,
    )

    fun student(doc: DocumentSnapshot): Student? {
        val registerNumber = doc.getString("registerNumber") ?: return null
        return Student(
            id = doc.id,
            registerNumber = registerNumber,
            name = doc.getString("name").orEmpty(),
            year = StudentYear.fromValue(doc.getLong("year")?.toInt() ?: 0) ?: return null,
            section = doc.getString("section").orEmpty(),
            position = doc.getLong("position")?.toInt() ?: 0,
            active = doc.getBoolean("active") ?: true,
        )
    }

    // ------------------------------------------------------------------
    // Teachers
    // ------------------------------------------------------------------

    fun Teacher.toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "username" to username,
        "role" to role.name,
        "active" to active,
    )

    fun teacher(doc: DocumentSnapshot): Teacher? {
        val username = doc.getString("username") ?: return null
        return Teacher(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            username = username,
            role = runCatching { UserRole.valueOf(doc.getString("role") ?: "") }
                .getOrDefault(UserRole.NORMAL_TEACHER),
            active = doc.getBoolean("active") ?: true,
        )
    }

    // ------------------------------------------------------------------
    // Exams
    // ------------------------------------------------------------------

    fun Exam.toMap(): Map<String, Any> = mapOf(
        "examName" to examName,
        "date" to date,
        "year" to year.value,
        "semester" to semester,
        "subjectCode" to subjectCode,
        "subjectName" to subjectName,
        "session" to session,
        "timing" to timing,
        "department" to department,
        "studentCount" to studentCount,
    )

    fun exam(doc: DocumentSnapshot): Exam? {
        val date = doc.getString("date") ?: return null
        return Exam(
            id = doc.id,
            examName = doc.getString("examName").orEmpty(),
            date = date,
            year = StudentYear.fromValue(doc.getLong("year")?.toInt() ?: 0) ?: return null,
            semester = doc.getLong("semester")?.toInt() ?: 0,
            subjectCode = doc.getString("subjectCode").orEmpty(),
            subjectName = doc.getString("subjectName").orEmpty(),
            session = doc.getString("session") ?: "FN",
            timing = doc.getString("timing") ?: "8:40 a.m. TO 10:10 a.m.",
            department = doc.getString("department") ?: "CSE",
            studentCount = doc.getLong("studentCount")?.toInt() ?: 0,
        )
    }

    // ------------------------------------------------------------------
    // Halls
    // ------------------------------------------------------------------

    fun Hall.toMap(): Map<String, Any> = mapOf(
        "roomNumber" to roomNumber,
        "block" to block,
        "floor" to floor,
        "capacity" to capacity,
        "active" to active,
    )

    fun hall(doc: DocumentSnapshot): Hall? {
        val roomNumber = doc.getString("roomNumber") ?: return null
        return Hall(
            id = doc.id,
            roomNumber = roomNumber,
            block = doc.getString("block").orEmpty(),
            floor = doc.getLong("floor")?.toInt() ?: 0,
            capacity = doc.getLong("capacity")?.toInt() ?: 0,
            active = doc.getBoolean("active") ?: true,
        )
    }

    // ------------------------------------------------------------------
    // Arrangements (+ subcollections)
    // ------------------------------------------------------------------

    fun Arrangement.toMap(): Map<String, Any> = mapOf(
        "examName" to examName,
        "date" to date,
        "phase" to phase.ordinal,
        "status" to status.name,
    )

    fun HallAssignment.toMap(): Map<String, Any> = mapOf(
        "hallId" to hallId,
        "year" to year.value,
        "semester" to semester,
        "startPosition" to startPosition,
        "endPosition" to endPosition,
        "studentIds" to studentIds,
        "studentCount" to studentIds.size,
    )

    fun hallAssignment(doc: DocumentSnapshot): HallAssignment? {
        val hallId = doc.getString("hallId") ?: return null
        @Suppress("UNCHECKED_CAST")
        val ids = (doc.get("studentIds") as? List<Any>)?.map { it.toString() } ?: return null
        return HallAssignment(
            id = doc.id,
            hallId = hallId,
            year = StudentYear.fromValue(doc.getLong("year")?.toInt() ?: 0) ?: return null,
            semester = doc.getLong("semester")?.toInt() ?: 0,
            startPosition = doc.getLong("startPosition")?.toInt() ?: 0,
            endPosition = doc.getLong("endPosition")?.toInt() ?: 0,
            studentIds = ids,
        )
    }

    fun InvigilatorAssignment.toMap(): Map<String, Any> = mapOf(
        "hallId" to hallId,
        "teacherId" to teacherId,
    )

    fun invigilatorAssignment(doc: DocumentSnapshot): InvigilatorAssignment? {
        return InvigilatorAssignment(
            id = doc.id,
            hallId = doc.getString("hallId") ?: return null,
            teacherId = doc.getString("teacherId") ?: return null,
        )
    }

    fun phaseFromOrdinal(ordinal: Int): ExamPhase =
        ExamPhase.entries.getOrElse(ordinal) { ExamPhase.PHASE_1 }

    fun statusFromName(name: String?): ArrangementStatus =
        runCatching { ArrangementStatus.valueOf(name ?: "") }.getOrDefault(ArrangementStatus.DRAFT)
}
