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
import com.example.examhallallocation.domain.model.Arrangement
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
    val sno: String,
    val roomNumber: String,
    val floor: String,
    val dept: String,
    val yearSem: String,
    val regFrom: String,
    val regTo: String,
    val studentCount: Int,
    val totalCount: String,
    val isNewHall: Boolean,
)

data class DutyRow(
    val sno: Int,
    val teacherName: String,
    val teacherRole: String,
    val hallNumber: String,
    val floor: String,
    val block: String,
    val totalDutiesInExam: Int,
    val session: String,
)

data class SeatingUiState(
    val examName: String = "Exam Hall Allocation",
    val availableDates: List<String> = emptyList(),
    val selectedDate: String = "",
    val rows: List<SeatingRow> = emptyList(),
    val dutyRows: List<DutyRow> = emptyList(),
    val facultyDutySummaries: List<com.example.examhallallocation.domain.model.TeacherDutySummary> = emptyList(),
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
    private val getFacultyDutySummaryUseCase: com.example.examhallallocation.domain.usecase.GetFacultyDutySummaryUseCase,
    private val pdfGenerator: PdfGenerator,
) : ViewModel() {

    sealed interface ExportState {
        data object Idle : ExportState
        data object Exporting : ExportState
        data class Done(val uri: Uri, val displayName: String, val fileSizeFormatted: String, val isPdf: Boolean = true) : ExportState
        data class Error(val message: String) : ExportState
    }

    private val _uiState = MutableStateFlow(SeatingUiState())
    val uiState: StateFlow<SeatingUiState> = _uiState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    init {
        loadData()
    }

    private fun isCorruptedArrangement(arrangement: Arrangement): Boolean {
        val byHall = arrangement.hallAssignments.groupBy { it.hallId }
        val capacityViolated = byHall.any { (_, blocks) ->
            blocks.sumOf { it.studentIds.size } > 30 || blocks.map { it.year }.distinct().size < blocks.size
        }
        val missingInvigilators = arrangement.invigilatorAssignments.isEmpty() && byHall.isNotEmpty()
        return capacityViolated || missingInvigilators
    }

    fun loadData(preferredDate: String? = null) {
        viewModelScope.launch {
            val exams = examRepository.observeAll().first()
            val examName = exams.firstOrNull()?.examName?.ifBlank { "Assessment Test - I" } ?: "Assessment Test - I"
            val examDates = exams.map { it.date }.distinct().sorted()

            val arrangements = arrangementRepository.allArrangements()
            if (arrangements.any { isCorruptedArrangement(it) }) {
                arrangementRepository.clearAll()
            }

            val arrDates = arrangementRepository.allArrangements().map { it.date }
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
        var arrangement = arrangementRepository.findByDate(date)
        if (arrangement != null && isCorruptedArrangement(arrangement)) {
            generateArrangementUseCase(date)
            arrangement = arrangementRepository.findByDate(date)
        }
        if (arrangement == null || arrangement.hallAssignments.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                rows = emptyList(),
                dutyRows = emptyList(),
                hallsCount = 0,
                studentsCount = 0,
                invigilatorsCount = 0,
                isGenerated = false,
            )
            return
        }

        val halls = hallRepository.observeAll().first().associateBy { it.id }
        val teachers = teacherRepository.observeAll().first().associateBy { it.id }
        val examsOnDate = examRepository.findByDate(date)
        val sessionTiming = examsOnDate.firstOrNull()?.let { "${it.timing} (${it.session})" }
            ?: "8:40 a.m. TO 10:10 a.m. (FN)"

        val byHall = linkedMapOf<String, MutableList<com.example.examhallallocation.domain.model.HallAssignment>>()
        arrangement.hallAssignments.forEach { ha ->
            byHall.getOrPut(ha.hallId) { mutableListOf() }.add(ha)
        }

        val rows = mutableListOf<SeatingRow>()
        var sno = 1
        byHall.forEach { (hallId, blocks) ->
            val hall = halls[hallId]
            val hallTotal = blocks.sumOf { it.studentIds.size }
            blocks.forEachIndexed { blockIndex, block ->
                val regNumbers = block.studentIds.map { it.removePrefix("stu_") }
                val fromRoll = regNumbers.firstOrNull()?.takeLast(3)?.toIntOrNull() ?: block.startPosition
                val toRoll = regNumbers.lastOrNull()?.takeLast(3)?.toIntOrNull() ?: block.endPosition
                val regFromStr = if (regNumbers.isNotEmpty()) "${regNumbers.first()} ($fromRoll)" else "-"
                val regToStr = if (regNumbers.isNotEmpty()) "${regNumbers.last()} ($toRoll)" else "-"

                rows.add(
                    SeatingRow(
                        sno = if (blockIndex == 0) sno.toString() else "",
                        roomNumber = if (blockIndex == 0) (hall?.roomNumber ?: hallId) else "",
                        floor = if (blockIndex == 0) (hall?.floor?.toString().orEmpty()) else "",
                        dept = if (blockIndex == 0) "CSE" else "",
                        yearSem = "${block.year.label} / Sem ${block.semester}",
                        regFrom = regFromStr,
                        regTo = regToStr,
                        studentCount = block.studentIds.size,
                        totalCount = if (blockIndex == 0) hallTotal.toString() else "",
                        isNewHall = blockIndex == 0,
                    )
                )
            }
            sno++
        }

        val totalStudents = rows.sumOf { it.studentCount }
        val hallsUsed = byHall.keys.size

        val facultySummaries = runCatching {
            getFacultyDutySummaryUseCase(_uiState.value.examName)
        }.getOrDefault(emptyList())

        val dutyCountByTeacherId = facultySummaries.associate { it.teacherId to it.totalDuties }

        // Build Duty Rows sorted neatly by Hall Room Number
        val dutyRows = arrangement.invigilatorAssignments
            .sortedWith(compareBy(
                { halls[it.hallId]?.block ?: "" },
                { halls[it.hallId]?.roomNumber ?: it.hallId }
            ))
            .mapIndexed { index, assign ->
                val hall = halls[assign.hallId]
                val teacher = teachers[assign.teacherId]
                val teacherName = teacher?.name ?: "Faculty Invigilator"
                val roleLabel = teacher?.role?.label ?: "Teacher"
                val hallNo = hall?.roomNumber ?: assign.hallId
                val floorStr = hall?.floor?.let { "Floor $it" } ?: "-"
                val blockStr = hall?.block?.let { "Block $it" } ?: "-"
                val totalDutiesInExam = dutyCountByTeacherId[assign.teacherId] ?: 1

                DutyRow(
                    sno = index + 1,
                    teacherName = teacherName,
                    teacherRole = roleLabel,
                    hallNumber = hallNo,
                    floor = floorStr,
                    block = blockStr,
                    totalDutiesInExam = totalDutiesInExam,
                    session = sessionTiming,
                )
            }

        _uiState.value = _uiState.value.copy(
            rows = rows,
            dutyRows = dutyRows,
            facultyDutySummaries = facultySummaries,
            hallsCount = hallsUsed,
            studentsCount = totalStudents,
            invigilatorsCount = dutyRows.size,
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

    fun exportPdf() = exportSeating(asPdf = true)

    fun exportCsv() = exportSeating(asPdf = false)

    fun exportSeating(asPdf: Boolean) {
        if (_exportState.value == ExportState.Exporting) return
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            if (date.isBlank()) {
                _exportState.value = ExportState.Error("Please select an exam date to export.")
                return@launch
            }

            var arrangement = arrangementRepository.findByDate(date)
            if (arrangement == null || arrangement.hallAssignments.isEmpty() || isCorruptedArrangement(arrangement)) {
                when (val res = generateArrangementUseCase(date)) {
                    is GenerationResult.Success -> {
                        arrangement = arrangementRepository.findByDate(date)
                    }
                    is GenerationResult.Failure -> {
                        _exportState.value = ExportState.Error(res.errors.joinToString("\n"))
                        return@launch
                    }
                }
            }

            if (arrangement == null || arrangement.hallAssignments.isEmpty()) {
                _exportState.value = ExportState.Error("No seating allocation data found to export for $date.")
                return@launch
            }

            if (arrangement.status != ArrangementStatus.APPROVED) {
                arrangementRepository.updateStatus(arrangement.id, ArrangementStatus.APPROVED)
            }

            _exportState.value = ExportState.Exporting
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val halls = hallRepository.observeAll().first()
                    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    val ext = if (asPdf) "pdf" else "csv"
                    val mimeType = if (asPdf) "application/pdf" else "text/csv"
                    val fileName = "GRT_Exam_Hall_Allocation_${date}_$stamp.$ext"

                    val docDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
                    if (!docDir.exists()) docDir.mkdirs()
                    val targetFile = File(docDir, fileName)

                    if (asPdf) {
                        pdfGenerator.generate(listOf(arrangement), halls, targetFile)
                    } else {
                        pdfGenerator.generateSeatingCsv(listOf(arrangement), halls, targetFile)
                    }
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
                                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
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
                onSuccess = { (uri, name, size) -> _exportState.value = ExportState.Done(uri, name, size, isPdf = asPdf) },
                onFailure = { e ->
                    _exportState.value = ExportState.Error(
                        e.message?.takeIf { it.isNotBlank() } ?: "Could not export seating allocation."
                    )
                },
            )
        }
    }

    fun refresh() = loadData()
}
