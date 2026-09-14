package com.example.examhallallocation.presentation.arrangement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.data.repository.TeacherRepository
import com.example.examhallallocation.domain.model.Arrangement
import com.example.examhallallocation.domain.model.ArrangementStatus
import com.example.examhallallocation.domain.usecase.GenerateArrangementUseCase
import com.example.examhallallocation.domain.usecase.GenerationResult
import com.example.examhallallocation.domain.usecase.SwapInvigilatorUseCase
import com.example.examhallallocation.domain.usecase.ValidateAndSaveArrangementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewRow(
    val date: String,
    val roomNumber: String,
    val block: String,
    val yearSemester: String,
    val positions: String,
    val studentCount: Int,
    val invigilator: String,
)

data class PreviewUi(
    val rows: List<PreviewRow> = emptyList(),
    val status: ArrangementStatus? = null,
    val dates: List<String> = emptyList(),
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val arrangementRepository: ArrangementRepository,
    private val hallRepository: HallRepository,
    private val teacherRepository: TeacherRepository,
    private val generateArrangementUseCase: GenerateArrangementUseCase,
    private val validateAndSaveArrangementUseCase: ValidateAndSaveArrangementUseCase,
    private val swapInvigilatorUseCase: SwapInvigilatorUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(PreviewUi())
    val ui: StateFlow<PreviewUi> = _ui.asStateFlow()

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val arrangements = arrangementRepository.allArrangements()
            val halls = hallRepository.observeAll().first().associateBy { it.id }
            val teachers = teacherRepository.observeAll().first().associateBy { it.id }

            val rows = arrangements.flatMap { arrangement ->
                val invByHall = arrangement.invigilatorAssignments.associateBy { it.hallId }
                arrangement.hallAssignments.map { ha ->
                    val hall = halls[ha.hallId]
                    val teacher = invByHall[ha.hallId]?.let { teachers[it.teacherId] }
                    PreviewRow(
                        date = arrangement.date,
                        roomNumber = hall?.roomNumber ?: "?",
                        block = hall?.block ?: "?",
                        yearSemester = "${ha.year.label} · S${ha.semester}",
                        positions = "${ha.startPosition}–${ha.endPosition}",
                        studentCount = ha.studentIds.size,
                        invigilator = teacher?.name ?: "Unassigned",
                    )
                }
            }.sortedWith(compareBy({ it.date }, { it.roomNumber }))

            _ui.value = PreviewUi(
                rows = rows,
                status = arrangements.maxByOrNull { it.date }?.status,
                dates = arrangements.map { it.date },
            )
        }
    }

    /** Regenerates the arrangement for one date using the domain UseCase. */
    fun regenerateForDate(date: String) {
        viewModelScope.launch {
            when (val result = generateArrangementUseCase(date)) {
                is GenerationResult.Success -> {
                    _events.value = "Arrangement regenerated for $date"
                    refresh()
                }
                is GenerationResult.Failure -> {
                    _events.value = result.errors.joinToString("\n")
                }
            }
        }
    }

    fun approveAll() {
        viewModelScope.launch {
            arrangementRepository.allArrangements().forEach {
                arrangementRepository.updateStatus(it.id, ArrangementStatus.APPROVED)
            }
            _events.value = "Arrangement approved. You can now export the PDF."
            refresh()
        }
    }

    /** Revalidates and commits any manually edited arrangement via domain UseCase. */
    fun validateAndSave(arrangement: Arrangement) {
        viewModelScope.launch {
            val report = validateAndSaveArrangementUseCase(arrangement)
            if (report.isValid) {
                _events.value = "Assignment updated"
            } else {
                _events.value = report.errors.joinToString("\n")
            }
            refresh()
        }
    }

    /** Reassigns an invigilator for a hall on a given date with instant validation. */
    fun swapInvigilator(date: String, hallId: String, newTeacherId: String) {
        viewModelScope.launch {
            when (val result = swapInvigilatorUseCase(date, hallId, newTeacherId)) {
                is SwapInvigilatorUseCase.Result.Success -> {
                    _events.value = "Invigilator updated successfully"
                    refresh()
                }
                is SwapInvigilatorUseCase.Result.Failure -> {
                    _events.value = result.errors.joinToString("\n")
                }
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }
}
