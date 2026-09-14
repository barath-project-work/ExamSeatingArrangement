package com.example.examhallallocation.presentation.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.ExamRepository
import com.example.examhallallocation.domain.model.ExamPhase
import com.example.examhallallocation.domain.model.StudentYear
import com.example.examhallallocation.domain.usecase.GenerateArrangementUseCase
import com.example.examhallallocation.domain.usecase.GenerationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class GenerateUi(
    val examName: String = "",
    val date: String = "",
    val phase: ExamPhase = ExamPhase.PHASE_1,
    val yearsLabel: String = "",
    val running: Boolean = false,
    val errors: List<String> = emptyList(),
    val summary: Summary? = null,
    val generated: Boolean = false,
) {
    data class Summary(val hallsUsed: Int, val studentsSeated: Int, val invigilators: Int)
}

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val examRepository: ExamRepository,
    private val arrangementRepository: ArrangementRepository,
    private val generateArrangementUseCase: GenerateArrangementUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(GenerateUi())
    val ui: StateFlow<GenerateUi> = _ui.asStateFlow()

    init {
        loadNextDate()
    }

    fun loadNextDate() {
        viewModelScope.launch {
            val range = examRepository.examDateRange()
            val all = arrangementRepository.allArrangements()
            val generatedDates = all.map { it.date }.toSet()
            val nextDate = range?.let { (first, last) ->
                generateSequence(LocalDate.parse(first)) { it.plusDays(1) }
                    .takeWhile { it <= LocalDate.parse(last) }
                    .map { it.toString() }
                    .firstOrNull { it !in generatedDates }
            } ?: range?.first ?: ""

            val exams = if (nextDate.isBlank()) emptyList() else examRepository.findByDate(nextDate)
            val years = exams.map { it.year }.distinct()
            val phase = phaseFor(years)
            _ui.value = _ui.value.copy(
                date = nextDate,
                examName = exams.firstOrNull()?.examName ?: "",
                phase = phase,
                yearsLabel = when (phase) {
                    ExamPhase.PHASE_1 -> "2nd + 3rd + 4th Year"
                    ExamPhase.PHASE_2 -> "2nd + 3rd Year"
                    ExamPhase.PHASE_3 -> "3rd Year only"
                },
                generated = false,
                summary = null,
                errors = emptyList(),
            )
        }
    }

    fun generate() {
        val current = _ui.value
        if (current.date.isBlank() || current.running) return
        _ui.value = current.copy(running = true, errors = emptyList(), summary = null)
        viewModelScope.launch {
            val result = generateArrangementUseCase(
                date = current.date,
                customExamName = current.examName,
                explicitPhase = current.phase,
            )

            when (result) {
                is GenerationResult.Success -> {
                    _ui.value = _ui.value.copy(
                        running = false,
                        generated = true,
                        summary = GenerateUi.Summary(
                            hallsUsed = result.arrangement.hallAssignments.map { it.hallId }.distinct().size,
                            studentsSeated = result.arrangement.hallAssignments.sumOf { it.studentIds.size },
                            invigilators = result.arrangement.invigilatorAssignments.size,
                        ),
                    )
                }
                is GenerationResult.Failure -> {
                    _ui.value = _ui.value.copy(running = false, errors = result.errors)
                }
            }
        }
    }

    private fun phaseFor(years: List<StudentYear>): ExamPhase = when {
        years.contains(StudentYear.YEAR_4) -> ExamPhase.PHASE_1
        years.contains(StudentYear.YEAR_2) -> ExamPhase.PHASE_2
        years.contains(StudentYear.YEAR_3) -> ExamPhase.PHASE_3
        else -> ExamPhase.PHASE_1
    }
}
