package com.example.examhallallocation.di

import android.content.Context
import androidx.room.Room
import com.example.examhallallocation.data.local.ExamHallDatabase
import com.example.examhallallocation.data.repository.AuthRepository
import com.example.examhallallocation.data.repository.DemoAuthRepository
import com.example.examhallallocation.data.repository.FirebaseAuthRepository
import com.example.examhallallocation.domain.model.UserSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImplSelector): AuthRepository
}

/**
 * Chooses the auth implementation at runtime: Firebase when the app was built with
 * google-services.json and Firebase initializes, otherwise the local demo repository.
 * This keeps demo-first builds working offline while activating Firebase transparently.
 */
@Singleton
class AuthRepositoryImplSelector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val demoAuthRepository: dagger.Lazy<DemoAuthRepository>,
    private val firebaseAuthRepository: dagger.Lazy<FirebaseAuthRepository>,
) : AuthRepository {

    private val delegate: AuthRepository by lazy {
        val firebaseConfigured = runCatching {
            com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
        }.getOrDefault(false)
        if (firebaseConfigured) firebaseAuthRepository.get() else demoAuthRepository.get()
    }

    override suspend fun login(username: String, password: String) = delegate.login(username, password)

    override suspend fun currentUser(): UserSession? = delegate.currentUser()

    override suspend fun logout() = delegate.logout()

    override val isDemoMode: Boolean get() = delegate.isDemoMode
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExamHallDatabase =
        Room.databaseBuilder(context, ExamHallDatabase::class.java, "grt_exam_hall.db")
            .addMigrations(ExamHallDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideStudentDao(db: ExamHallDatabase): com.example.examhallallocation.data.local.StudentDao = db.studentDao()
    @Provides fun provideTeacherDao(db: ExamHallDatabase): com.example.examhallallocation.data.local.TeacherDao = db.teacherDao()
    @Provides fun provideExamDao(db: ExamHallDatabase): com.example.examhallallocation.data.local.ExamDao = db.examDao()
    @Provides fun provideHallDao(db: ExamHallDatabase): com.example.examhallallocation.data.local.HallDao = db.hallDao()
    @Provides fun provideArrangementDao(db: ExamHallDatabase): com.example.examhallallocation.data.local.ArrangementDao = db.arrangementDao()
    @Provides fun provideSyncQueueDao(db: ExamHallDatabase): com.example.examhallallocation.data.local.SyncQueueDao = db.syncQueueDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth? =
        runCatching { FirebaseAuth.getInstance() }.getOrNull()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore? =
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()
}
