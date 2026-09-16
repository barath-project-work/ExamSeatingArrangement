package com.example.examhallallocation.domain.usecase

import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.ExamRepository
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.data.repository.StudentRepository
import com.example.examhallallocation.data.repository.TeacherRepository
import com.example.examhallallocation.domain.model.Arrangement
import com.example.examhallallocation.domain.model.ExamPhase
import com.example.examhallallocation.domain.model.StudentYear
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain UseCase that coordinates the generation and persistence of an exam seating
 * arrangement for a specific date, adhering strictly to institutional rules.
 */
@Singleton
class GenerateArrangementUseCase @Inject constructor(
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository,
    private val hallRepository: HallRepository,
    private val examRepository: ExamRepository,
    private val arrangementRepository: ArrangementRepository,
    private val generator: ArrangementGenerator,
) {

    suspend operator fun invoke(
        date: String,
        customExamName: String? = null,
        explicitPhase: ExamPhase? = null,
    ): GenerationResult {
        if (date.isBlank()) {
            return GenerationResult.Failure(listOf("Exam date cannot be empty."))
        }

        val students = studentRepository.observeAll().first()
        val teachers = teacherRepository.observeAll().first()
        val halls = hallRepository.observeAll().first()
        val exams = examRepository.findByDate(date)
        val existing = arrangementRepository.allArrangements().filter { it.date != date }

        val years = exams.map { it.year }.distinct()
        val phase = explicitPhase ?: phaseFor(years)

        val semesters = exams.associate { it.year to it.semester }
            .ifEmpty {
                mapOf(
                    StudentYear.YEAR_1 to 2,
                    StudentYear.YEAR_2 to 4,
                    StudentYear.YEAR_3 to 6,
                    StudentYear.YEAR_4 to 8,
                )
            }

        val examName = customExamName?.takeIf { it.isNotBlank() }
            ?: exams.firstOrNull()?.examName
            ?: "Examination"

        android.util.Log.i("ArrangementGen", "Invoke date=$date, students=${students.size}, teachers=${teachers.size}, halls=${halls.size}, exams=${exams.size}, years=$years, phase=$phase")

        val result = generator.generate(
            examName = examName,
            date = date,
            phase = phase,
            students = students,
            halls = halls,
            teachers = teachers,
            semesters = semesters,
            existingArrangements = existing,
        )

        android.util.Log.i("ArrangementGen", "Generation result: $result")

        if (result is GenerationResult.Success) {
            arrangementRepository.replaceForDate(date, result.arrangement)
        }

        return result
    }

    private fun phaseFor(years: List<StudentYear>): ExamPhase = when {
        years.contains(StudentYear.YEAR_4) -> ExamPhase.PHASE_1
        years.contains(StudentYear.YEAR_2) -> ExamPhase.PHASE_2
        years.contains(StudentYear.YEAR_3) -> ExamPhase.PHASE_3
        else -> ExamPhase.PHASE_1
    }
}
