package com.example.examhallallocation.presentation.teacher

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.AuthRepository
import com.example.examhallallocation.data.repository.ExamRepository
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.data.repository.StudentRepository
import com.example.examhallallocation.domain.model.DutyDay
import com.example.examhallallocation.domain.model.HallStudentAttendance
import com.example.examhallallocation.domain.model.Student
import com.example.examhallallocation.domain.model.UserSession
import com.example.examhallallocation.domain.usecase.DataExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeacherDashboardUi(
    val session: UserSession? = null,
    val duties: List<DutyDay> = emptyList(),
    val currentDuty: DutyDay? = null,
    val hallStudents: List<HallStudentAttendance> = emptyList(),
    val totalEligible: Int = 0,
    val totalPresent: Int = 0,
    val totalAbsent: Int = 0,
    val totalExamDays: Int = 0,
    val isExporting: Boolean = false,
)

@HiltViewModel
class TeacherDashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val arrangementRepository: ArrangementRepository,
    private val hallRepository: HallRepository,
    private val examRepository: ExamRepository,
    private val studentRepository: StudentRepository,
    private val dataExportManager: DataExportManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(TeacherDashboardUi())
    val ui: StateFlow<TeacherDashboardUi> = _ui.asStateFlow()

    // Key: studentId -> isPresent (default true)
    private val attendanceOverrides = mutableMapOf<String, Boolean>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val session = authRepository.currentUser()
            if (session == null) {
                _ui.value = TeacherDashboardUi()
                return@launch
            }

            val arrangements = arrangementRepository.allArrangements()
            val halls = hallRepository.observeAll().first().associateBy { it.id }
            val exams = examRepository.observeAll().first()

            val duties = arrangements.mapNotNull { arrangement ->
                val inv = arrangement.invigilatorAssignments.firstOrNull { it.teacherId == session.teacherId }
                    ?: return@mapNotNull null
                val hall = halls[inv.hallId]
                val blocks = arrangement.hallAssignments.filter { it.hallId == inv.hallId }
                val subjects = exams.filter { it.date == arrangement.date }
                    .joinToString(", ") { it.subjectCode }
                    .ifBlank { arrangement.examName }

                val yearsLabel = blocks.map { it.year.label }.distinct().joinToString(" & ")

                DutyDay(
                    date = arrangement.date,
                    roomNumber = hall?.roomNumber ?: "?",
                    block = hall?.block ?: "?",
                    floor = hall?.floor ?: 0,
                    yearSemester = yearsLabel.ifBlank { "All Batches" },
                    subjects = subjects,
                )
            }

            val currentDuty = duties.firstOrNull()

            val hallStudents = if (currentDuty != null) {
                val arrangement = arrangements.firstOrNull { it.date == currentDuty.date }
                val inv = arrangement?.invigilatorAssignments?.firstOrNull { it.teacherId == session.teacherId }
                val blocks = arrangement?.hallAssignments
                    ?.filter { it.hallId == inv?.hallId }
                    .orEmpty()

                val rawStudents = studentRepository.studentsByIds(blocks.flatMap { it.studentIds })
                val studentMap = rawStudents.associateBy { it.id }

                val orderedStudents = if (blocks.size == 2) {
                    val listA = blocks[0].studentIds.mapNotNull { studentMap[it] }
                    val listB = blocks[1].studentIds.mapNotNull { studentMap[it] }
                    val interleaved = mutableListOf<Student>()
                    val maxSize = maxOf(listA.size, listB.size)
                    for (i in 0 until maxSize) {
                        if (i < listA.size) interleaved.add(listA[i])
                        if (i < listB.size) interleaved.add(listB[i])
                    }
                    interleaved
                } else {
                    blocks.flatMap { b -> b.studentIds.mapNotNull { studentMap[it] } }
                }

                orderedStudents.mapIndexed { index, s ->
                    val isPresent = attendanceOverrides[s.id] ?: true
                    HallStudentAttendance(
                        id = s.id,
                        registerNumber = s.registerNumber,
                        name = s.name,
                        yearLabel = "Bench ${index + 1} · ${s.year.label}",
                        section = s.section.ifBlank { "A" },
                        isPresent = isPresent,
                    )
                }
            } else {
                emptyList()
            }

            val eligible = hallStudents.size
            val present = hallStudents.count { it.isPresent }
            val absent = hallStudents.count { !it.isPresent }

            val totalDays = runCatching {
                val range = examRepository.examDateRange()
                if (range != null) {
                    val count = exams.map { it.date }.distinct().size
                    count.coerceAtLeast(1)
                } else 0
            }.getOrDefault(0)

            _ui.value = TeacherDashboardUi(
                session = session,
                duties = duties,
                currentDuty = currentDuty,
                hallStudents = hallStudents,
                totalEligible = eligible,
                totalPresent = present,
                totalAbsent = absent,
                totalExamDays = totalDays,
            )
        }
    }

    fun toggleAttendance(studentId: String, isPresent: Boolean) {
        attendanceOverrides[studentId] = isPresent
        val currentStudents = _ui.value.hallStudents.map {
            if (it.id == studentId) it.copy(isPresent = isPresent) else it
        }
        val eligible = currentStudents.size
        val present = currentStudents.count { it.isPresent }
        val absent = currentStudents.count { !it.isPresent }

        _ui.value = _ui.value.copy(
            hallStudents = currentStudents,
            totalEligible = eligible,
            totalPresent = present,
            totalAbsent = absent,
        )
    }

    fun exportAttendance(
        asPdf: Boolean,
        onDone: (Uri, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val duty = _ui.value.currentDuty
        val session = _ui.value.session
        val students = _ui.value.hallStudents

        if (duty == null || session == null || students.isEmpty()) {
            onError("No active hall attendance roster to export.")
            return
        }

        viewModelScope.launch {
            _ui.value = _ui.value.copy(isExporting = true)
            try {
                val (uri, fileName) = dataExportManager.exportAttendanceSheet(
                    duty = duty,
                    teacherName = session.name,
                    students = students,
                    asPdf = asPdf,
                )
                _ui.value = _ui.value.copy(isExporting = false)
                onDone(uri, fileName)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(isExporting = false)
                onError(t.localizedMessage ?: "Export failed")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }
}
