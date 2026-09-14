package com.example.examhallallocation.domain.usecase

import com.example.examhallallocation.domain.model.Student
import com.example.examhallallocation.domain.model.StudentYear

/**
 * Parses CSV student imports: RegisterNumber,Name,Year,Section,Position(optional).
 * Returns parsed rows and per-line errors; duplicates within the file are skipped.
 */
object CsvParser {

    data class Result(
        val students: List<Student>,
        val skipped: Int,
        val errors: List<String>,
    )

    private val EXPECTED_HEADER = setOf("registernumber", "name", "year", "section")

    fun parse(content: String, existingRegisterNumbers: Set<String> = emptySet()): Result {
        val students = mutableListOf<Student>()
        val errors = mutableListOf<String>()
        val seenRegisterNumbers = mutableSetOf<String>()
        var skipped = 0

        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val parts = line.split(",").map { it.trim() }

            if (index == 0 && parts.map { it.lowercase() }.toSet().intersect(EXPECTED_HEADER).size >= 3) {
                return@forEachIndexed // header row
            }

            if (parts.size < 4) {
                errors.add("Line $lineNumber: expected at least 4 columns")
                skipped++
                return@forEachIndexed
            }

            val registerNumber = parts[0]
            val name = parts[1]
            val year = parts[2].toIntOrNull()?.let { StudentYear.fromValue(it) }
            val section = parts.getOrElse(3) { "" }
            val position = parts.getOrNull(4)?.toIntOrNull()

            when {
                registerNumber.isEmpty() -> { errors.add("Line $lineNumber: register number is empty"); skipped++ }
                name.isEmpty() -> { errors.add("Line $lineNumber: name is empty"); skipped++ }
                year == null -> { errors.add("Line $lineNumber: year must be 2, 3 or 4"); skipped++ }
                registerNumber in existingRegisterNumbers || registerNumber in seenRegisterNumbers -> {
                    errors.add("Line $lineNumber: duplicate register number $registerNumber")
                    skipped++
                }
                else -> {
                    seenRegisterNumbers.add(registerNumber)
                    students.add(
                        Student(
                            id = "stu_${registerNumber}",
                            registerNumber = registerNumber,
                            name = name,
                            year = year,
                            section = section,
                            position = position ?: 0, // 0 = auto-assign on insert
                            active = true,
                        )
                    )
                }
            }
        }
        return Result(students, skipped, errors)
    }
}
