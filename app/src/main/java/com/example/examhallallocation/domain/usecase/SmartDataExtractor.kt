package com.example.examhallallocation.domain.usecase

import android.util.Xml
import com.example.examhallallocation.domain.model.*
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Intelligent data extractor that parses native Excel (.xlsx), CSV, TSV,
 * and clipboard-pasted tables for Students, Exams, Teachers, and Halls.
 *
 * Key Capabilities:
 * - Native OpenXML .xlsx parser built entirely with Android/Java SDK (zero external dependencies).
 * - Multi-sheet support: infers year/section from sheet names (e.g. "III-B 2023-27", "IV yr 2022-26").
 * - Flexible column detection: handles arbitrary column ordering and gracefully ignores unneeded columns
 *   (father's name, remarks, phone numbers, etc.).
 * - Scientific notation normalization: converts Excel float cells like "1.10323E+11" back to clean register numbers.
 * - Transfer Certificate (TC) & Debarred detection: auto-marks students with "got TC" or "debarred" as inactive.
 * - Robust fallback logic: guarantees that students are never rejected if a Year column is missing by
 *   using the active UI tab or sheet metadata.
 */
object SmartDataExtractor {

    data class StudentImportResult(
        val students: List<Student>,
        val errors: List<String>,
        val skipped: Int,
    )

    data class ExamImportResult(
        val exams: List<Exam>,
        val errors: List<String>,
        val skipped: Int,
    )

    data class TeacherImportData(
        val teacher: Teacher,
        val initialPassword: String,
    )

    data class TeacherImportResult(
        val teachers: List<TeacherImportData>,
        val errors: List<String>,
        val skipped: Int,
    )

    data class HallImportResult(
        val halls: List<Hall>,
        val errors: List<String>,
        val skipped: Int,
    )

    data class SheetData(
        val name: String,
        val rows: List<List<String>>,
    )

    // =========================================================================
    // 1. STUDENTS
    // =========================================================================

    /**
     * Parses student data from raw bytes (supporting both native .xlsx workbooks and text CSV/TSV).
     */
    fun parseStudents(
        bytes: ByteArray,
        defaultYear: StudentYear? = null,
        existingRegisterNumbers: Set<String> = emptySet(),
    ): StudentImportResult {
        val sheets = if (isZipOrXlsx(bytes)) {
            extractFromXlsx(bytes)
        } else {
            listOf(SheetData(name = "Data", rows = parseRows(String(bytes, Charsets.UTF_8))))
        }
        return processStudentSheets(sheets, defaultYear, existingRegisterNumbers)
    }

    /**
     * Parses student data from text content (CSV, TSV, or clipboard paste).
     */
    fun parseStudents(
        content: String,
        defaultYear: StudentYear? = null,
        existingRegisterNumbers: Set<String> = emptySet(),
    ): StudentImportResult {
        return parseStudents(content.toByteArray(Charsets.UTF_8), defaultYear, existingRegisterNumbers)
    }

    private fun processStudentSheets(
        sheets: List<SheetData>,
        defaultYear: StudentYear?,
        existingRegisterNumbers: Set<String>,
    ): StudentImportResult {
        if (sheets.isEmpty()) return StudentImportResult(emptyList(), listOf("Uploaded file contains no data"), 0)

        val students = mutableListOf<Student>()
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var skipped = 0

        // If defaultYear is specified and some sheets match the year, prioritize matching sheets; otherwise process all sheets
        val targetSheets = if (sheets.size > 1 && defaultYear != null) {
            val matching = sheets.filter { inferYearFromSheetTitle(it.name) == defaultYear }
            if (matching.isNotEmpty()) matching else sheets
        } else {
            sheets
        }

        for (sheet in targetSheets) {
            val rows = sheet.rows
            if (rows.isEmpty()) continue

            // Determine if there is a header in the first 15 rows
            val headerRowIdx = rows.take(15).indexOfFirst { isStudentHeader(it) }
            val hasHeader = headerRowIdx != -1
            val dataRows = if (hasHeader) rows.drop(headerRowIdx + 1) else rows
            val headerRow = if (hasHeader) rows[headerRowIdx] else emptyList()

            val headerIndex = if (hasHeader) buildHeaderMap(headerRow) else emptyMap()
            val regCol = headerIndex.findKey("reg", "roll", "usn", "htno", "candidate", "id", "register")
            val nameCol = headerIndex.findKey("name", "student", "candidate")
            val yearCol = headerIndex.findKey("year", "yr", "batch", "class", "sem")
            val secCol = headerIndex.findKey("sec", "section")
            val posCol = headerIndex.findKey("pos", "position", "sno", "s.no", "slno", "rank")
            val tcCol = headerIndex.findKey("remark", "status", "note", "tc")

            val sheetYear = inferYearFromSheetTitle(sheet.name) ?: inferYearFromHeaderRows(rows.take(15))
            val sheetSection = inferSectionFromSheetTitle(sheet.name) ?: inferSectionFromHeaderRows(rows.take(15))

            dataRows.forEachIndexed { idx, rawCols ->
                val lineNo = if (hasHeader) headerRowIdx + idx + 2 else idx + 1
                if (rawCols.isEmpty() || rawCols.all { it.isBlank() }) return@forEachIndexed

                // Normalize cells (scientific notation, whitespace)
                val cols = rawCols.map { normalizeScientificNumber(it.trim()) }

                var registerNumber = if (regCol != -1) cols.getOrElse(regCol) { "" } else ""
                var name = if (nameCol != -1) cols.getOrElse(nameCol) { "" } else ""
                var yearVal = if (yearCol != -1) cols.getOrElse(yearCol) { "" } else ""
                var sectionVal = if (secCol != -1) cols.getOrElse(secCol) { "" } else ""
                val posVal = if (posCol != -1) cols.getOrElse(posCol) { "" }.toIntOrNull() else null
                val remarkVal = if (tcCol != -1) cols.getOrElse(tcCol) { "" } else ""

                // Positional heuristic fallback if registerNumber wasn't resolved by header
                if (registerNumber.isBlank() || !looksLikeRegisterNumber(registerNumber)) {
                    val candidateIdx = cols.indexOfFirst { looksLikeRegisterNumber(it) }
                    if (candidateIdx != -1) {
                        registerNumber = cols[candidateIdx]
                        if (name.isBlank()) {
                            // Find any text column with letters that isn't the register number or serial number
                            val nameIdx = cols.indices.firstOrNull { i ->
                                i != candidateIdx && cols[i].any { it.isLetter() } && cols[i].length >= 2
                            }
                            if (nameIdx != null) name = cols[nameIdx]
                        }
                    } else if (!hasHeader) {
                        // Strict positional: S.No (col 0), RegNo (col 1), Name (col 2)
                        if (cols.size >= 2 && looksLikeRegisterNumber(cols[1])) {
                            registerNumber = cols[1]
                            name = cols.getOrElse(2) { "" }
                        } else {
                            registerNumber = cols.getOrElse(0) { "" }
                            name = cols.getOrElse(1) { "" }
                        }
                    }
                }

                // If still missing valid register number, skip this row (e.g. repeated header, subtotal row)
                if (registerNumber.isBlank() || !registerNumber.any { it.isDigit() }) {
                    return@forEachIndexed
                }

                // Clean name fallback
                if (name.isBlank()) {
                    name = "Student $registerNumber"
                }

                // Academic Year Determination (Bulletproof Cascade):
                // 1. Explicit cell value
                // 2. Sheet tab name (e.g. "III-B 2023-27" -> YEAR_3, "IV yr" -> YEAR_4)
                // 3. Register number pattern (110323 -> YEAR_3, 110322 -> YEAR_4, 110324/25 -> YEAR_2)
                // 4. Default Year from active screen tab
                // 5. Fallback YEAR_3
                val studentYear = parseStudentYear(yearVal)
                    ?: sheetYear
                    ?: inferYearFromRegNo(registerNumber, defaultYear)
                    ?: defaultYear
                    ?: StudentYear.YEAR_3

                // Section Determination
                val section = when {
                    sectionVal.isNotBlank() -> sectionVal.take(1).uppercase(Locale.ROOT)
                    sheetSection != null -> sheetSection
                    else -> "A"
                }

                // Position (S.No / Roll Number)
                val regDigits = registerNumber.filter { it.isDigit() }
                val regRollSuffix = if (regDigits.length >= 6) {
                    val s3 = regDigits.takeLast(3).toIntOrNull()
                    if (s3 != null && s3 in 1..999) s3 else null
                } else null
                val position = posVal
                    ?: regRollSuffix
                    ?: cols.firstOrNull()?.toIntOrNull()
                    ?: (students.size + 1)

                // TC / Debarred / Inactive Detection
                val isInactive = hasTcOrDebarredRemarks(remarkVal) ||
                        cols.any { it.contains("got tc", ignoreCase = true) || it.contains("tc in", ignoreCase = true) || it.contains("debarred", ignoreCase = true) }

                // Per institutional requirement: completely neglect/drop debarred or non-promoted students
                // from the database so they never take up seats or clutter records
                if (isInactive) {
                    skipped++
                    return@forEachIndexed
                }

                when {
                    registerNumber in existingRegisterNumbers || registerNumber in seen -> {
                        errors.add("Line $lineNo: Duplicate register number $registerNumber skipped")
                        skipped++
                    }
                    else -> {
                        seen.add(registerNumber)
                        students.add(
                            Student(
                                id = "stu_$registerNumber",
                                registerNumber = registerNumber,
                                name = name,
                                year = studentYear,
                                section = section,
                                position = position,
                                active = true,
                            )
                        )
                    }
                }
            }
        }

        val sortedStudents = students.sortedWith(StudentOrderComparator)
        return StudentImportResult(sortedStudents, errors, skipped)
    }

    private fun looksLikeRegisterNumber(raw: String): Boolean {
        val clean = raw.trim()
        if (clean.length !in 6..18) return false
        val digitCount = clean.count { it.isDigit() }
        return digitCount >= 5
    }

    private fun hasTcOrDebarredRemarks(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        if (lower.isEmpty() || lower == "promoted" || lower == "active" || lower == "regular" || lower == "eligible") return false
        return lower.contains("tc") || lower.contains("transfer") || lower.contains("debarred") ||
                lower.contains("discontinued") || lower.contains("detained") || lower.contains("inactive") ||
                lower.contains("not promoted") || lower.contains("absent") || lower.contains("drop")
    }

    // =========================================================================
    // 2. EXAMS / TIMETABLE
    // =========================================================================

    fun parseExams(bytes: ByteArray): ExamImportResult {
        val sheets = if (isZipOrXlsx(bytes)) {
            extractFromXlsx(bytes)
        } else {
            listOf(SheetData(name = "Data", rows = parseRows(String(bytes, Charsets.UTF_8))))
        }
        return processExamSheets(sheets)
    }

    fun parseExams(content: String): ExamImportResult {
        return parseExams(content.toByteArray(Charsets.UTF_8))
    }

    private fun processExamSheets(sheets: List<SheetData>): ExamImportResult {
        val exams = mutableListOf<Exam>()
        val errors = mutableListOf<String>()
        var skipped = 0

        for (sheet in sheets) {
            val rows = sheet.rows
            if (rows.isEmpty()) continue

            val headerRowIdx = rows.take(15).indexOfFirst { isExamHeader(it) }
            val hasHeader = headerRowIdx != -1
            val dataRows = if (hasHeader) rows.drop(headerRowIdx + 1) else rows
            val headerRow = if (hasHeader) rows[headerRowIdx] else emptyList()

            val headerIndex = if (hasHeader) buildHeaderMap(headerRow) else emptyMap()
            val dateCol = headerIndex.findKey("date", "day", "schedule")
            val sessCol = headerIndex.findKey("sessio", "session", "slot")
            val timeCol = headerIndex.findKey("timing", "time", "hour")
            val yearSemCol = headerIndex.findKey("yearsem", "year/sem", "yr/sem", "sem/year", "class", "batch")
            val yearCol = if (yearSemCol != -1) yearSemCol else headerIndex.findKey("year", "yr", "batch")
            val semCol = if (yearSemCol != -1) yearSemCol else headerIndex.findKey("sem", "semester")
            val deptCol = headerIndex.findKey("dept", "department", "branch")
            val codeCol = headerIndex.findKey("subcode", "subjectcode", "code", "paper")
            val nameCol = headerIndex.findKey("subname", "subjectname", "subject", "name", "title", "course")
            val countCol = headerIndex.findKey("count", "studentscount", "studentcount", "strength", "total")

            val detectedExamName = rows.take(10).flatten().firstOrNull {
                it.contains("Assessment", ignoreCase = true) || it.contains("Internal", ignoreCase = true) || it.contains("Test", ignoreCase = true)
            }?.trim() ?: "Assessment Test - I"

            dataRows.forEachIndexed { idx, rawCols ->
                val lineNo = if (hasHeader) headerRowIdx + idx + 2 else idx + 1
                if (rawCols.isEmpty() || rawCols.all { it.isBlank() }) return@forEachIndexed

                val cols = rawCols.map { normalizeScientificNumber(it.trim()) }

                var rawDate = if (dateCol != -1) cols.getOrElse(dateCol) { "" } else ""
                var session = if (sessCol != -1) cols.getOrElse(sessCol) { "FN" } else "FN"
                var timing = if (timeCol != -1) cols.getOrElse(timeCol) { "" } else ""
                var rawYearSem = if (yearSemCol != -1) cols.getOrElse(yearSemCol) { "" } else ""
                var rawYear = if (yearCol != -1) cols.getOrElse(yearCol) { "" } else rawYearSem
                var rawSem = if (semCol != -1) cols.getOrElse(semCol) { "" } else rawYearSem
                var dept = if (deptCol != -1) cols.getOrElse(deptCol) { "CSE" } else "CSE"
                var subCode = if (codeCol != -1) cols.getOrElse(codeCol) { "" } else ""
                var subName = if (nameCol != -1) cols.getOrElse(nameCol) { "" } else ""
                val rawCount = if (countCol != -1) cols.getOrElse(countCol) { "" } else ""
                val studentCount = rawCount.filter { it.isDigit() }.toIntOrNull() ?: 0

                // Fallback positional if completely unlabelled
                if (!hasHeader) {
                    rawDate = cols.getOrElse(0) { "" }
                    session = cols.getOrElse(1) { "FN" }
                    timing = cols.getOrElse(2) { "" }
                    rawYear = cols.getOrElse(3) { "" }
                    dept = cols.getOrElse(4) { "CSE" }
                    subCode = cols.getOrElse(5) { "" }
                    subName = cols.getOrElse(6) { "" }
                }

                // Search for date cell if not found
                val normDate = normalizeDate(rawDate) ?: cols.firstNotNullOfOrNull { normalizeDate(it) }

                // Search for subject code
                if (subCode.isBlank() || !looksLikeSubjectCode(subCode)) {
                    val codeCandidate = cols.firstOrNull { looksLikeSubjectCode(it) }
                    if (codeCandidate != null) subCode = codeCandidate
                }

                // Search for subject name
                if (subName.isBlank() || subName == subCode) {
                    val nameCandidate = cols.firstOrNull { it != subCode && it != rawDate && it.length > 3 && it.any { c -> c.isLetter() } }
                    if (nameCandidate != null) subName = nameCandidate
                }

                // Split combined "Year / Sem" column like "II / 03", "III / 05", "IV / 07"
                val (yearStr, semStr) = if (rawYear.contains("/")) {
                    val p = rawYear.split("/")
                    p.getOrNull(0)?.trim().orEmpty() to p.getOrNull(1)?.trim().orEmpty()
                } else {
                    rawYear to rawSem
                }

                val semInt = semStr.filter { it.isDigit() }.toIntOrNull() ?: rawSem.filter { it.isDigit() }.toIntOrNull()
                val year = parseStudentYear(yearStr)
                    ?: parseStudentYear(rawYear)
                    ?: inferYearFromSemester(semInt)
                    ?: inferYearFromSheetTitle(sheet.name)
                    ?: StudentYear.YEAR_2

                val effectiveSem = semInt ?: when (year) {
                    StudentYear.YEAR_1 -> 1
                    StudentYear.YEAR_2 -> 3
                    StudentYear.YEAR_3 -> 5
                    StudentYear.YEAR_4 -> 7
                    else -> 3
                }

                when {
                    normDate == null -> {
                        errors.add("Line $lineNo: Invalid or missing date \"$rawDate\"")
                        skipped++
                    }
                    subCode.isBlank() -> {
                        errors.add("Line $lineNo: Missing subject code")
                        skipped++
                    }
                    else -> {
                        val finalName = if (subName.isNotBlank()) subName else subCode
                        exams.add(
                            Exam(
                                id = "exam_${normDate}_${subCode.lowercase()}_${year.value}",
                                examName = detectedExamName,
                                date = normDate,
                                year = year,
                                semester = effectiveSem,
                                subjectCode = subCode.uppercase(Locale.ROOT),
                                subjectName = finalName,
                                session = session.ifBlank { "FN" },
                                timing = timing.ifBlank { "8:40 a.m. TO 10:10 a.m." },
                                department = dept.ifBlank { "CSE" },
                                studentCount = studentCount,
                            )
                        )
                    }
                }
            }
        }

        return ExamImportResult(exams, errors, skipped)
    }

    // =========================================================================
    // 3. TEACHERS / FACULTY
    // =========================================================================

    fun parseTeachers(
        bytes: ByteArray,
        defaultDomain: String = "grt.edu.in",
    ): TeacherImportResult {
        val sheets = if (isZipOrXlsx(bytes)) {
            extractFromXlsx(bytes)
        } else {
            listOf(SheetData(name = "Data", rows = parseRows(String(bytes, Charsets.UTF_8))))
        }
        return processTeacherSheets(sheets, defaultDomain)
    }

    fun parseTeachers(
        content: String,
        defaultDomain: String = "grt.edu.in",
    ): TeacherImportResult {
        return parseTeachers(content.toByteArray(Charsets.UTF_8), defaultDomain)
    }

    private fun processTeacherSheets(
        sheets: List<SheetData>,
        defaultDomain: String,
    ): TeacherImportResult {
        val teachers = mutableListOf<TeacherImportData>()
        val errors = mutableListOf<String>()
        val seenUsernames = mutableSetOf<String>()
        var skipped = 0

        for (sheet in sheets) {
            val rows = sheet.rows
            if (rows.isEmpty()) continue

            val headerRowIdx = rows.take(15).indexOfFirst { isTeacherHeader(it) }
            val hasHeader = headerRowIdx != -1
            val dataRows = if (hasHeader) rows.drop(headerRowIdx + 1) else rows
            val headerRow = if (hasHeader) rows[headerRowIdx] else emptyList()

            val headerIndex = if (hasHeader) buildHeaderMap(headerRow) else emptyMap()
            val nameCol = headerIndex.findKey("name", "faculty", "staff", "teacher")
            val userCol = headerIndex.findKey("user", "username", "email", "login", "id", "staffid")
            val passCol = headerIndex.findKey("pass", "password", "pwd")
            val roleCol = headerIndex.findKey("role", "designation", "dept", "post")

            dataRows.forEachIndexed { idx, rawCols ->
                val lineNo = if (hasHeader) headerRowIdx + idx + 2 else idx + 1
                if (rawCols.isEmpty() || rawCols.all { it.isBlank() }) return@forEachIndexed

                val cols = rawCols.map { normalizeScientificNumber(it.trim()) }

                var name = if (nameCol != -1) cols.getOrElse(nameCol) { "" } else ""
                var username = if (userCol != -1) cols.getOrElse(userCol) { "" } else ""
                var rawPass = if (passCol != -1) cols.getOrElse(passCol) { "" } else ""
                var rawRole = if (roleCol != -1) cols.getOrElse(roleCol) { "" } else ""

                if (!hasHeader) {
                    name = cols.getOrElse(0) { "" }
                    username = cols.getOrElse(1) { "" }
                    rawPass = cols.getOrElse(2) { "" }
                    rawRole = cols.getOrElse(3) { "" }
                }

                if (name.isBlank()) {
                    val candidateName = cols.firstOrNull { it.length >= 3 && it.any { c -> c.isLetter() } && !it.contains("@") }
                    if (candidateName != null) name = candidateName
                }

                if (username.isBlank() && name.isNotBlank()) {
                    // Generate a clean username from name: "Dr. K. Rajesh" -> "rajesh"
                    val parts = name.split(" ").filter { it.isNotBlank() && !it.equals("dr", ignoreCase = true) && !it.equals("prof", ignoreCase = true) }
                    username = parts.lastOrNull()?.replace(Regex("[^A-Za-z0-9]"), "")?.lowercase(Locale.ROOT)
                        ?: name.replace(Regex("[^A-Za-z0-9]"), "").lowercase(Locale.ROOT).take(12)
                }

                val cleanUsername = if (username.contains("@")) username.substringBefore("@").lowercase(Locale.ROOT) else username.lowercase(Locale.ROOT)
                val initialPassword = if (rawPass.length >= 6) rawPass else "${cleanUsername}@123"
                val role = parseRole(rawRole)

                when {
                    name.isBlank() -> {
                        errors.add("Line $lineNo: Teacher name is required")
                        skipped++
                    }
                    cleanUsername.isBlank() -> {
                        errors.add("Line $lineNo: Teacher username/email is required")
                        skipped++
                    }
                    cleanUsername in seenUsernames -> {
                        errors.add("Line $lineNo: Duplicate username \"$cleanUsername\" skipped")
                        skipped++
                    }
                    else -> {
                        seenUsernames.add(cleanUsername)
                        teachers.add(
                            TeacherImportData(
                                teacher = Teacher(
                                    id = "tea_$cleanUsername",
                                    name = name,
                                    username = cleanUsername,
                                    role = role,
                                    active = true,
                                ),
                                initialPassword = initialPassword,
                            )
                        )
                    }
                }
            }
        }

        return TeacherImportResult(teachers, errors, skipped)
    }

    // =========================================================================
    // 4. HALLS / ROOMS
    // =========================================================================

    fun parseHalls(bytes: ByteArray): HallImportResult {
        val sheets = if (isZipOrXlsx(bytes)) {
            extractFromXlsx(bytes)
        } else {
            listOf(SheetData(name = "Data", rows = parseRows(String(bytes, Charsets.UTF_8))))
        }
        return processHallSheets(sheets)
    }

    fun parseHalls(content: String): HallImportResult {
        return parseHalls(content.toByteArray(Charsets.UTF_8))
    }

    private fun processHallSheets(sheets: List<SheetData>): HallImportResult {
        val halls = mutableListOf<Hall>()
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var skipped = 0

        for (sheet in sheets) {
            val rows = sheet.rows
            if (rows.isEmpty()) continue

            val headerRowIdx = rows.take(15).indexOfFirst { isHallHeader(it) }
            val hasHeader = headerRowIdx != -1
            val dataRows = if (hasHeader) rows.drop(headerRowIdx + 1) else rows
            val headerRow = if (hasHeader) rows[headerRowIdx] else emptyList()

            val headerIndex = if (hasHeader) buildHeaderMap(headerRow) else emptyMap()
            val roomCol = headerIndex.findKey("room", "hall", "hallno", "roomno", "number")
            val blockCol = headerIndex.findKey("block", "building")
            val floorCol = headerIndex.findKey("floor", "level")
            val capCol = headerIndex.findKey("capacity", "cap", "seats", "benches")

            dataRows.forEachIndexed { idx, rawCols ->
                val lineNo = if (hasHeader) headerRowIdx + idx + 2 else idx + 1
                if (rawCols.isEmpty() || rawCols.all { it.isBlank() }) return@forEachIndexed

                val cols = rawCols.map { normalizeScientificNumber(it.trim()) }

                var roomNo = if (roomCol != -1) cols.getOrElse(roomCol) { "" } else ""
                var block = if (blockCol != -1) cols.getOrElse(blockCol) { "" } else ""
                var floor = if (floorCol != -1) cols.getOrElse(floorCol) { "" }.toIntOrNull() else null
                var capacity = if (capCol != -1) cols.getOrElse(capCol) { "" }.toIntOrNull() else null

                if (!hasHeader) {
                    roomNo = cols.getOrElse(0) { "" }
                    block = cols.getOrElse(1) { "A" }
                    floor = cols.getOrElse(2) { "1" }.toIntOrNull() ?: 1
                    capacity = cols.getOrElse(3) { "30" }.toIntOrNull() ?: 30
                }

                if (roomNo.isBlank()) {
                    val candidate = cols.firstOrNull { it.any { c -> c.isDigit() } && it.length in 2..8 }
                    if (candidate != null) roomNo = candidate
                }

                if (floor == null && roomNo.length >= 3 && roomNo.first().isDigit()) {
                    floor = roomNo.first().digitToInt()
                }

                when {
                    roomNo.isBlank() -> {
                        errors.add("Line $lineNo: Hall / room number is required")
                        skipped++
                    }
                    roomNo.lowercase() in seen -> {
                        errors.add("Line $lineNo: Duplicate room number $roomNo skipped")
                        skipped++
                    }
                    else -> {
                        seen.add(roomNo.lowercase())
                        halls.add(
                            Hall(
                                id = "hall_${roomNo.lowercase()}",
                                roomNumber = roomNo,
                                block = block.ifBlank { "Main Block" },
                                floor = floor ?: 1,
                                capacity = capacity ?: 30,
                                active = true,
                            )
                        )
                    }
                }
            }
        }

        return HallImportResult(halls, errors, skipped)
    }

    // =========================================================================
    // NATIVE OPENXML .XLSX ZIP PARSER (ZERO EXTERNAL DEPENDENCIES)
    // =========================================================================

    /**
     * Checks if the binary buffer starts with the PK\x03\x04 signature of a ZIP/XLSX file.
     */
    fun isZipOrXlsx(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()

    /**
     * Extracts sheet names, rows, and cell strings directly from an .xlsx file using
     * Android's built-in XmlPullParser and ZipInputStream.
     */
    fun extractFromXlsx(bytes: ByteArray): List<SheetData> {
        val sharedStrings = mutableListOf<String>()
        val sheetXmlMap = mutableMapOf<String, ByteArray>()
        val sheetNames = mutableMapOf<String, String>()
        val sheetOrder = mutableListOf<String>()

        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    when {
                        entryName == "xl/sharedStrings.xml" -> {
                            sharedStrings.addAll(parseSharedStrings(zip.readBytes()))
                        }
                        entryName == "xl/workbook.xml" -> {
                            parseWorkbookSheets(zip.readBytes(), sheetNames, sheetOrder)
                        }
                        entryName.startsWith("xl/worksheets/sheet") && entryName.endsWith(".xml") -> {
                            val simpleName = entryName.substringAfterLast("/")
                            sheetXmlMap[simpleName] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val result = mutableListOf<SheetData>()
        val orderedKeys = mutableListOf<String>()
        for (key in sheetOrder) {
            if (sheetXmlMap.containsKey(key) && !orderedKeys.contains(key)) {
                orderedKeys.add(key)
            }
        }
        for (key in sheetXmlMap.keys.sorted()) {
            if (!orderedKeys.contains(key)) {
                orderedKeys.add(key)
            }
        }

        for (key in orderedKeys) {
            val xmlData = sheetXmlMap[key] ?: continue
            val title = sheetNames[key] ?: key.substringBefore(".xml")
            val rows = parseSheetXml(xmlData, sharedStrings)
            if (rows.isNotEmpty()) {
                result.add(SheetData(name = title, rows = rows))
            }
        }

        return result
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            val currentString = StringBuilder()
            var insideSi = false
            var insideT = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "si" -> {
                                insideSi = true
                                currentString.clear()
                            }
                            "t" -> insideT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideSi && insideT) {
                            currentString.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "t" -> insideT = false
                            "si" -> {
                                strings.add(currentString.toString())
                                insideSi = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return strings
    }

    private fun parseWorkbookSheets(
        xmlBytes: ByteArray,
        sheetNames: MutableMap<String, String>,
        sheetOrder: MutableList<String>,
    ) {
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var eventType = parser.eventType
            var sheetIdx = 1

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet$sheetIdx"
                    val fileName = "sheet$sheetIdx.xml"
                    sheetNames[fileName] = name
                    sheetOrder.add(fileName)
                    sheetIdx++
                }
                eventType = parser.next()
            }
        }
    }

    private fun parseSheetXml(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            var currentRowCells = mutableMapOf<Int, String>()
            var maxCol = 0
            var currentCellCol = 0
            var currentCellType = ""
            val currentCellValue = StringBuilder()
            var insideV = false
            var insideT = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> {
                                currentRowCells = mutableMapOf()
                                maxCol = 0
                            }
                            "c" -> {
                                val ref = parser.getAttributeValue(null, "r").orEmpty()
                                currentCellCol = colRefToIndex(ref)
                                if (currentCellCol > maxCol) maxCol = currentCellCol
                                currentCellType = parser.getAttributeValue(null, "t").orEmpty()
                                currentCellValue.clear()
                            }
                            "v" -> insideV = true
                            "t" -> insideT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideV || insideT) {
                            currentCellValue.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v" -> insideV = false
                            "t" -> insideT = false
                            "c" -> {
                                val raw = currentCellValue.toString().trim()
                                val finalValue = when (currentCellType) {
                                    "s" -> {
                                        val idx = raw.toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                                    }
                                    else -> normalizeScientificNumber(raw)
                                }
                                currentRowCells[currentCellCol] = finalValue
                            }
                            "row" -> {
                                if (currentRowCells.isNotEmpty() && currentRowCells.values.any { it.isNotBlank() }) {
                                    val rowList = (0..maxCol).map { c -> currentRowCells[c].orEmpty() }
                                    rows.add(rowList)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return rows
    }

    private fun colRefToIndex(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return if (col > 0) col - 1 else 0
    }

    // =========================================================================
    // HELPERS & CSV / TSV PARSING
    // =========================================================================

    /** Normalizes numbers that Excel stored as scientific notation (e.g. "1.10323E+11" -> "110323000000"). */
    fun normalizeScientificNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        if ((trimmed.contains("E") || trimmed.contains("e")) && trimmed.any { it.isDigit() }) {
            runCatching {
                val bd = BigDecimal(trimmed)
                return bd.toPlainString().substringBefore('.')
            }
        }
        if (trimmed.endsWith(".0") && trimmed.dropLast(2).all { it.isDigit() }) {
            return trimmed.dropLast(2)
        }
        return trimmed
    }

    /** Parses raw text with dynamic delimiter auto-detection (comma, tab, semicolon) and quotes support. */
    fun parseRows(content: String): List<List<String>> {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val sample = lines.take(5).joinToString("\n")
        val tabCount = sample.count { it == '\t' }
        val commaCount = sample.count { it == ',' }
        val semiCount = sample.count { it == ';' }

        val delimiter = when {
            tabCount > commaCount && tabCount > semiCount -> '\t'
            semiCount > commaCount -> ';'
            else -> ','
        }

        return lines.map { line -> parseLineWithQuotes(line, delimiter) }
    }

    private fun parseLineWithQuotes(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var insideQuotes = false

        for (char in line) {
            when {
                char == '\"' -> insideQuotes = !insideQuotes
                char == delimiter && !insideQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(char)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun buildHeaderMap(headerRow: List<String>): Map<String, Int> =
        headerRow.mapIndexed { idx, name ->
            name.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]"), "") to idx
        }.toMap()

    private fun Map<String, Int>.findKey(vararg patterns: String): Int {
        for ((colName, idx) in this) {
            for (p in patterns) {
                if (colName.contains(p)) return idx
            }
        }
        return -1
    }

    private fun isStudentHeader(row: List<String>): Boolean {
        val nonBlank = row.filter { it.isNotBlank() }
        if (nonBlank.size < 2) return false
        val flat = nonBlank.joinToString(" ").lowercase(Locale.ROOT)
        val hasRegOrRoll = flat.contains("roll") || flat.contains("reg") || flat.contains("usn") || flat.contains("htno")
        val hasNameOrStudent = flat.contains("name") || flat.contains("student") || flat.contains("candidate")
        return hasRegOrRoll && hasNameOrStudent
    }

    private fun isExamHeader(row: List<String>): Boolean {
        val nonBlank = row.filter { it.isNotBlank() }
        if (nonBlank.size < 3) return false
        val flat = nonBlank.joinToString(" ").lowercase(Locale.ROOT)
        val hasSub = flat.contains("subject") || flat.contains("subcode") || flat.contains("course") || flat.contains("code")
        val hasDateOrTime = flat.contains("date") || flat.contains("time") || flat.contains("sem") || flat.contains("timing")
        return hasSub && hasDateOrTime
    }

    private fun isTeacherHeader(row: List<String>): Boolean {
        val flat = row.joinToString(" ").lowercase(Locale.ROOT)
        return flat.contains("teacher") || flat.contains("faculty") || flat.contains("staff") ||
                flat.contains("designation") || flat.contains("employee")
    }

    private fun isHallHeader(row: List<String>): Boolean {
        val flat = row.joinToString(" ").lowercase(Locale.ROOT)
        return flat.contains("room") || flat.contains("hall") || flat.contains("block") || flat.contains("capacity")
    }

    private fun parseStudentYear(raw: String): StudentYear? {
        val clean = raw.trim().lowercase(Locale.ROOT)
            .removeSuffix("rd").removeSuffix("nd").removeSuffix("th").removeSuffix("st").trim()
        return when (clean) {
            "1", "i", "first" -> StudentYear.YEAR_1
            "2", "ii", "second" -> StudentYear.YEAR_2
            "3", "iii", "third" -> StudentYear.YEAR_3
            "4", "iv", "fourth", "final" -> StudentYear.YEAR_4
            else -> null
        }
    }

    private fun inferYearFromSheetTitle(sheetTitle: String): StudentYear? {
        val clean = sheetTitle.uppercase(Locale.ROOT)
        return when {
            clean.contains("IV") || clean.contains("4TH") || clean.contains("FINAL") || clean.contains("2022-26") -> StudentYear.YEAR_4
            clean.contains("III") || clean.contains("3RD") || clean.contains("2023-27") -> StudentYear.YEAR_3
            clean.contains("II") || clean.contains("2ND") || clean.contains("2024-28") || clean.contains("2025-29") -> StudentYear.YEAR_2
            else -> null
        }
    }

    private fun inferYearFromHeaderRows(topRows: List<List<String>>): StudentYear? {
        for (row in topRows) {
            val text = row.joinToString(" ").uppercase(Locale.ROOT)
            when {
                text.contains("IV YEAR") || text.contains("4TH YEAR") || text.contains("FOURTH YEAR") || text.contains("FINAL YEAR") || text.contains("2022-26") -> return StudentYear.YEAR_4
                text.contains("III YEAR") || text.contains("3RD YEAR") || text.contains("THIRD YEAR") || text.contains("2023-27") -> return StudentYear.YEAR_3
                text.contains("II YEAR") || text.contains("2ND YEAR") || text.contains("SECOND YEAR") || text.contains("2024-28") || text.contains("2025-29") -> return StudentYear.YEAR_2
            }
        }
        return null
    }

    private fun inferSectionFromSheetTitle(sheetTitle: String): String? {
        val clean = sheetTitle.uppercase(Locale.ROOT).trim()
        return when {
            clean.contains("-A") || clean.contains("_A") || clean.contains("SEC A") || clean.contains("SECTION A") || clean.endsWith(" A") || clean == "A" -> "A"
            clean.contains("-B") || clean.contains("_B") || clean.contains("SEC B") || clean.contains("SECTION B") || clean.endsWith(" B") || clean == "B" -> "B"
            clean.contains("-C") || clean.contains("_C") || clean.contains("SEC C") || clean.contains("SECTION C") || clean.endsWith(" C") || clean == "C" -> "C"
            else -> null
        }
    }

    private fun inferSectionFromHeaderRows(topRows: List<List<String>>): String? {
        for (row in topRows) {
            val text = row.joinToString(" ").uppercase(Locale.ROOT)
            val match = Regex("""(?:SEC|SECTION|CSE|ECE|MECH|IT|EEE|AIDS)\s*["'-]?\s*([A-C])\b""").find(text)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    /**
     * Deduces academic year from Anna University / GRT roll number patterns.
     * College Code 1103:
     * - 110323... -> Joined 2023 -> 3rd Year (in 2025-2026 academic year)
     * - 110322... -> Joined 2022 -> 4th Year (Final Year)
     * - 110324... -> Joined 2024 -> 2nd Year
     * - 110325... -> Joined 2025 -> 2nd Year
     * If pattern is unfamiliar, falls back to [defaultYear] or YEAR_3.
     */
    private fun inferYearFromRegNo(regNo: String, defaultYear: StudentYear?): StudentYear? {
        val clean = regNo.trim()
        return when {
            clean.startsWith("110326") -> StudentYear.YEAR_1
            clean.startsWith("110322") -> StudentYear.YEAR_4
            clean.startsWith("110323") -> defaultYear ?: StudentYear.YEAR_3
            clean.startsWith("110324") -> StudentYear.YEAR_2
            clean.startsWith("110325") -> StudentYear.YEAR_2
            clean.contains("26") -> StudentYear.YEAR_1
            clean.contains("22") -> StudentYear.YEAR_4
            clean.contains("23") -> defaultYear ?: StudentYear.YEAR_3
            clean.contains("24") -> StudentYear.YEAR_2
            clean.contains("25") -> StudentYear.YEAR_2
            else -> defaultYear
        }
    }

    private fun inferYearFromSemester(sem: Int?): StudentYear? = when (sem) {
        1, 2 -> StudentYear.YEAR_1
        3, 4 -> StudentYear.YEAR_2
        5, 6 -> StudentYear.YEAR_3
        7, 8 -> StudentYear.YEAR_4
        else -> null
    }

    fun normalizeDate(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        // ISO format: yyyy-MM-dd
        runCatching { return LocalDate.parse(value).toString() }

        // dd-MM-yyyy / dd/MM/yyyy / dd.MM.yyyy (even with day names like "17-08-2026 (Monday)")
        Regex("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})").find(value)?.let { match ->
            val (day, month, year) = match.destructured
            return runCatching {
                LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toString()
            }.getOrNull()
        }

        // Textual month: "13 Apr 2026", "13 April 2026"
        val months = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )
        Regex("^(\\d{1,2})\\s+([A-Za-z]{3,9})\\s+(\\d{4})$").find(value)?.let { match ->
            val (day, monthName, year) = match.destructured
            val m = months[monthName.take(3).lowercase(Locale.ROOT)]
            if (m != null) {
                return runCatching { LocalDate.of(year.toInt(), m, day.toInt()).toString() }.getOrNull()
            }
        }

        // Excel numeric serial date (e.g. 46125)
        value.toDoubleOrNull()?.let { serial ->
            if (serial in 30000.0..60000.0) {
                return runCatching {
                    LocalDate.of(1899, 12, 30).plusDays(serial.toLong()).toString()
                }.getOrNull()
            }
        }

        return null
    }

    private fun looksLikeSubjectCode(raw: String): Boolean {
        val clean = raw.trim()
        return clean.length in 5..9 && clean.any { it.isLetter() } && clean.any { it.isDigit() }
    }

    private fun parseRole(raw: String): UserRole {
        val clean = raw.trim().uppercase(Locale.ROOT)
        return when {
            clean.contains("COORD") || clean.contains("CELL") -> UserRole.EXAM_CELL_COORDINATOR
            clean.contains("ADMIN") -> UserRole.ADMIN
            else -> UserRole.NORMAL_TEACHER
        }
    }
}
