package com.example.examhallallocation.domain.usecase

import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.domain.model.Arrangement
import com.example.examhallallocation.domain.model.InvigilatorAssignment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain UseCase providing granular manual override capability:
 * Reassigns an invigilator for a specific examination hall on a given date,
 * ensuring the change is instantly audited against institutional policies.
 */
@Singleton
class SwapInvigilatorUseCase @Inject constructor(
    private val arrangementRepository: ArrangementRepository,
    private val validateAndSaveArrangementUseCase: ValidateAndSaveArrangementUseCase,
) {

    sealed interface Result {
        data class Success(val updatedArrangement: Arrangement) : Result
        data class Failure(val errors: List<String>) : Result
    }

    suspend operator fun invoke(
        date: String,
        hallId: String,
        newTeacherId: String,
    ): Result {
        val arrangement = arrangementRepository.findByDate(date)
            ?: return Result.Failure(listOf("No arrangement found for date $date."))

        val existingInv = arrangement.invigilatorAssignments.firstOrNull { it.hallId == hallId }
            ?: return Result.Failure(listOf("No invigilator assigned to hall $hallId on $date."))

        if (existingInv.teacherId == newTeacherId) {
            return Result.Success(arrangement)
        }

        val updatedAssignments = arrangement.invigilatorAssignments.map { inv ->
            if (inv.hallId == hallId) inv.copy(teacherId = newTeacherId) else inv
        }

        val modifiedArrangement = arrangement.copy(invigilatorAssignments = updatedAssignments)

        val report = validateAndSaveArrangementUseCase(modifiedArrangement)
        return if (report.isValid) {
            Result.Success(modifiedArrangement)
        } else {
            Result.Failure(report.errors)
        }
    }
}
