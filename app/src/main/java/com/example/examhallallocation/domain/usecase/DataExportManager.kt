package com.example.examhallallocation.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.examhallallocation.data.repository.*
import com.example.examhallallocation.domain.model.DutyDay
import com.example.examhallallocation.domain.model.HallStudentAttendance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfGenerator: PdfGenerator,
    private val examRepository: ExamRepository,
    private val studentRepository: StudentRepository,
    private val hallRepository: HallRepository,
    private val teacherRepository: TeacherRepository,
    private val arrangementRepository: ArrangementRepository,
) {

    private fun getTargetFile(fileName: String): File {
        val docDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!docDir.exists()) docDir.mkdirs()
        return File(docDir, fileName)
    }

    private fun getPublicUri(file: File, mimeType: String, publicSubfolder: String): Uri {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // Best effort sync with Android MediaStore
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/$publicSubfolder")
                }
                val resolver = context.contentResolver
                val mediaUri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                if (mediaUri != null) {
                    resolver.openOutputStream(mediaUri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
            }
        }

        return contentUri
    }

    suspend fun exportAttendanceSheet(
        duty: DutyDay,
        teacherName: String,
        students: List<HallStudentAttendance>,
        asPdf: Boolean,
    ): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val safeRoom = duty.roomNumber.replace(" ", "_")
        val ext = if (asPdf) "pdf" else "csv"
        val mime = if (asPdf) "application/pdf" else "text/csv"
        val fileName = "GRT_Attendance_${safeRoom}_${stamp}.$ext"
        val targetFile = getTargetFile(fileName)

        if (asPdf) {
            pdfGenerator.generateAttendancePdf(duty, teacherName, students, targetFile)
        } else {
            pdfGenerator.generateAttendanceCsv(duty, teacherName, students, targetFile)
        }

        val uri = getPublicUri(targetFile, mime, "GRT_Attendance")
        Pair(uri, fileName)
    }

    suspend fun exportTimetable(asPdf: Boolean): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val exams = examRepository.observeAll().first()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val ext = if (asPdf) "pdf" else "csv"
        val mime = if (asPdf) "application/pdf" else "text/csv"
        val fileName = "GRT_Exam_Timetable_${stamp}.$ext"
        val targetFile = getTargetFile(fileName)

        if (asPdf) {
            pdfGenerator.generateTimetablePdf(exams, targetFile)
        } else {
            pdfGenerator.generateTimetableCsv(exams, targetFile)
        }

        val uri = getPublicUri(targetFile, mime, "GRT_Timetable")
        Pair(uri, fileName)
    }

    suspend fun exportHalls(asPdf: Boolean): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val halls = hallRepository.observeAll().first()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val ext = if (asPdf) "pdf" else "csv"
        val mime = if (asPdf) "application/pdf" else "text/csv"
        val fileName = "GRT_Exam_Halls_${stamp}.$ext"
        val targetFile = getTargetFile(fileName)

        if (asPdf) {
            pdfGenerator.generateHallsPdf(halls, targetFile)
        } else {
            pdfGenerator.generateHallsCsv(halls, targetFile)
        }

        val uri = getPublicUri(targetFile, mime, "GRT_Halls")
        Pair(uri, fileName)
    }

    suspend fun exportTeachers(asPdf: Boolean): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val teachers = teacherRepository.observeAll().first()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val ext = if (asPdf) "pdf" else "csv"
        val mime = if (asPdf) "application/pdf" else "text/csv"
        val fileName = "GRT_Faculty_Directory_${stamp}.$ext"
        val targetFile = getTargetFile(fileName)

        if (asPdf) {
            pdfGenerator.generateTeachersPdf(teachers, targetFile)
        } else {
            pdfGenerator.generateTeachersCsv(teachers, targetFile)
        }

        val uri = getPublicUri(targetFile, mime, "GRT_Faculty")
        Pair(uri, fileName)
    }

    suspend fun exportMasterArchive(asPdf: Boolean): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val exams = examRepository.observeAll().first()
        val students = studentRepository.observeAll().first()
        val halls = hallRepository.observeAll().first()
        val teachers = teacherRepository.observeAll().first()
        val arrangements = arrangementRepository.allArrangements()

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val ext = if (asPdf) "pdf" else "csv"
        val mime = if (asPdf) "application/pdf" else "text/csv"
        val fileName = "GRT_Master_Repository_Archive_${stamp}.$ext"
        val targetFile = getTargetFile(fileName)

        if (asPdf) {
            pdfGenerator.generateStudentListPdf(students, "INSTITUTIONAL MASTER REPOSITORY ARCHIVE", targetFile)
        } else {
            pdfGenerator.generateMasterReportCsv(exams, students, halls, teachers, arrangements, targetFile)
        }

        val uri = getPublicUri(targetFile, mime, "GRT_MasterArchive")
        Pair(uri, fileName)
    }
}
