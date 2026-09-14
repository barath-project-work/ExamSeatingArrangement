package com.example.examhallallocation.presentation.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.ExamRepository
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.data.repository.StudentRepository
import com.example.examhallallocation.data.repository.SubjectRepository
import com.example.examhallallocation.data.repository.TeacherRepository
import com.example.examhallallocation.data.seed.SeedDataProvider
import com.example.examhallallocation.domain.model.Exam
import com.example.examhallallocation.domain.usecase.SmartDataExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminDashboardUi(
    val totalStudents: Int = 0,
    val activeTeachers: Int = 0,
    val activeHalls: Int = 0,
    val totalSubjects: Int = 0,
    val isTimetableActive: Boolean = false,
    val examName: String? = null,
    val examDatesSummary: String? = null,
    val totalExams: Int = 0,
    val arrangementDates: List<String> = emptyList(),
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    teacherRepository: TeacherRepository,
    hallRepository: HallRepository,
    private val subjectRepository: SubjectRepository,
    private val examRepository: ExamRepository,
    private val arrangementRepository: ArrangementRepository,
    private val authRepository: com.example.examhallallocation.data.repository.AuthRepository,
    private val databaseSeeder: com.example.examhallallocation.data.seed.DatabaseSeeder,
    private val dataExportManager: com.example.examhallallocation.domain.usecase.DataExportManager,
) : ViewModel() {

    private val _isSyncing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    init {
        syncWithCloud()
    }

    fun syncWithCloud(onDone: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = databaseSeeder.hydrateFromCloud()
            _isSyncing.value = false
            onDone?.invoke(success)
        }
    }

    private data class CoreCounts(
        val totalStudents: Int,
        val activeTeachers: Int,
        val activeHalls: Int,
        val totalSubjects: Int,
    )

    private val coreCountsFlow = combine(
        studentRepository.observeAll(),
        teacherRepository.observeAll(),
        hallRepository.observeAll(),
        subjectRepository.observeAll(),
    ) { students, teachers, halls, subjects ->
        CoreCounts(
            totalStudents = students.count { it.active },
            activeTeachers = teachers.count { it.active && it.role != com.example.examhallallocation.domain.model.UserRole.HOD },
            activeHalls = halls.count { it.active },
            totalSubjects = subjects.count { it.active },
        )
    }

    val ui: StateFlow<AdminDashboardUi> = combine(
        coreCountsFlow,
        examRepository.observeAll(),
        arrangementRepository.observeAllDates(),
    ) { counts, exams, arrangementDates ->
        val activeDates = exams.map { it.date }.distinct().sorted()
        val summary = if (activeDates.isNotEmpty()) {
            val dateSpan = if (activeDates.size > 1) "${activeDates.first()} to ${activeDates.last()}" else activeDates.first()
            "${activeDates.size} Exam Days ($dateSpan) · ${exams.size} Scheduled Exams"
        } else null

        AdminDashboardUi(
            totalStudents = counts.totalStudents,
            activeTeachers = counts.activeTeachers,
            activeHalls = counts.activeHalls,
            totalSubjects = counts.totalSubjects,
            isTimetableActive = exams.isNotEmpty(),
            examName = exams.firstOrNull()?.examName?.takeIf { it.isNotBlank() },
            examDatesSummary = summary,
            totalExams = exams.size,
            arrangementDates = arrangementDates,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminDashboardUi())

    fun importTimetableFile(bytes: ByteArray, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = SmartDataExtractor.parseExams(bytes)
            if (result.exams.isEmpty()) {
                val error = result.errors.firstOrNull() ?: "No valid exams found in spreadsheet"
                onDone(false, error)
                return@launch
            }
            examRepository.clearAll()
            examRepository.addAll(result.exams)
            onDone(true, "Successfully generated timetable with ${result.exams.size} scheduled exams!")
        }
    }

    fun importTimetableCsv(content: String, onDone: (Boolean, String) -> Unit) {
        importTimetableFile(content.toByteArray(Charsets.UTF_8), onDone)
    }

    fun loadOfficialAssessmentTest1(onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            examRepository.clearAll()
            examRepository.addAll(SeedDataProvider.exams())
            onDone(true, "Assessment Test - I timetable loaded. Dates successfully generated!")
        }
    }

    fun clearActiveTimetable(onDone: () -> Unit) {
        viewModelScope.launch {
            examRepository.clearAll()
            arrangementRepository.clearAll()
            onDone()
        }
    }

    fun exportTimetable(
        asPdf: Boolean,
        onDone: (Uri, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val (uri, fileName) = dataExportManager.exportTimetable(asPdf)
                onDone(uri, fileName)
            } catch (t: Throwable) {
                onError(t.localizedMessage ?: "Failed to generate timetable export")
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    fun exportMasterArchive(
        asPdf: Boolean,
        onDone: (Uri, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val (uri, fileName) = dataExportManager.exportMasterArchive(asPdf)
                onDone(uri, fileName)
            } catch (t: Throwable) {
                onError(t.localizedMessage ?: "Failed to generate master export")
            }
        }
    }

    fun wipeAllMockData(onDone: () -> Unit) {
        viewModelScope.launch {
            studentRepository.clearAll()
            examRepository.clearAll()
            arrangementRepository.clearAll()
            onDone()
        }
    }
}
