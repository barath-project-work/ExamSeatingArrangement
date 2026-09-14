package com.example.examhallallocation.presentation.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.ExamRepository
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.data.repository.TeacherRepository
import com.example.examhallallocation.domain.model.ArrangementStatus
import com.example.examhallallocation.domain.usecase.GenerateArrangementUseCase
import com.example.examhallallocation.domain.usecase.GenerationResult
import com.example.examhallallocation.domain.usecase.PdfGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SeatingRow(
    val roomNumber: String,
    val yearSem: String,
    val studentCount: Int,
    val totalCount: Int,
    val invigilator: String,
)

data class SeatingUiState(
    val examName: String = "Exam Hall Allocation",
    val availableDates: List<String> = emptyList(),
    val selectedDate: String = "",
    val rows: List<SeatingRow> = emptyList(),
    val hallsCount: Int = 0,
    val studentsCount: Int = 0,
    val invigilatorsCount: Int = 0,
    val isGenerated: Boolean = false,
    val isGenerating: Boolean = false,
    val statusMessage: String? = null,
)

@HiltViewModel
class PdfExportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val examRepository: ExamRepository,
    private val arrangementRepository: ArrangementRepository,
    private val hallRepository: HallRepository,
    private val teacherRepository: TeacherRepository,
    private val generateArrangementUseCase: GenerateArrangementUseCase,
    private val pdfGenerator: PdfGenerator,
) : ViewModel() {

    sealed interface ExportState {
        data object Idle : ExportState
        data object Exporting : ExportState
        data class Done(val uri: Uri, val displayName: String, val fileSizeFormatted: String) : ExportState
        data class Error(val message: String) : ExportState
    }

    private val _uiState = MutableStateFlow(SeatingUiState())
    val uiState: StateFlow<SeatingUiState> = _uiState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(preferredDate: String? = null) {
        viewModelScope.launch {
            val exams = examRepository.observeAll().first()
            val examName = exams.firstOrNull()?.examName?.ifBlank { "Assessment Test - I" } ?: "Assessment Test - I"
            val examDates = exams.map { it.date }.distinct().sorted()

            val arrangements = arrangementRepository.allArrangements()
            val arrDates = arrangements.map { it.date }
            val allDates = (examDates + arrDates).distinct().sorted()

            val targetDate = preferredDate?.takeIf { it in allDates }
                ?: _uiState.value.selectedDate.takeIf { it in allDates }
                ?: allDates.firstOrNull()
                ?: LocalDate.now().toString()

            _uiState.value = _uiState.value.copy(
                examName = examName,
                availableDates = allDates,
                selectedDate = targetDate,
            )

            loadArrangementForDate(targetDate)
        }
    }

    fun selectDate(date: String) {
        if (date == _uiState.value.selectedDate) return
        _uiState.value = _uiState.value.copy(selectedDate = date)
        viewModelScope.launch {
            loadArrangementForDate(date)
        }
    }

    private suspend fun loadArrangementForDate(date: String) {
        val arrangement = arrangementRepository.findByDate(date)
        if (arrangement == null || arrangement.hallAssignments.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                rows = emptyList(),
                hallsCount = 0,
                studentsCount = 0,
                invigilatorsCount = 0,
                isGenerated = false,
            )
            return
        }

        val halls = hallRepository.observeAll().first().associateBy { it.id }
        val teachers = teacherRepository.observeAll().first().associateBy { it.id }
        val invByHall = arrangement.invigilatorAssignments.associateBy { it.hallId }

        val rows = arrangement.hallAssignments.map { ha ->
            val hall = halls[ha.hallId]
            val teacher = invByHall[ha.hallId]?.let { teachers[it.teacherId] }
            SeatingRow(
                roomNumber = hall?.roomNumber ?: "Room",
                yearSem = "${ha.year.label} · S${ha.semester}",
                studentCount = ha.studentIds.size,
                totalCount = hall?.capacity ?: 30,
                invigilator = teacher?.name ?: "Duty Staff",
            )
        }.sortedBy { it.roomNumber }

        val totalStudents = rows.sumOf { it.studentCount }
        val hallsUsed = rows.map { it.roomNumber }.distinct().size

        _uiState.value = _uiState.value.copy(
            rows = rows,
            hallsCount = hallsUsed,
            studentsCount = totalStudents,
            invigilatorsCount = arrangement.invigilatorAssignments.size,
            isGenerated = true,
        )
    }

    fun generateForCurrentDate() {
        val date = _uiState.value.selectedDate
        if (date.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, statusMessage = null)
            when (val res = generateArrangementUseCase(date)) {
                is GenerationResult.Success -> {
                    val arr = arrangementRepository.findByDate(date)
                    if (arr != null) {
                        arrangementRepository.updateStatus(arr.id, ArrangementStatus.APPROVED)
                    }
                    loadArrangementForDate(date)
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        statusMessage = "Arrangement successfully generated!",
                    )
                }
                is GenerationResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        statusMessage = res.errors.joinToString("\n"),
                    )
                }
            }
        }
    }

    fun exportPdf() {
        if (_exportState.value == ExportState.Exporting) return
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            var arrangements = arrangementRepository.allArrangements()
            if (arrangements.isEmpty() && date.isNotBlank()) {
                generateArrangementUseCase(date)
                arrangements = arrangementRepository.allArrangements()
            }

            if (arrangements.isEmpty()) {
                _exportState.value = ExportState.Error("No seating allocation data found to export.")
                return@launch
            }

            arrangements.forEach {
                if (it.status != ArrangementStatus.APPROVED) {
                    arrangementRepository.updateStatus(it.id, ArrangementStatus.APPROVED)
                }
            }

            _exportState.value = ExportState.Exporting
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val halls = hallRepository.observeAll().first()
                    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    val fileName = "GRT_Exam_Hall_Allocation_$stamp.pdf"

                    val docDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
                    if (!docDir.exists()) docDir.mkdirs()
                    val targetFile = File(docDir, fileName)

                    pdfGenerator.generate(arrangements, halls, targetFile)
                    val sizeKb = (targetFile.length() / 1024).coerceAtLeast(1)
                    val sizeFormatted = "$sizeKb KB"

                    val contentUri = androidx.core.content.FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        targetFile
                    )

                    runCatching {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/GRT_Exam_Arrangement")
                            }
                            val resolver = appContext.contentResolver
                            val mediaUri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                            if (mediaUri != null) {
                                resolver.openOutputStream(mediaUri)?.use { out ->
                                    targetFile.inputStream().use { it.copyTo(out) }
                                }
                            }
                        }
                    }

                    Result.success(Triple(contentUri, fileName, sizeFormatted))
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }

            outcome.fold(
                onSuccess = { (uri, name, size) -> _exportState.value = ExportState.Done(uri, name, size) },
                onFailure = { e ->
                    _exportState.value = ExportState.Error(
                        e.message?.takeIf { it.isNotBlank() } ?: "Could not create the PDF document"
                    )
                },
            )
        }
    }

    fun refresh() = loadData()
}
