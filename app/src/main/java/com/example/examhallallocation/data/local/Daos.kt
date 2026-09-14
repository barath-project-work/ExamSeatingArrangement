package com.example.examhallallocation.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY year ASC, position ASC, registerNumber ASC")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE registerNumber = :registerNumber LIMIT 1")
    suspend fun findByRegisterNumber(registerNumber: String): StudentEntity?

    @Query("SELECT * FROM students WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<StudentEntity>

    @Query("SELECT MAX(position) FROM students WHERE year = :year")
    suspend fun maxPosition(year: Int): Int?

    @Query("SELECT registerNumber FROM students")
    suspend fun allRegisterNumbers(): List<String>

    @Query("SELECT id FROM students")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM students")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(students: List<StudentEntity>)

    @Update
    suspend fun update(student: StudentEntity)

    @Delete
    suspend fun delete(student: StudentEntity)

    @Query("SELECT COUNT(*) FROM students WHERE active = 1")
    fun observeActiveCount(): Flow<Int>
}

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers ORDER BY name ASC")
    fun observeAll(): Flow<List<TeacherEntity>>

    @Query("SELECT * FROM teachers WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): TeacherEntity?

    @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TeacherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(teacher: TeacherEntity)

    @Update
    suspend fun update(teacher: TeacherEntity)

    @Delete
    suspend fun delete(teacher: TeacherEntity)
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE active = 1 ORDER BY year ASC, semester ASC, code ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE active = 1 ORDER BY year ASC, semester ASC, code ASC")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE year = :year AND active = 1 ORDER BY semester ASC, code ASC")
    suspend fun getByYear(year: Int): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<SubjectEntity>)

    @Delete
    suspend fun delete(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY date ASC, year ASC")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE date = :date")
    suspend fun findByDate(date: String): List<ExamEntity>

    @Query("SELECT MIN(date) FROM exams")
    suspend fun firstDate(): String?

    @Query("SELECT MAX(date) FROM exams")
    suspend fun lastDate(): String?

    @Query("SELECT id FROM exams")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM exams")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exam: ExamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exams: List<ExamEntity>)

    @Delete
    suspend fun delete(exam: ExamEntity)
}

@Dao
interface HallDao {
    @Query("SELECT * FROM halls ORDER BY block ASC, roomNumber ASC")
    fun observeAll(): Flow<List<HallEntity>>

    @Query("SELECT * FROM halls WHERE active = 1 ORDER BY block ASC, roomNumber ASC")
    suspend fun activeHalls(): List<HallEntity>

    @Query("SELECT id FROM halls")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM halls")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hall: HallEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(halls: List<HallEntity>)

    @Update
    suspend fun update(hall: HallEntity)

    @Delete
    suspend fun delete(hall: HallEntity)
}

@Dao
interface ArrangementDao {
    @Transaction
    @Query("SELECT * FROM arrangements ORDER BY date ASC")
    fun observeAll(): Flow<List<ArrangementEntity>>

    @Query("SELECT * FROM arrangements WHERE date = :date LIMIT 1")
    suspend fun findByDateRaw(date: String): ArrangementEntity?

    @Query(
        "SELECT * FROM hall_assignments WHERE arrangementId = :arrangementId"
    )
    suspend fun hallAssignmentsFor(arrangementId: String): List<HallAssignmentEntity>

    @Query(
        "SELECT * FROM invigilator_assignments WHERE arrangementId = :arrangementId"
    )
    suspend fun invigilatorAssignmentsFor(arrangementId: String): List<InvigilatorAssignmentEntity>

    @Query("SELECT * FROM invigilator_assignments")
    suspend fun allInvigilatorAssignments(): List<InvigilatorAssignmentEntity>

    @Query("SELECT * FROM arrangements")
    suspend fun allArrangements(): List<ArrangementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArrangement(arrangement: ArrangementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHallAssignments(assignments: List<HallAssignmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvigilatorAssignments(assignments: List<InvigilatorAssignmentEntity>)

    @Query("DELETE FROM hall_assignments WHERE arrangementId = :arrangementId")
    suspend fun deleteHallAssignments(arrangementId: String)

    @Query("DELETE FROM invigilator_assignments WHERE arrangementId = :arrangementId")
    suspend fun deleteInvigilatorAssignments(arrangementId: String)

    @Query("DELETE FROM arrangements WHERE date = :date")
    suspend fun deleteArrangementByDate(date: String)

    @Query("UPDATE arrangements SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM arrangements")
    suspend fun clearAll()

    @Query("DELETE FROM arrangements")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceForDate(
        date: String,
        arrangement: ArrangementEntity,
        hallAssignments: List<HallAssignmentEntity>,
        invigilatorAssignments: List<InvigilatorAssignmentEntity>,
    ) {
        deleteArrangementByDate(date)
        insertArrangement(arrangement)
        insertHallAssignments(hallAssignments)
        insertInvigilatorAssignments(invigilatorAssignments)
    }
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<SyncQueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueItemEntity): Long

    @Delete
    suspend fun remove(item: SyncQueueItemEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun removeById(id: Long)

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}

