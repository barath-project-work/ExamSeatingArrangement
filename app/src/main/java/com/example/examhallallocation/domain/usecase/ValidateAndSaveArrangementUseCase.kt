package com.example.examhallallocation.domain.usecase

import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.data.repository.StudentRepository
import com.example.examhallallocation.data.repository.TeacherRepository
import com.example.examhallallocation.domain.model.Arrangement
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain UseCase that validates any arrangement (generated or manually modified)
 * before committing it to storage, ensuring institutional rules can never be bypassed.
 */
@Singleton
class ValidateAndSaveArrangementUseCase @Inject constructor(
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository,
    private val hallRepository: HallRepository,
    private val arrangementRepository: ArrangementRepository,
) {

    suspend operator fun invoke(arrangement: Arrangement): ArrangementValidator.Report {
        val students = studentRepository.observeAll().first()
        val teachers = teacherRepository.observeAll().first()
        val halls = hallRepository.observeAll().first()
        val allArrangements = arrangementRepository.allArrangements()
            .filter { it.date != arrangement.date } + arrangement

        val report = ArrangementValidator.validate(
            arrangement = arrangement,
            students = students,
            halls = halls,
            teachers = teachers,
            allArrangements = allArrangements,
        )

        if (report.isValid) {
            arrangementRepository.replaceForDate(arrangement.date, arrangement)
        }

        return report
    }
}
