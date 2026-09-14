package com.example.examhallallocation.data.seed

import androidx.room.withTransaction
import com.example.examhallallocation.data.local.ExamHallDatabase
import com.example.examhallallocation.data.local.toEntity
import com.example.examhallallocation.data.repository.DemoAuthRepository
import com.example.examhallallocation.data.sync.SyncManager
import com.example.examhallallocation.domain.model.Teacher
import com.example.examhallallocation.domain.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-launch data bootstrap.
 *
 * - Firebase build (google-services.json present): makes sure the two demo login
 *   accounts exist in Firebase Auth, then either hydrates the local Room cache from
 *   the cloud snapshot (fresh device joining an existing project) or seeds the demo
 *   dataset locally AND to the cloud (brand-new project), so every device converges.
 * - Offline demo build: seeds the local database with deterministic sample data and
 *   registers the demo credentials locally.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ExamHallDatabase,
    private val demoAuthRepository: dagger.Lazy<DemoAuthRepository>,
    private val syncManager: dagger.Lazy<SyncManager>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun seedIfEmpty() {
        scope.launch {
            runCatching {
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    bootstrapAndHydrate()
                } else {
                    seedDemoData(pushToCloud = false)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Firebase mode
    // ------------------------------------------------------------------

    private suspend fun bootstrapAndHydrate() {
        bootstrapAdminAccount()
        bootstrapHalls()
        hydrateFromCloud()
    }

    /**
     * Pulls genuine cloud records from Firestore and populates local Room database.
     * Accessible publicly so LoginViewModel and Dashboard can synchronize on-demand.
     */
    suspend fun hydrateFromCloud(): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            if (auth != null && auth.currentUser == null) {
                // Ensure authenticated session so Firestore security rules permit access
                runCatching {
                    auth.signInWithEmailAndPassword("admin@grt.edu.in", "admin@123").await()
                }
            }

            val snapshot = syncManager.get().pullAll()
            if (snapshot != null && !snapshot.isEmpty) {
                database.withTransaction {
                    if (snapshot.students.isNotEmpty()) {
                        database.studentDao().deleteAll()
                        database.studentDao().insertAll(snapshot.students.map { it.toEntity() })
                    }
                    if (snapshot.teachers.isNotEmpty()) {
                        snapshot.teachers.forEach { database.teacherDao().insert(it.toEntity()) }
                    }
                    if (snapshot.exams.isNotEmpty()) {
                        database.examDao().deleteAll()
                        database.examDao().insertAll(snapshot.exams.map { it.toEntity() })
                    }
                    if (snapshot.halls.isNotEmpty()) {
                        database.hallDao().deleteAll()
                        database.hallDao().insertAll(snapshot.halls.map { it.toEntity() })
                    }
                }
                snapshot.arrangements.forEach { arrangement ->
                    database.arrangementDao().replaceForDate(
                        date = arrangement.date,
                        arrangement = arrangement.toEntity(),
                        hallAssignments = arrangement.hallAssignments.map { it.toEntity(arrangement.id) },
                        invigilatorAssignments = arrangement.invigilatorAssignments.map { it.toEntity(arrangement.id) },
                    )
                }
                android.util.Log.i(TAG, "Hydrated ${snapshot.students.size} students, ${snapshot.exams.size} exams from Cloud Firestore")
                true
            } else {
                false
            }
        }.getOrElse { e ->
            android.util.Log.w(TAG, "hydrateFromCloud failed", e)
            false
        }
    }

    /**
     * Ensures the primary Admin account exists in Firebase Auth and Cloud Firestore.
     * Login: admin@grt.edu.in / admin@123
     */
    private suspend fun bootstrapAdminAccount() {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull() ?: return
        val username = "admin"
        val password = "admin@123"
        val email = "$username@grt.edu.in"

        runCatching {
            val existing = runCatching { auth.signInWithEmailAndPassword(email, password).await() }
            if (existing.getOrNull()?.user == null) {
                auth.createUserWithEmailAndPassword(email, password).await()
            }
        }

        val adminTeacher = com.example.examhallallocation.domain.model.Teacher(
            id = "tea_admin",
            name = "Exam Cell Admin",
            username = "admin",
            role = com.example.examhallallocation.domain.model.UserRole.ADMIN,
            active = true,
        )
        syncManager.get().pushTeacher(adminTeacher)
        database.teacherDao().insert(adminTeacher.toEntity())
    }

    /**
     * Provisions the institutional physical exam halls (A212..B215) to Firestore and Room.
     */
    private suspend fun bootstrapHalls() {
        val halls = SeedDataProvider.halls()
        database.hallDao().run {
            halls.forEach { insert(it.toEntity()) }
        }
        val sync = syncManager.get()
        halls.forEach { sync.pushHall(it) }
    }

    // ------------------------------------------------------------------
    // Offline Demo Fallback Only (when Firebase is completely absent)
    // ------------------------------------------------------------------

    private suspend fun seedDemoData(pushToCloud: Boolean) {
        val hasHalls = database.hallDao().observeAll().first().isNotEmpty()
        if (!hasHalls) {
            database.hallDao().run {
                SeedDataProvider.halls().forEach { insert(it.toEntity()) }
            }
        }

        val hasExams = database.examDao().observeAll().first().isNotEmpty()
        if (!hasExams) {
            database.examDao().run {
                insertAll(SeedDataProvider.exams().map { it.toEntity() })
            }
        }

        val adminTeacher = Teacher(
            id = "tea_admin",
            name = "Exam Cell Admin",
            username = SeedDataProvider.DEMO_ADMIN_USERNAME,
            role = UserRole.ADMIN,
            active = true,
        )
        database.teacherDao().insert(adminTeacher.toEntity())
        demoAuthRepository.get().registerCredential("tea_admin", SeedDataProvider.DEMO_ADMIN_PASSWORD)
    }

    private companion object {
        const val TAG = "DatabaseSeeder"
    }
}
