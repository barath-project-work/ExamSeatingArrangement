package com.example.examhallallocation.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.examhallallocation.R
import com.example.examhallallocation.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 100% Native Android PDF Generator built with android.graphics.pdf.PdfDocument.
 * Completely immune to Java AWT/iText runtime linkage crashes on Android devices.
 */
@Singleton
class PdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val colorNavy = Color.rgb(0x17, 0x32, 0x4D)
    private val colorTextDark = Color.rgb(0x1E, 0x29, 0x3B)
    private val colorTextMuted = Color.rgb(0x64, 0x74, 0x8B)
    private val colorBorder = Color.rgb(0xCB, 0xD5, 0xE1)
    private val colorZebra = Color.rgb(0xF8, 0xFA, 0xFC)
    private val colorOrangeAccent = Color.rgb(0xFE, 0xF3, 0xC7)

    private fun loadBannerBitmap(): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        BitmapFactory.decodeResource(context.resources, R.drawable.grt_banner, opts)
    }.getOrNull()

    /**
     * Generates formal examination seating arrangement PDF in landscape A4.
     */
    fun generate(arrangements: List<Arrangement>, halls: List<Hall>, outFile: File): File {
        val pdfDoc = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595
        val margin = 36f

        val hallById = halls.associateBy { it.id }

        // Extract flattened rows for rendering
        val rowsToPrint = mutableListOf<ArrangementRow>()
        var globalSno = 1

        arrangements.sortedBy { it.date }.forEach { arr ->
            val byHall = arr.hallAssignments.groupBy { it.hallId }
                .toSortedMap(compareBy { hallById[it]?.roomNumber ?: it })

            byHall.forEach { (hallId, blocks) ->
                val hall = hallById[hallId]
                val hallTotal = blocks.sumOf { it.studentIds.size }
                blocks.forEachIndexed { blockIndex, block ->
                    val regNumbers = block.studentIds.map { it.removePrefix("stu_") }
                    rowsToPrint.add(
                        ArrangementRow(
                            sno = if (blockIndex == 0) globalSno.toString() else "",
                            hallNumber = if (blockIndex == 0) (hall?.roomNumber ?: hallId) else "",
                            floor = if (blockIndex == 0) (hall?.floor?.toString().orEmpty()) else "",
                            branch = if (blockIndex == 0) "CSE" else "",
                            yearSem = "${block.year.label} / Sem ${block.semester}",
                            regFrom = regNumbers.firstOrNull() ?: "-",
                            regTo = regNumbers.lastOrNull() ?: "-",
                            count = block.studentIds.size.toString(),
                            hallTotal = if (blockIndex == 0) hallTotal.toString() else "",
                            isNewHall = blockIndex == 0,
                            date = arr.date,
                            phase = arr.phase,
                        )
                    )
                }
                globalSno++
            }
        }

        // Layout parameters
        val rowsPerPage = 14
        val chunks = if (rowsToPrint.isNotEmpty()) rowsToPrint.chunked(rowsPerPage) else listOf(emptyList())
        val totalPages = chunks.size

        val bannerBitmap = runCatching {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeResource(context.resources, R.drawable.grt_banner, opts)
        }.getOrNull()

        try {
            chunks.forEachIndexed { pageIndex, pageRows ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // 1. Draw Header
                val contentTop = drawLandscapeHeader(canvas, pageWidth.toFloat(), margin, arrangements, bannerBitmap)

                // 2. Draw Table
                val tableBottom = drawLandscapeTable(canvas, margin, contentTop, pageWidth.toFloat() - margin, pageRows)

                // 3. Draw Signatures on last page or bottom
                if (pageIndex == totalPages - 1) {
                    drawLandscapeSignatures(canvas, margin, pageWidth.toFloat() - margin, pageHeight - 36f)
                }

                // 4. Page number footer
                drawFooter(canvas, pageWidth.toFloat(), pageHeight.toFloat(), pageIndex + 1, totalPages)

                pdfDoc.finishPage(page)
            }

            FileOutputStream(outFile).use { fos ->
                pdfDoc.writeTo(fos)
            }
        } finally {
            runCatching { bannerBitmap?.recycle() }
            pdfDoc.close()
        }
        return outFile
    }

    private data class ArrangementRow(
        val sno: String,
        val hallNumber: String,
        val floor: String,
        val branch: String,
        val yearSem: String,
        val regFrom: String,
        val regTo: String,
        val count: String,
        val hallTotal: String,
        val isNewHall: Boolean,
        val date: String,
        val phase: ExamPhase,
    )

    private fun drawLandscapeHeader(
        canvas: Canvas,
        width: Float,
        margin: Float,
        arrangements: List<Arrangement>,
        bannerBitmap: Bitmap?
    ): Float {
        var y = margin

        // Draw banner logo if available
        if (bannerBitmap != null && bannerBitmap.width > 0 && bannerBitmap.height > 0) {
            val desiredWidth = 260f
            val desiredHeight = desiredWidth * (bannerBitmap.height.toFloat() / bannerBitmap.width.toFloat())
            val dst = RectF(margin, y, margin + desiredWidth, y + desiredHeight)
            canvas.drawBitmap(bannerBitmap, null, dst, null)
        }

        val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNavy
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val paintSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextMuted
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }

        val titleX = if (bannerBitmap != null) margin + 280f else margin
        canvas.drawText("GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY (Autonomous)", titleX, y + 16f, paintTitle)
        canvas.drawText("DEPARTMENT OF COMPUTER SCIENCE AND ENGINEERING · EXAM SEATING ALLOCATION", titleX, y + 32f, paintSub)

        val dates = arrangements.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        val dateRangeStr = if (dates.isNotEmpty()) {
            val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
            "Exam Period: ${dates.minOrNull()?.format(fmt)} to ${dates.maxOrNull()?.format(fmt)}"
        } else "Exam Hall Seating Plan"
        canvas.drawText(dateRangeStr, titleX, y + 46f, paintSub)

        return y + 58f
    }

    private fun drawLandscapeTable(canvas: Canvas, left: Float, top: Float, right: Float, rows: List<ArrangementRow>): Float {
        val totalWidth = right - left
        // Column width distribution percentage
        val colWeights = floatArrayOf(0.05f, 0.10f, 0.07f, 0.08f, 0.16f, 0.18f, 0.18f, 0.08f, 0.10f)
        val colWidths = colWeights.map { it * totalWidth }
        val headers = arrayOf("S.No", "Hall No", "Floor", "Dept", "Year / Sem", "Reg No From", "Reg No To", "Count", "Total")

        val paintBg = Paint()
        val paintBorder = Paint().apply {
            color = colorBorder
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val paintHeaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val paintCellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextDark
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val paintBoldText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNavy
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Draw header row
        val headerHeight = 22f
        paintBg.color = colorNavy
        canvas.drawRect(left, top, right, top + headerHeight, paintBg)
        canvas.drawRect(left, top, right, top + headerHeight, paintBorder)

        var curX = left
        headers.forEachIndexed { i, h ->
            val w = colWidths[i]
            canvas.drawText(h, curX + w / 2f, top + 15f, paintHeaderText)
            canvas.drawLine(curX, top, curX, top + headerHeight, paintBorder)
            curX += w
        }
        canvas.drawLine(right, top, right, top + headerHeight, paintBorder)

        var curY = top + headerHeight
        val rowHeight = 20f
        var zebra = false

        rows.forEach { row ->
            if (row.isNewHall) zebra = !zebra
            val bg = if (zebra) colorZebra else Color.WHITE
            paintBg.color = bg

            canvas.drawRect(left, curY, right, curY + rowHeight, paintBg)
            canvas.drawRect(left, curY, right, curY + rowHeight, paintBorder)

            val cells = arrayOf(
                row.sno,
                row.hallNumber,
                row.floor,
                row.branch,
                row.yearSem,
                row.regFrom,
                row.regTo,
                row.count,
                row.hallTotal
            )

            var cellX = left
            cells.forEachIndexed { colIdx, text ->
                val w = colWidths[colIdx]
                canvas.drawLine(cellX, curY, cellX, curY + rowHeight, paintBorder)

                when (colIdx) {
                    0, 1, 2, 3, 7 -> {
                        paintCellText.textAlign = Paint.Align.CENTER
                        canvas.drawText(text, cellX + w / 2f, curY + 14f, paintCellText)
                    }
                    8 -> {
                        canvas.drawText(text, cellX + w / 2f, curY + 14f, paintBoldText)
                    }
                    else -> {
                        paintCellText.textAlign = Paint.Align.LEFT
                        canvas.drawText(text, cellX + 6f, curY + 14f, paintCellText)
                    }
                }
                cellX += w
            }
            canvas.drawLine(right, curY, right, curY + rowHeight, paintBorder)
            curY += rowHeight
        }

        return curY
    }

    private fun drawLandscapeSignatures(canvas: Canvas, left: Float, right: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNavy
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("EXAM CELL COORDINATOR", left + 20f, y, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("HOD / CSE", (left + right) / 2f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("PRINCIPAL", right - 20f, y, paint)
    }

    private fun drawFooter(canvas: Canvas, width: Float, height: Float, pageNum: Int, totalPages: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextMuted
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Page $pageNum of $totalPages · GRT Institute of Engineering and Technology (Autonomous)", width / 2f, height - 16f, paint)
    }

    // =========================================================================
    // STUDENT DIRECTORY PDF EXPORT (A4 PORTRAIT WITH INSTITUTIONAL BANNER)
    // =========================================================================

    fun generateStudentListPdf(students: List<Student>, title: String, outFile: File): File {
        val sortedStudents = students.sortedWith(StudentOrderComparator)
        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f

        val rowsPerPage = 26
        val chunks = if (sortedStudents.isNotEmpty()) sortedStudents.chunked(rowsPerPage) else listOf(emptyList())
        val totalPages = chunks.size

        val bannerBitmap = runCatching {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeResource(context.resources, R.drawable.grt_banner, opts)
        }.getOrNull()

        try {
            chunks.forEachIndexed { pageIndex, pageStudents ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // 1. Draw top letterhead banner
                val tableTop = drawStudentPortraitHeader(canvas, pageWidth.toFloat(), margin, title, bannerBitmap)

                // 2. Draw student table
                drawStudentTable(canvas, margin, tableTop, pageWidth.toFloat() - margin, pageStudents)

                // 3. Footer
                drawFooter(canvas, pageWidth.toFloat(), pageHeight.toFloat(), pageIndex + 1, totalPages)

                pdfDoc.finishPage(page)
            }

            FileOutputStream(outFile).use { fos ->
                pdfDoc.writeTo(fos)
            }
        } finally {
            runCatching { bannerBitmap?.recycle() }
            pdfDoc.close()
        }
        return outFile
    }

    private fun drawStudentPortraitHeader(
        canvas: Canvas,
        width: Float,
        margin: Float,
        title: String,
        bannerBitmap: Bitmap?
    ): Float {
        var y = margin

        if (bannerBitmap != null && bannerBitmap.width > 0 && bannerBitmap.height > 0) {
            val desiredWidth = width - (margin * 2)
            val desiredHeight = desiredWidth * (bannerBitmap.height.toFloat() / bannerBitmap.width.toFloat())
            val dst = RectF(margin, y, margin + desiredWidth, y + desiredHeight)
            canvas.drawBitmap(bannerBitmap, null, dst, null)
            y += desiredHeight + 10f
        } else {
            val paintHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorNavy
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY", width / 2f, y + 16f, paintHeader)
            paintHeader.textSize = 10f
            paintHeader.color = colorTextMuted
            paintHeader.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("(An Autonomous Institution) · Department of Computer Science and Engineering", width / 2f, y + 32f, paintHeader)
            y += 44f
        }

        // Section / Subtitle banner
        val paintSubtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNavy
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, width / 2f, y + 14f, paintSubtitle)

        return y + 24f
    }

    private fun drawStudentTable(canvas: Canvas, left: Float, top: Float, right: Float, students: List<Student>) {
        val totalWidth = right - left
        // S.No (10%), Register No (28%), Name (38%), Year/Sec (12%), Status (12%)
        val colWeights = floatArrayOf(0.10f, 0.28f, 0.38f, 0.12f, 0.12f)
        val colWidths = colWeights.map { it * totalWidth }
        val headers = arrayOf("S.NO", "REGISTER NO", "STUDENT NAME", "YR / SEC", "STATUS")

        val paintBg = Paint()
        val paintBorder = Paint().apply {
            color = colorBorder
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val paintHeaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNavy
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val paintCellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextDark
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Header Row (Peach / Accent style matching college Excel sheet)
        val headerHeight = 22f
        paintBg.color = colorOrangeAccent
        canvas.drawRect(left, top, right, top + headerHeight, paintBg)
        canvas.drawRect(left, top, right, top + headerHeight, paintBorder)

        var curX = left
        headers.forEachIndexed { i, h ->
            val w = colWidths[i]
            canvas.drawText(h, curX + w / 2f, top + 15f, paintHeaderText)
            canvas.drawLine(curX, top, curX, top + headerHeight, paintBorder)
            curX += w
        }
        canvas.drawLine(right, top, right, top + headerHeight, paintBorder)

        var curY = top + headerHeight
        val rowHeight = 21f

        students.forEachIndexed { index, stu ->
            val bg = if (index % 2 == 1) colorZebra else Color.WHITE
            paintBg.color = bg
            canvas.drawRect(left, curY, right, curY + rowHeight, paintBg)
            canvas.drawRect(left, curY, right, curY + rowHeight, paintBorder)

            val rollNo = stu.extractRollNumber()
            val rollDisplay = if (rollNo in 1..9999) rollNo.toString() else (index + 1).toString()
            val statusText = if (stu.active) "Promoted" else "Inactive"
            val cells = arrayOf(
                rollDisplay,
                stu.registerNumber,
                stu.name,
                "${stu.year.label.take(2)} - ${stu.section.ifBlank { "A" }}",
                statusText
            )

            var cellX = left
            cells.forEachIndexed { colIdx, text ->
                val w = colWidths[colIdx]
                canvas.drawLine(cellX, curY, cellX, curY + rowHeight, paintBorder)

                when (colIdx) {
                    0, 3, 4 -> {
                        paintCellText.textAlign = Paint.Align.CENTER
                        canvas.drawText(text, cellX + w / 2f, curY + 14f, paintCellText)
                    }
                    1 -> {
                        paintCellText.textAlign = Paint.Align.LEFT
                        canvas.drawText(text, cellX + 6f, curY + 14f, paintCellText)
                    }
                    2 -> {
                        paintCellText.textAlign = Paint.Align.LEFT
                        canvas.drawText(text, cellX + 6f, curY + 14f, paintCellText)
                    }
                }
                cellX += w
            }
            canvas.drawLine(right, curY, right, curY + rowHeight, paintBorder)
            curY += rowHeight
        }
    }

    // =========================================================================
    // STUDENT DIRECTORY EXCEL / CSV EXPORT (MATCHING COLLEGE SHEET TEMPLATE)
    // =========================================================================

    fun generateStudentListCsv(students: List<Student>, title: String, outFile: File): File {
        val lines = mutableListOf<String>()
        lines.add("GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY, Tiruttani.")
        lines.add("(An Autonomous Institution)")
        lines.add("NAAC Accredited with \"A++\" Grade & An ISO 9001: 2015 Certified Institution")
        lines.add("Approved by AICTE, New Delhi, Affiliated to Anna University, Chennai.")
        lines.add("GRT Mahalakshmi Nagar, Chennai - Tirupathi Highway, Tiruttani - 631 209, Tiruvallur District.")
        lines.add("Department of Computer Science and Engineering")
        lines.add("")
        lines.add(title)
        lines.add("S.NO,REGISTER NO,STUDENT NAME,YEAR,SECTION,STATUS")

        val sortedStudents = students.sortedWith(StudentOrderComparator)
        sortedStudents.forEachIndexed { index, s ->
            val rollNo = s.extractRollNumber().let { if (it in 1..9999) it else (index + 1) }
            val statusStr = if (s.active) "Promoted" else "Inactive"
            lines.add("$rollNo,\"${s.registerNumber}\",\"${s.name}\",\"${s.year.label}\",\"${s.section}\",\"$statusStr\"")
        }

        outFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        return outFile
    }

    // =========================================================================
    // TEACHER HALL ATTENDANCE SHEET (PDF & CSV)
    // =========================================================================

    fun generateAttendancePdf(
        duty: DutyDay,
        teacherName: String,
        students: List<HallStudentAttendance>,
        outFile: File
    ): File {
        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val left = margin
        val right = pageWidth - margin
        val bannerBitmap = loadBannerBitmap()

        val totalEligible = students.size
        val totalPresent = students.count { it.isPresent }
        val totalAbsent = students.count { !it.isPresent }

        val itemsPerPage = 28
        val pages = students.chunked(itemsPerPage).ifEmpty { listOf(emptyList()) }

        pages.forEachIndexed { pageIndex, pageStudents ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            var curY = margin
            if (bannerBitmap != null && bannerBitmap.width > 0 && bannerBitmap.height > 0) {
                val desiredWidth = right - left
                val desiredHeight = desiredWidth * (bannerBitmap.height.toFloat() / bannerBitmap.width.toFloat())
                val dst = RectF(left, curY, left + desiredWidth, curY + desiredHeight)
                canvas.drawBitmap(bannerBitmap, null, dst, null)
                curY += desiredHeight + 8f
            } else {
                curY += 20f
            }

            // Title
            val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorNavy
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("EXAMINATION HALL ATTENDANCE & INVENTORY SHEET", pageWidth / 2f, curY + 14f, paintTitle)
            curY += 22f

            // Meta banner
            val paintMetaBg = Paint().apply { color = Color.rgb(0xF1, 0xF5, 0xF9) }
            val paintBorder = Paint().apply {
                color = colorBorder
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val paintMetaText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorTextDark
                textSize = 9.5f
            }
            val paintMetaBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorNavy
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            canvas.drawRoundRect(RectF(left, curY, right, curY + 36f), 6f, 6f, paintMetaBg)
            canvas.drawRoundRect(RectF(left, curY, right, curY + 36f), 6f, 6f, paintBorder)

            canvas.drawText("Room No: ", left + 10f, curY + 14f, paintMetaText)
            canvas.drawText("${duty.roomNumber} (Block ${duty.block}, Fl ${duty.floor})", left + 60f, curY + 14f, paintMetaBold)

            canvas.drawText("Date: ", left + 220f, curY + 14f, paintMetaText)
            canvas.drawText(duty.date, left + 250f, curY + 14f, paintMetaBold)

            canvas.drawText("Invigilator: ", left + 360f, curY + 14f, paintMetaText)
            canvas.drawText(teacherName, left + 420f, curY + 14f, paintMetaBold)

            canvas.drawText("Cohorts: ", left + 10f, curY + 28f, paintMetaText)
            canvas.drawText(duty.yearSemester.ifBlank { "All Batches" }, left + 60f, curY + 28f, paintMetaBold)

            canvas.drawText("Eligible: ", left + 220f, curY + 28f, paintMetaText)
            canvas.drawText("$totalEligible", left + 265f, curY + 28f, paintMetaBold)

            canvas.drawText("Present: ", left + 310f, curY + 28f, paintMetaText)
            canvas.drawText("$totalPresent", left + 355f, curY + 28f, paintMetaBold)

            canvas.drawText("Absent: ", left + 390f, curY + 28f, paintMetaText)
            val paintRed = Paint(paintMetaBold).apply { color = Color.rgb(0xDC, 0x26, 0x26) }
            canvas.drawText("$totalAbsent", left + 435f, curY + 28f, paintRed)

            curY += 44f

            // Table Header
            val totalWidth = right - left
            val colWeights = floatArrayOf(0.08f, 0.26f, 0.32f, 0.14f, 0.10f, 0.10f)
            val colWidths = colWeights.map { it * totalWidth }
            val headers = arrayOf("S.NO", "REGISTER NO", "STUDENT NAME", "COHORT", "STATUS", "SIGN")

            val headerHeight = 20f
            val paintHeaderBg = Paint().apply { color = colorNavy }
            val paintHeaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawRect(left, curY, right, curY + headerHeight, paintHeaderBg)

            var hx = left
            headers.forEachIndexed { idx, h ->
                val w = colWidths[idx]
                canvas.drawText(h, hx + w / 2f, curY + 14f, paintHeaderText)
                canvas.drawLine(hx, curY, hx, curY + headerHeight, paintBorder)
                hx += w
            }
            canvas.drawLine(right, curY, right, curY + headerHeight, paintBorder)
            curY += headerHeight

            // Rows
            val rowHeight = 18f
            val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorTextDark
                textSize = 8.5f
            }
            val paintStatusPresent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0x16, 0xA3, 0x4A)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val paintStatusAbsent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0xDC, 0x26, 0x26)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            pageStudents.forEachIndexed { index, stu ->
                val globalIndex = pageIndex * itemsPerPage + index + 1
                val cells = arrayOf(
                    globalIndex.toString(),
                    stu.registerNumber,
                    stu.name,
                    "${stu.yearLabel.take(2)}-${stu.section.ifBlank { "A" }}",
                    if (stu.isPresent) "PRESENT" else "ABSENT",
                    ""
                )

                var cx = left
                cells.forEachIndexed { cIdx, txt ->
                    val w = colWidths[cIdx]
                    canvas.drawLine(cx, curY, cx, curY + rowHeight, paintBorder)
                    when (cIdx) {
                        0 -> {
                            paintCell.textAlign = Paint.Align.CENTER
                            canvas.drawText(txt, cx + w / 2f, curY + 12f, paintCell)
                        }
                        1 -> {
                            paintCell.textAlign = Paint.Align.LEFT
                            canvas.drawText(txt, cx + 4f, curY + 12f, paintCell)
                        }
                        2 -> {
                            paintCell.textAlign = Paint.Align.LEFT
                            canvas.drawText(txt.take(26), cx + 4f, curY + 12f, paintCell)
                        }
                        3 -> {
                            paintCell.textAlign = Paint.Align.CENTER
                            canvas.drawText(txt, cx + w / 2f, curY + 12f, paintCell)
                        }
                        4 -> {
                            val p = if (stu.isPresent) paintStatusPresent else paintStatusAbsent
                            canvas.drawText(txt, cx + w / 2f, curY + 12f, p)
                        }
                        5 -> {}
                    }
                    cx += w
                }
                canvas.drawLine(right, curY, right, curY + rowHeight, paintBorder)
                canvas.drawLine(left, curY + rowHeight, right, curY + rowHeight, paintBorder)
                curY += rowHeight
            }

            // Signatures at bottom of last page
            if (pageIndex == pages.size - 1) {
                val sigY = pageHeight - margin - 25f
                paintMetaText.textAlign = Paint.Align.LEFT
                canvas.drawText("Invigilator Signature: _______________________", left + 10f, sigY, paintMetaText)
                paintMetaText.textAlign = Paint.Align.RIGHT
                canvas.drawText("Chief Superintendent: _______________________", right - 10f, sigY, paintMetaText)
            }

            pdfDoc.finishPage(page)
        }

        FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        return outFile
    }

    fun generateAttendanceCsv(
        duty: DutyDay,
        teacherName: String,
        students: List<HallStudentAttendance>,
        outFile: File
    ): File {
        val lines = mutableListOf<String>()
        lines.add("GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY, Tiruttani.")
        lines.add("EXAMINATION HALL ATTENDANCE REPORT")
        lines.add("Room Number: ${duty.roomNumber} (Block ${duty.block}), Date: ${duty.date}")
        lines.add("Invigilator: $teacherName, Batches: ${duty.yearSemester}")
        lines.add("Total Eligible: ${students.size}, Present: ${students.count { it.isPresent }}, Absent: ${students.count { !it.isPresent }}")
        lines.add("")
        lines.add("S.NO,REGISTER NO,STUDENT NAME,YEAR,SECTION,ATTENDANCE STATUS")
        students.forEachIndexed { i, s ->
            val status = if (s.isPresent) "PRESENT" else "ABSENT"
            lines.add("${i + 1},\"${s.registerNumber}\",\"${s.name}\",\"${s.yearLabel}\",\"${s.section}\",\"$status\"")
        }
        outFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        return outFile
    }

    // =========================================================================
    // EXAM TIMETABLE (PDF & CSV)
    // =========================================================================

    fun generateTimetablePdf(exams: List<Exam>, outFile: File): File {
        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val left = margin
        val right = pageWidth - margin
        val bannerBitmap = loadBannerBitmap()

        val itemsPerPage = 25
        val pages = exams.sortedWith(compareBy({ it.date }, { it.year })).chunked(itemsPerPage).ifEmpty { listOf(emptyList()) }

        pages.forEachIndexed { pageIndex, pageExams ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            var curY = margin
            if (bannerBitmap != null && bannerBitmap.width > 0 && bannerBitmap.height > 0) {
                val desiredWidth = right - left
                val desiredHeight = desiredWidth * (bannerBitmap.height.toFloat() / bannerBitmap.width.toFloat())
                canvas.drawBitmap(bannerBitmap, null, RectF(left, curY, left + desiredWidth, curY + desiredHeight), null)
                curY += desiredHeight + 8f
            } else {
                curY += 20f
            }

            val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorNavy
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("EXAMINATION TIMETABLE & SCHEDULE", pageWidth / 2f, curY + 14f, paintTitle)
            curY += 26f

            val totalWidth = right - left
            val colWeights = floatArrayOf(0.08f, 0.20f, 0.12f, 0.16f, 0.32f, 0.12f)
            val colWidths = colWeights.map { it * totalWidth }
            val headers = arrayOf("S.NO", "DATE & SESS", "YEAR", "SUB CODE", "SUBJECT NAME", "COUNT")

            val headerHeight = 20f
            val paintBorder = Paint().apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 0.8f }
            val paintHeaderBg = Paint().apply { color = colorNavy }
            val paintHeaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
            }
            canvas.drawRect(left, curY, right, curY + headerHeight, paintHeaderBg)
            var hx = left
            headers.forEachIndexed { idx, h ->
                val w = colWidths[idx]
                canvas.drawText(h, hx + w / 2f, curY + 14f, paintHeaderText)
                canvas.drawLine(hx, curY, hx, curY + headerHeight, paintBorder)
                hx += w
            }
            canvas.drawLine(right, curY, right, curY + headerHeight, paintBorder)
            curY += headerHeight

            val rowHeight = 20f
            val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 8.5f }

            pageExams.forEachIndexed { index, e ->
                val globalIndex = pageIndex * itemsPerPage + index + 1
                val cells = arrayOf(
                    globalIndex.toString(),
                    "${e.date} (${e.session})",
                    "${e.year.label} S${e.semester}",
                    e.subjectCode,
                    e.subjectName,
                    e.studentCount.toString()
                )
                var cx = left
                cells.forEachIndexed { cIdx, txt ->
                    val w = colWidths[cIdx]
                    canvas.drawLine(cx, curY, cx, curY + rowHeight, paintBorder)
                    when (cIdx) {
                        0, 1, 2, 5 -> {
                            paintCell.textAlign = Paint.Align.CENTER
                            canvas.drawText(txt, cx + w / 2f, curY + 13f, paintCell)
                        }
                        3, 4 -> {
                            paintCell.textAlign = Paint.Align.LEFT
                            canvas.drawText(txt.take(24), cx + 4f, curY + 13f, paintCell)
                        }
                    }
                    cx += w
                }
                canvas.drawLine(right, curY, right, curY + rowHeight, paintBorder)
                canvas.drawLine(left, curY + rowHeight, right, curY + rowHeight, paintBorder)
                curY += rowHeight
            }
            pdfDoc.finishPage(page)
        }

        FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        return outFile
    }

    fun generateTimetableCsv(exams: List<Exam>, outFile: File): File {
        val lines = mutableListOf<String>()
        lines.add("GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY, Tiruttani.")
        lines.add("EXAMINATION TIMETABLE MASTER SCHEDULE")
        lines.add("")
        lines.add("S.NO,DATE,SESSION,TIME,YEAR,SEMESTER,DEPT,SUBJECT CODE,SUBJECT NAME,STUDENT COUNT")
        exams.sortedWith(compareBy({ it.date }, { it.year })).forEachIndexed { i, e ->
            lines.add("${i + 1},\"${e.date}\",\"${e.session}\",\"${e.timing}\",\"${e.year.label}\",\"${e.semester}\",\"${e.department}\",\"${e.subjectCode}\",\"${e.subjectName}\",\"${e.studentCount}\"")
        }
        outFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        return outFile
    }

    // =========================================================================
    // EXAMINATION HALLS (PDF & CSV)
    // =========================================================================

    fun generateHallsPdf(halls: List<Hall>, outFile: File): File {
        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val left = margin
        val right = pageWidth - margin
        val bannerBitmap = loadBannerBitmap()

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        var curY = margin
        if (bannerBitmap != null && bannerBitmap.width > 0 && bannerBitmap.height > 0) {
            val desiredWidth = right - left
            val desiredHeight = desiredWidth * (bannerBitmap.height.toFloat() / bannerBitmap.width.toFloat())
            canvas.drawBitmap(bannerBitmap, null, RectF(left, curY, left + desiredWidth, curY + desiredHeight), null)
            curY += desiredHeight + 8f
        } else {
            curY += 20f
        }

        val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNavy; textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("EXAMINATION HALLS & BENCH CAPACITIES", pageWidth / 2f, curY + 14f, paintTitle)
        curY += 26f

        val totalWidth = right - left
        val colWeights = floatArrayOf(0.10f, 0.25f, 0.25f, 0.20f, 0.20f)
        val colWidths = colWeights.map { it * totalWidth }
        val headers = arrayOf("S.NO", "ROOM NUMBER", "BLOCK & FLOOR", "CAPACITY", "STATUS")

        val headerHeight = 22f
        val paintBorder = Paint().apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val paintHeaderBg = Paint().apply { color = colorNavy }
        val paintHeaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 9.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawRect(left, curY, right, curY + headerHeight, paintHeaderBg)
        var hx = left
        headers.forEachIndexed { idx, h ->
            val w = colWidths[idx]
            canvas.drawText(h, hx + w / 2f, curY + 15f, paintHeaderText)
            canvas.drawLine(hx, curY, hx, curY + headerHeight, paintBorder)
            hx += w
        }
        canvas.drawLine(right, curY, right, curY + headerHeight, paintBorder)
        curY += headerHeight

        val rowHeight = 22f
        val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 9.5f; textAlign = Paint.Align.CENTER }

        halls.sortedWith(compareBy({ it.block }, { it.roomNumber })).forEachIndexed { index, h ->
            val cells = arrayOf(
                (index + 1).toString(),
                h.roomNumber,
                "Block ${h.block} · Floor ${h.floor}",
                "${h.capacity} Benches",
                if (h.active) "Active" else "Inactive"
            )
            var cx = left
            cells.forEachIndexed { cIdx, txt ->
                val w = colWidths[cIdx]
                canvas.drawLine(cx, curY, cx, curY + rowHeight, paintBorder)
                canvas.drawText(txt, cx + w / 2f, curY + 15f, paintCell)
                cx += w
            }
            canvas.drawLine(right, curY, right, curY + rowHeight, paintBorder)
            canvas.drawLine(left, curY + rowHeight, right, curY + rowHeight, paintBorder)
            curY += rowHeight
        }

        pdfDoc.finishPage(page)
        FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        return outFile
    }

    fun generateHallsCsv(halls: List<Hall>, outFile: File): File {
        val lines = mutableListOf<String>()
        lines.add("GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY, Tiruttani.")
        lines.add("EXAMINATION HALLS MASTER DIRECTORY")
        lines.add("")
        lines.add("S.NO,ROOM NUMBER,BLOCK,FLOOR,CAPACITY,STATUS")
        halls.sortedWith(compareBy({ it.block }, { it.roomNumber })).forEachIndexed { i, h ->
            lines.add("${i + 1},\"${h.roomNumber}\",\"${h.block}\",\"${h.floor}\",\"${h.capacity}\",\"${if (h.active) "Active" else "Inactive"}\"")
        }
        outFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        return outFile
    }

    // =========================================================================
    // TEACHERS & INVIGILATION STAFF (PDF & CSV)
    // =========================================================================

    fun generateTeachersPdf(teachers: List<Teacher>, outFile: File): File {
        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val left = margin
        val right = pageWidth - margin
        val bannerBitmap = loadBannerBitmap()

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        var curY = margin
        if (bannerBitmap != null && bannerBitmap.width > 0 && bannerBitmap.height > 0) {
            val desiredWidth = right - left
            val desiredHeight = desiredWidth * (bannerBitmap.height.toFloat() / bannerBitmap.width.toFloat())
            canvas.drawBitmap(bannerBitmap, null, RectF(left, curY, left + desiredWidth, curY + desiredHeight), null)
            curY += desiredHeight + 8f
        } else {
            curY += 20f
        }

        val paintTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorNavy; textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("FACULTY & INVIGILATION STAFF DIRECTORY", pageWidth / 2f, curY + 14f, paintTitle)
        curY += 26f

        val totalWidth = right - left
        val colWeights = floatArrayOf(0.10f, 0.35f, 0.25f, 0.15f, 0.15f)
        val colWidths = colWeights.map { it * totalWidth }
        val headers = arrayOf("S.NO", "FACULTY NAME", "USERNAME", "ROLE", "STATUS")

        val headerHeight = 22f
        val paintBorder = Paint().apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val paintHeaderBg = Paint().apply { color = colorNavy }
        val paintHeaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 9.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawRect(left, curY, right, curY + headerHeight, paintHeaderBg)
        var hx = left
        headers.forEachIndexed { idx, h ->
            val w = colWidths[idx]
            canvas.drawText(h, hx + w / 2f, curY + 15f, paintHeaderText)
            canvas.drawLine(hx, curY, hx, curY + headerHeight, paintBorder)
            hx += w
        }
        canvas.drawLine(right, curY, right, curY + headerHeight, paintBorder)
        curY += headerHeight

        val rowHeight = 22f
        val paintCell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextDark; textSize = 9.5f }

        teachers.sortedBy { it.name }.forEachIndexed { index, t ->
            val cells = arrayOf(
                (index + 1).toString(),
                t.name,
                "@${t.username}",
                t.role.name.replace("_", " "),
                if (t.active) "Active" else "Inactive"
            )
            var cx = left
            cells.forEachIndexed { cIdx, txt ->
                val w = colWidths[cIdx]
                canvas.drawLine(cx, curY, cx, curY + rowHeight, paintBorder)
                if (cIdx == 1) {
                    paintCell.textAlign = Paint.Align.LEFT
                    canvas.drawText(txt, cx + 6f, curY + 15f, paintCell)
                } else {
                    paintCell.textAlign = Paint.Align.CENTER
                    canvas.drawText(txt, cx + w / 2f, curY + 15f, paintCell)
                }
                cx += w
            }
            canvas.drawLine(right, curY, right, curY + rowHeight, paintBorder)
            canvas.drawLine(left, curY + rowHeight, right, curY + rowHeight, paintBorder)
            curY += rowHeight
        }

        pdfDoc.finishPage(page)
        FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        return outFile
    }

    fun generateTeachersCsv(teachers: List<Teacher>, outFile: File): File {
        val lines = mutableListOf<String>()
        lines.add("GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY, Tiruttani.")
        lines.add("FACULTY AND INVIGILATION STAFF DIRECTORY")
        lines.add("")
        lines.add("S.NO,FACULTY NAME,USERNAME,ROLE,STATUS")
        teachers.sortedBy { it.name }.forEachIndexed { i, t ->
            lines.add("${i + 1},\"${t.name}\",\"${t.username}\",\"${t.role.name}\",\"${if (t.active) "Active" else "Inactive"}\"")
        }
        outFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        return outFile
    }

    // =========================================================================
    // MASTER ARCHIVE (CSV)
    // =========================================================================

    fun generateMasterReportCsv(
        exams: List<Exam>,
        students: List<Student>,
        halls: List<Hall>,
        teachers: List<Teacher>,
        arrangements: List<Arrangement>,
        outFile: File
    ): File {
        val lines = mutableListOf<String>()
        lines.add("GRT INSTITUTE OF ENGINEERING AND TECHNOLOGY, Tiruttani.")
        lines.add("COMPREHENSIVE INSTITUTIONAL EXAM REPOSITORY MASTER EXPORT")
        lines.add("Generated on: ${LocalDate.now()}")
        lines.add("")

        lines.add("=== SECTION 1: EXAMINATION TIMETABLE ===")
        lines.add("Date,Session,Time,Year,Semester,Subject Code,Subject Name,Students")
        exams.forEach { e ->
            lines.add("\"${e.date}\",\"${e.session}\",\"${e.timing}\",\"${e.year.label}\",\"${e.semester}\",\"${e.subjectCode}\",\"${e.subjectName}\",\"${e.studentCount}\"")
        }
        lines.add("")

        lines.add("=== SECTION 2: STUDENTS MASTER DIRECTORY ===")
        lines.add("Register Number,Student Name,Year,Section,Status")
        students.sortedWith(StudentOrderComparator).forEach { s ->
            lines.add("\"${s.registerNumber}\",\"${s.name}\",\"${s.year.label}\",\"${s.section}\",\"${if (s.active) "Active" else "Inactive"}\"")
        }
        lines.add("")

        lines.add("=== SECTION 3: PHYSICAL EXAMINATION HALLS ===")
        lines.add("Room Number,Block,Floor,Capacity,Status")
        halls.forEach { h ->
            lines.add("\"${h.roomNumber}\",\"${h.block}\",\"${h.floor}\",\"${h.capacity}\",\"${if (h.active) "Active" else "Inactive"}\"")
        }
        lines.add("")

        lines.add("=== SECTION 4: FACULTY & INVIGILATION STAFF ===")
        lines.add("Name,Username,Role,Status")
        teachers.forEach { t ->
            lines.add("\"${t.name}\",\"${t.username}\",\"${t.role.name}\",\"${if (t.active) "Active" else "Inactive"}\"")
        }
        lines.add("")

        lines.add("=== SECTION 5: GENERATED SEATING ARRANGEMENTS ===")
        lines.add("Date,Hall,Year,Start Pos,End Pos,Student Count")
        arrangements.forEach { a ->
            a.hallAssignments.forEach { ha ->
                lines.add("\"${a.date}\",\"${ha.hallId}\",\"${ha.year.label}\",\"${ha.startPosition}\",\"${ha.endPosition}\",\"${ha.studentIds.size}\"")
            }
        }

        outFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        return outFile
    }
}
