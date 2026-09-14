package com.example.examhallallocation.domain.model

/**
 * Represents a student assigned to an exam hall for attendance tracking.
 */
data class HallStudentAttendance(
    val id: String,
    val registerNumber: String,
    val name: String,
    val yearLabel: String,
    val section: String,
    val isPresent: Boolean = true,
)
