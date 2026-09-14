package com.example.examhallallocation.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.ExamRepository
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.data.repository.StudentRepository
import com.example.examhallallocation.data.repository.TeacherRepository
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
    val latestExamName: String? = null,
    val arrangementDates: List<String> = emptyList(),
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    teacherRepository: TeacherRepository,
    hallRepository: HallRepository,
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

    val ui: StateFlow<AdminDashboardUi> = combine(
        studentRepository.observeAll(),
        teacherRepository.observeAll(),
        hallRepository.observeAll(),
        examRepository.observeAll(),
        arrangementRepository.observeAllDates(),
    ) { students, teachers, halls, exams, arrangementDates ->
        AdminDashboardUi(
            totalStudents = students.count { it.active },
            activeTeachers = teachers.count { it.active && it.role != com.example.examhallallocation.domain.model.UserRole.HOD },
            activeHalls = halls.count { it.active },
            latestExamName = exams.minByOrNull { it.date }?.examName,
            arrangementDates = arrangementDates,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminDashboardUi())

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    fun exportMasterArchive(
        asPdf: Boolean,
        onDone: (android.net.Uri, String) -> Unit,
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
