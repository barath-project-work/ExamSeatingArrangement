package com.example.examhallallocation.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StudentEntity::class,
        TeacherEntity::class,
        SubjectEntity::class,
        ExamEntity::class,
        HallEntity::class,
        ArrangementEntity::class,
        HallAssignmentEntity::class,
        InvigilatorAssignmentEntity::class,
        SyncQueueItemEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class ExamHallDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun teacherDao(): TeacherDao
    abstract fun subjectDao(): SubjectDao
    abstract fun examDao(): ExamDao
    abstract fun hallDao(): HallDao
    abstract fun arrangementDao(): ArrangementDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exams ADD COLUMN session TEXT NOT NULL DEFAULT 'FN'")
                db.execSQL("ALTER TABLE exams ADD COLUMN timing TEXT NOT NULL DEFAULT '8:40 a.m. TO 10:10 a.m.'")
                db.execSQL("ALTER TABLE exams ADD COLUMN department TEXT NOT NULL DEFAULT 'CSE'")
                db.execSQL("ALTER TABLE exams ADD COLUMN studentCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subjects (
                        id TEXT PRIMARY KEY NOT NULL,
                        code TEXT NOT NULL,
                        name TEXT NOT NULL,
                        year INTEGER NOT NULL,
                        semester INTEGER NOT NULL,
                        department TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_subjects_code ON subjects (code)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM hall_assignments")
                db.execSQL("DELETE FROM invigilator_assignments")
                db.execSQL("DELETE FROM arrangements")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Purge any legacy arrangements & eliminate any HOD entries
                db.execSQL("DELETE FROM hall_assignments")
                db.execSQL("DELETE FROM invigilator_assignments")
                db.execSQL("DELETE FROM arrangements")
                db.execSQL("DELETE FROM teachers WHERE role = 'HOD'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Purge stale arrangements and old student gaps so fresh 1..120 cohorts are re-seeded
                db.execSQL("DELETE FROM hall_assignments")
                db.execSQL("DELETE FROM invigilator_assignments")
                db.execSQL("DELETE FROM arrangements")
                db.execSQL("DELETE FROM students")
            }
        }
    }
}
