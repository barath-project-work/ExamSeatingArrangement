package com.example.examhallallocation.data.repository

import com.example.examhallallocation.data.local.*
import com.example.examhallallocation.data.sync.SyncManager
import com.example.examhallallocation.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositories own the dual-write contract: Room is always written first (source of
 * truth for reads, works fully offline); the same change is then pushed to Firestore
 * so other devices converge. Cloud failures never block or revert the local write.
 * A fresh device hydrates itself once via [SyncManager.pullAll] (see DatabaseSeeder).
 */
@Singleton
class StudentRepository @Inject constructor(
    private val studentDao: StudentDao,
    private val sync: SyncManager,
) {
    fun observeAll(): Flow<List<Student>> = studentDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveCount(): Flow<Int> = studentDao.observeActiveCount()

    suspend fun findByRegisterNumber(registerNumber: String): Student? =
        studentDao.findByRegisterNumber(registerNumber)?.toDomain()

    suspend fun studentsByIds(ids: List<String>): List<Student> =
        if (ids.isEmpty()) emptyList() else studentDao.getByIds(ids).map { it.toDomain() }

    suspend fun add(student: Student): Boolean {
        val final = if (student.position == 0) {
            val extracted = student.extractRollNumber()
            val pos = if (extracted in 1..9999) extracted else ((studentDao.maxPosition(student.year.value) ?: 0) + 1)
            student.copy(id = ensureId(student), position = pos)
        } else {
            student.copy(id = ensureId(student))
        }
        return try {
            studentDao.insert(final.toEntity())
            sync.pushStudent(final)
            true
        } catch (_: Exception) {
            false // unique constraint violation -> duplicate register number
        }
    }

    suspend fun update(student: Student): Boolean {
        return try {
            studentDao.update(student.toEntity())
            sync.pushStudent(student)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun delete(student: Student) {
        studentDao.delete(student.toEntity())
        sync.pushStudentDeletion(student.id)
    }

    /**
     * Replaces the entire student list (bulk real-data load). Cloud deletions are
     * pushed for removed ids; all new/kept rows are then pushed via upserts.
     */
    suspend fun replaceAll(students: List<Student>) {
        val oldIds = studentDao.allIds().toSet()
        studentDao.deleteAll()
        studentDao.insertAll(students.map { it.toEntity() })
        oldIds.filterNot { id -> students.any { it.id == id } }
            .forEach { sync.pushStudentDeletion(it) }
        sync.pushStudentsBatch(students)
    }

    suspend fun addAll(students: List<Student>) {
        if (students.isEmpty()) return
        studentDao.insertAll(students.map { it.toEntity() })
        sync.pushStudentsBatch(students)
    }

    /** Removes everything (cloud deletions pushed); used by "clear before import". */
    suspend fun clearAll() {
        val oldIds = studentDao.allIds()
        studentDao.deleteAll()
        oldIds.forEach { sync.pushStudentDeletion(it) }
    }

    suspend fun maxPosition(year: StudentYear): Int = studentDao.maxPosition(year.value) ?: 0

    suspend fun allRegisterNumbers(): Set<String> = studentDao.allRegisterNumbers().toSet()

    private fun ensureId(student: Student): String =
        student.id.ifBlank { "stu_${student.registerNumber}" }
}

@Singleton
class TeacherRepository @Inject constructor(
    private val teacherDao: TeacherDao,
    private val sync: SyncManager,
) {
    fun observeAll(): Flow<List<Teacher>> = teacherDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun findByUsername(username: String): Teacher? =
        teacherDao.findByUsername(username)?.toDomain()

    suspend fun findById(id: String): Teacher? = teacherDao.findById(id)?.toDomain()

    suspend fun add(teacher: Teacher): Boolean {
        val final = teacher.copy(id = teacher.id.ifBlank { "tea_${teacher.username}" })
        return try {
            teacherDao.insert(final.toEntity())
            sync.pushTeacher(final)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun update(teacher: Teacher) {
        teacherDao.update(teacher.toEntity())
        sync.pushTeacher(teacher)
    }

    suspend fun delete(teacher: Teacher) {
        teacherDao.delete(teacher.toEntity())
        sync.pushTeacherDeletion(teacher.id)
    }
}

@Singleton
class ExamRepository @Inject constructor(
    private val examDao: ExamDao,
    private val sync: SyncManager,
) {
    fun observeAll(): Flow<List<Exam>> = examDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun findByDate(date: String): List<Exam> = examDao.findByDate(date).map { it.toDomain() }

    suspend fun examDateRange(): Pair<String, String>? {
        val first = examDao.firstDate() ?: return null
        val last = examDao.lastDate() ?: return null
        return first to last
    }

    suspend fun add(exam: Exam) {
        examDao.insert(exam.toEntity())
        sync.pushExam(exam)
    }

    suspend fun addAll(exams: List<Exam>) {
        examDao.insertAll(exams.map { it.toEntity() })
        sync.pushExamsBatch(exams)
    }

    suspend fun delete(exam: Exam) {
        examDao.delete(exam.toEntity())
        sync.pushExamDeletion(exam.id)
    }

    /** Replaces the entire exam schedule (bulk real-data load). */
    suspend fun replaceAll(exams: List<Exam>) {
        val oldIds = examDao.allIds().toSet()
        examDao.deleteAll()
        examDao.insertAll(exams.map { it.toEntity() })
        oldIds.filterNot { id -> exams.any { it.id == id } }
            .forEach { sync.pushExamDeletion(it) }
        sync.pushExamsBatch(exams)
    }

    suspend fun clearAll() {
        val oldIds = examDao.allIds()
        examDao.deleteAll()
        oldIds.forEach { sync.pushExamDeletion(it) }
    }
}

@Singleton
class HallRepository @Inject constructor(
    private val hallDao: HallDao,
    private val sync: SyncManager,
) {
    fun observeAll(): Flow<List<Hall>> = hallDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun activeHalls(): List<Hall> = hallDao.activeHalls().map { it.toDomain() }

    suspend fun add(hall: Hall): Boolean {
        val final = hall.copy(id = hall.id.ifBlank { "hall_${hall.block}_${hall.roomNumber}" })
        return try {
            hallDao.insert(final.toEntity())
            sync.pushHall(final)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun update(hall: Hall) {
        hallDao.update(hall.toEntity())
        sync.pushHall(hall)
    }

    suspend fun delete(hall: Hall) {
        // Deactivate rather than remove: past arrangements may reference the hall.
        val deactivated = hall.copy(active = false)
        hallDao.update(deactivated.toEntity())
        sync.pushHall(deactivated)
    }

    /** Replaces the entire hall list (bulk real-data load). */
    suspend fun replaceAll(halls: List<Hall>) {
        val oldIds = hallDao.allIds().toSet()
        hallDao.deleteAll()
        hallDao.insertAll(halls.map { it.toEntity() })
        oldIds.filterNot { id -> halls.any { it.id == id } }
            .forEach { sync.pushHallDeletion(it) }
        halls.forEach { sync.pushHall(it) }
    }

    suspend fun clearAll() {
        val oldIds = hallDao.allIds()
        hallDao.deleteAll()
        oldIds.forEach { sync.pushHallDeletion(it) }
    }
}

@Singleton
class ArrangementRepository @Inject constructor(
    private val arrangementDao: ArrangementDao,
    private val studentDao: StudentDao,
    private val sync: SyncManager,
) {
    fun observeAllDates(): Flow<List<String>> =
        arrangementDao.observeAll().map { list -> list.map { it.date } }

    suspend fun allArrangements(): List<Arrangement> {
        return arrangementDao.allArrangements().map { entity ->
            entity.toDomain(
                hallAssignments = arrangementDao.hallAssignmentsFor(entity.id),
                invigilatorAssignments = arrangementDao.invigilatorAssignmentsFor(entity.id),
            )
        }
    }

    suspend fun findByDate(date: String): Arrangement? {
        val entity = arrangementDao.findByDateRaw(date) ?: return null
        return entity.toDomain(
            hallAssignments = arrangementDao.hallAssignmentsFor(entity.id),
            invigilatorAssignments = arrangementDao.invigilatorAssignmentsFor(entity.id),
        )
    }

    suspend fun replaceForDate(date: String, arrangement: Arrangement) {
        val stale = findByDate(date)
        arrangementDao.replaceForDate(
            date = date,
            arrangement = arrangement.toEntity(),
            hallAssignments = arrangement.hallAssignments.map { it.toEntity(arrangement.id) },
            invigilatorAssignments = arrangement.invigilatorAssignments.map { it.toEntity(arrangement.id) },
        )
        sync.pushArrangement(arrangement)
        // Cloud cleanup for a regeneration that changed the arrangement id.
        if (stale != null && stale.id != arrangement.id) sync.pushArrangementDeletion(stale.id)
    }

    suspend fun updateStatus(id: String, status: ArrangementStatus) {
        arrangementDao.updateStatus(id, status.name)
        sync.pushArrangementStatus(id, status.name)
    }

    suspend fun invigilatorAssignmentsForTeacher(teacherId: String): List<Pair<String, String>> =
        arrangementDao.allInvigilatorAssignments()
            .filter { it.teacherId == teacherId }
            .map { it.arrangementId to it.hallId }

    suspend fun studentsByIds(ids: List<String>): List<Student> =
        if (ids.isEmpty()) emptyList() else studentDao.getByIds(ids).map { it.toDomain() }

    suspend fun clearAll() {
        arrangementDao.clearAll()
        // Note: cloud arrangements are replaced per-date on regeneration; full cloud
        // wipe is intentionally manual from the Firebase console.
    }
}
