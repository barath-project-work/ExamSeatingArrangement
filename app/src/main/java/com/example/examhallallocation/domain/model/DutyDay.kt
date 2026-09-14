package com.example.examhallallocation.domain.model

data class DutyDay(
    val date: String,
    val roomNumber: String,
    val block: String,
    val floor: Int,
    val yearSemester: String,
    val subjects: String,
)
