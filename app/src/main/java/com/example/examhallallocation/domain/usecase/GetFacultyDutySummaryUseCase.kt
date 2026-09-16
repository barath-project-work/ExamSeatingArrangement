package com.example.examhallallocation.domain.usecase

import com.example.examhallallocation.data.repository.ArrangementRepository
import com.example.examhallallocation.data.repository.ExamRepository
import com.example.examhallallocation.data.repository.HallRepository
import com.example.examhallallocation.data.repository.TeacherRepository
import com.example.examhallallocation.domain.model.TeacherDutyAssignment
import com.example.examhallallocation.domain.model.TeacherDutySummary
import com.example.examhallallocation.domain.model.UserRole
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain UseCase that calculates the total invigilation duties assigned to each teacher
 * for a specific examination (e.g. Assessment Test - I), along with hall room numbers,
 * dates, floors, blocks, and session timings.
 */
@Singleton
class GetFacultyDutySummaryUseCase @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val arrangementRepository: ArrangementRepository,
    private val hallRepository: HallRepository,
    private val examRepository: ExamRepository,
    private val generateArrangementUseCase: GenerateArrangementUseCase,
) {

    suspend operator fun invoke(examNameFilter: String? = null): List<TeacherDutySummary> {
        val teachers = teacherRepository.observeAll().first()
        val halls = hallRepository.observeAll().first().associateBy { it.id }
        val exams = examRepository.observeAll().first()

        val defaultExamName = exams.firstOrNull()?.examName?.ifBlank { "Assessment Test - I" } ?: "Assessment Test - I"
        val targetExamName = examNameFilter?.ifBlank { defaultExamName } ?: defaultExamName

        val examDates = exams.map { it.date }.distinct().sorted()

        // Ensure arrangements and duty rosters are generated for all dates of this exam
        for (date in examDates) {
            val existing = arrangementRepository.findByDate(date)
            if (existing == null || existing.invigilatorAssignments.isEmpty()) {
                generateArrangementUseCase(date)
            }
        }

        val allArrangements = arrangementRepository.allArrangements()
        val relevantArrangements = if (examDates.isNotEmpty()) {
            allArrangements.filter { it.date in examDates }
        } else {
            allArrangements
        }

        val examsByDate = exams.associateBy { it.date }

        return teachers
            .filter { it.role != UserRole.ADMIN && it.role != UserRole.HOD }
            .map { teacher ->
                val dutyAssignments = mutableListOf<TeacherDutyAssignment>()

                relevantArrangements.sortedBy { it.date }.forEach { arr ->
                    val assignment = arr.invigilatorAssignments.firstOrNull { it.teacherId == teacher.id }
                    if (assignment != null) {
                        val hall = halls[assignment.hallId]
                        val exam = examsByDate[arr.date]
                        dutyAssignments.add(
                            TeacherDutyAssignment(
                                date = arr.date,
                                hallRoomNumber = hall?.roomNumber ?: assignment.hallId,
                                floor = hall?.floor?.let { "Floor $it" } ?: "-",
                                block = hall?.block?.let { "Block $it" } ?: "-",
                                session = exam?.let { "${it.timing} (${it.session})" } ?: "8:40 a.m. TO 10:10 a.m. (FN)",
                            )
                        )
                    }
                }

                TeacherDutySummary(
                    teacherId = teacher.id,
                    teacherName = teacher.name,
                    username = teacher.username,
                    role = teacher.role,
                    active = teacher.active,
                    totalDuties = dutyAssignments.size,
                    examName = targetExamName,
                    assignments = dutyAssignments,
                )
            }
            .sortedWith(compareByDescending<TeacherDutySummary> { it.totalDuties }.thenBy { it.teacherName })
    }
}
