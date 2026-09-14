package com.example.examhallallocation.domain.usecase

import com.example.examhallallocation.domain.model.Exam
import com.example.examhallallocation.domain.model.Hall
import com.example.examhallallocation.domain.model.StudentYear
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV parsers for bulk exam-schedule and hall imports, mirroring [CsvParser] for
 * students. Headers are optional; per-line errors carry line numbers so the admin
 * can fix the file in one pass. Dates accept the common formats used by colleges
 * (2026-04-13, 13-04-2026, 13/04/2026, 13.04.2026, 13 Apr 2026) and are normalized
 * to ISO yyyy-MM-dd, which is what the generation engine expects.
 */
object ExamCsvParser {

    data class Result(val exams: List<Exam>, val errors: List<String>) {
        val skipped: Int get() = errors.size
    }

    private val monthNames = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    fun parse(content: String, examName: String = "End Semester Examinations"): Result {
        val exams = mutableListOf<Exam>()
        val errors = mutableListOf<String>()

        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val parts = line.split(",").map { it.trim() }

            if (index == 0 && line.lowercase(Locale.ROOT).contains("subject")) {
                return@forEachIndexed // header row
            }

            if (parts.size < 5) {
                errors.add("Line $lineNumber: expected Date,Year,Semester,SubjectCode,SubjectName")
                return@forEachIndexed
            }

            val date = normalizeDate(parts[0])
            val year = parseYear(parts[1])
            val semester = parts[2].toIntOrNull()
            val subjectCode = parts[3]
            val subjectName = parts[4]

            when {
                date == null -> errors.add("Line $lineNumber: unreadable date \"${parts[0]}\"")
                year == null -> errors.add("Line $lineNumber: year must be 2, 3 or 4")
                semester == null || semester !in 3..8 ->
                    errors.add("Line $lineNumber: semester must be between 3 and 8")
                subjectCode.isEmpty() || subjectName.isEmpty() ->
                    errors.add("Line $lineNumber: subject code and name are required")
                else -> exams.add(
                    Exam(
                        id = "exam_${date}_${year.value}_$subjectCode",
                        examName = examName,
                        date = date,
                        year = year,
                        semester = semester,
                        subjectCode = subjectCode,
                        subjectName = subjectName,
                    )
                )
            }
        }
        return Result(exams, errors)
    }

    /** Accepts ISO, dd-MM-yyyy, dd/MM/yyyy, dd.MM.yyyy and "13 Apr 2026" styles. */
    fun normalizeDate(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        // Strict ISO (yyyy-MM-dd) first.
        runCatching { return LocalDate.parse(value).toString() }

        // Numeric day-first: dd-MM-yyyy / dd/MM/yyyy / dd.MM.yyyy
        Regex("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$").find(value)?.let { match ->
            val (day, month, year) = match.destructured
            return runCatching {
                LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toString()
            }.getOrNull()
        }

        // Textual month: "13 Apr 2026" / "13 April 2026"
        Regex("^(\\d{1,2})\\s+([A-Za-z]{3,9})\\s+(\\d{4})$").find(value)?.let { match ->
            val (day, monthName, year) = match.destructured
            val month = monthNames[monthName.take(3).lowercase(Locale.ROOT)]
            return month?.let {
                runCatching { LocalDate.of(year.toInt(), it, day.toInt()).toString() }.getOrNull()
            }
        }
        return null
    }

    private fun parseYear(raw: String): StudentYear? =
        when (raw.trim().lowercase(Locale.ROOT).removeSuffix("rd").removeSuffix("nd").removeSuffix("th").trim()) {
            "2" -> StudentYear.YEAR_2
            "3" -> StudentYear.YEAR_3
            "4" -> StudentYear.YEAR_4
            else -> null
        }
}

/** CSV parser for hall bulk import: RoomNumber,Block,Floor,Capacity,Active(optional). */
object HallCsvParser {

    data class Result(val halls: List<Hall>, val errors: List<String>) {
        val skipped: Int get() = errors.size
    }

    fun parse(content: String): Result {
        val halls = mutableListOf<Hall>()
        val errors = mutableListOf<String>()

        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val parts = line.split(",").map { it.trim() }

            if (index == 0 && line.lowercase(Locale.ROOT).contains("room")) {
                return@forEachIndexed // header row
            }

            if (parts.size < 4) {
                errors.add("Line $lineNumber: expected RoomNumber,Block,Floor,Capacity")
                return@forEachIndexed
            }

            val roomNumber = parts[0]
            val block = parts[1]
            val floor = parts[2].toIntOrNull()
            val capacity = parts[3].toIntOrNull()
            val active = parts.getOrNull(4)?.lowercase(Locale.ROOT) != "false"

            when {
                roomNumber.isEmpty() || block.isEmpty() ->
                    errors.add("Line $lineNumber: room number and block are required")
                floor == null || floor < 0 ->
                    errors.add("Line $lineNumber: floor must be 0 or more")
                capacity == null || capacity !in 1..60 ->
                    errors.add("Line $lineNumber: capacity must be between 1 and 60")
                else -> halls.add(
                    Hall(
                        id = "hall_${block}_$roomNumber",
                        roomNumber = roomNumber,
                        block = block,
                        floor = floor,
                        capacity = capacity,
                        active = active,
                    )
                )
            }
        }
        return Result(halls, errors)
    }
}
