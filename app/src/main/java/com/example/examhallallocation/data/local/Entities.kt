package com.example.examhallallocation.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [Index(value = ["registerNumber"], unique = true)],
)
data class StudentEntity(
    @PrimaryKey val id: String,
    val registerNumber: String,
    val name: String,
    val year: Int,
    val section: String,
    val position: Int,
    val active: Boolean,
)

@Entity(
    tableName = "teachers",
    indices = [Index(value = ["username"], unique = true)],
)
data class TeacherEntity(
    @PrimaryKey val id: String,
    val name: String,
    val username: String,
    val role: String,
    val active: Boolean,
)

@Entity(tableName = "subjects", indices = [Index(value = ["code"], unique = true)])
data class SubjectEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val year: Int,
    val semester: Int,
    val department: String = "CSE",
    val active: Boolean = true,
)

@Entity(tableName = "exams", indices = [Index(value = ["date"])])
data class ExamEntity(
    @PrimaryKey val id: String,
    val examName: String,
    val date: String,
    val year: Int,
    val semester: Int,
    val subjectCode: String,
    val subjectName: String,
    val session: String = "FN",
    val timing: String = "8:40 a.m. TO 10:10 a.m.",
    val department: String = "CSE",
    val studentCount: Int = 0,
)

@Entity(tableName = "halls")
data class HallEntity(
    @PrimaryKey val id: String,
    val roomNumber: String,
    val block: String,
    val floor: Int,
    val capacity: Int,
    val active: Boolean,
)

@Entity(tableName = "arrangements", indices = [Index(value = ["date"], unique = true)])
data class ArrangementEntity(
    @PrimaryKey val id: String,
    val examName: String,
    val date: String,
    val phase: Int,
    val status: String,
)

@Entity(
    tableName = "hall_assignments",
    indices = [Index(value = ["arrangementId"])],
)
data class HallAssignmentEntity(
    @PrimaryKey val id: String,
    val arrangementId: String,
    val hallId: String,
    val year: Int,
    val semester: Int,
    val startPosition: Int,
    val endPosition: Int,
    /** Comma-joined student ids; avoids a join table for V1. */
    val studentIdsJoined: String,
    val studentCount: Int,
)

@Entity(
    tableName = "invigilator_assignments",
    indices = [Index(value = ["arrangementId"]), Index(value = ["teacherId"])],
)
data class InvigilatorAssignmentEntity(
    @PrimaryKey val id: String,
    val arrangementId: String,
    val hallId: String,
    val teacherId: String,
)

/**
 * Persistent sync queue item in SQLite. Ensures offline mutations are queued
 * and automatically dispatched with zero data loss once connectivity is restored.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String, // "UPSERT" or "DELETE"
    val collection: String,
    val documentId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
)

