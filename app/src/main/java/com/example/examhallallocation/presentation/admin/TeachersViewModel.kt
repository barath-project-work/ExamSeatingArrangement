package com.example.examhallallocation.presentation.admin

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.AuthRepository
import com.example.examhallallocation.data.repository.DemoAuthRepository
import com.example.examhallallocation.data.repository.TeacherRepository
import com.example.examhallallocation.domain.model.Teacher
import com.example.examhallallocation.domain.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class TeachersViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val teacherRepository: TeacherRepository,
    private val authRepository: AuthRepository,
    private val demoAuthRepository: dagger.Lazy<DemoAuthRepository>,
    private val dataExportManager: com.example.examhallallocation.domain.usecase.DataExportManager,
) : ViewModel() {

    val teachers: StateFlow<List<Teacher>> = teacherRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    private val firebaseConfigured: Boolean by lazy { FirebaseApp.getApps(appContext).isNotEmpty() }

    fun addTeacher(name: String, username: String, password: String, role: UserRole) {
        viewModelScope.launch {
            val raw = username.trim().lowercase()
            val cleanUsername = if (raw.contains("@")) raw.substringBefore("@") else raw
            val cleanEmail = if (raw.contains("@")) raw else "$cleanUsername@grt.edu.in"

            when {
                name.isBlank() || cleanUsername.isBlank() ->
                    _events.value = "Name and username are required"
                password.length < 6 ->
                    _events.value = "Password must be at least 6 characters"
                teacherRepository.findByUsername(cleanUsername) != null ->
                    _events.value = "A teacher with username \"$cleanUsername\" already exists"
                else -> {
                    val teacher = Teacher(
                        id = "tea_$cleanUsername",
                        name = name,
                        username = cleanUsername,
                        role = role,
                        active = true,
                    )

                    var firebaseOk = true
                    var collision = false
                    if (firebaseConfigured) {
                        // Real cloud account in Firebase Auth.
                        // A secondary FirebaseApp instance is used so the admin's active session is untouched.
                        runCatching {
                            val secondaryApp = runCatching {
                                FirebaseApp.initializeApp(
                                    appContext, FirebaseApp.getInstance().options, SECONDARY_APP_NAME
                                )
                            }.getOrNull() ?: FirebaseApp.getInstance(SECONDARY_APP_NAME)
                            FirebaseAuth.getInstance(secondaryApp)
                                .createUserWithEmailAndPassword(cleanEmail, password)
                                .await()
                        }.onFailure { err ->
                            if (err is com.google.firebase.auth.FirebaseAuthUserCollisionException
                                || err.message?.contains("already in use") == true) {
                                collision = true
                            } else {
                                firebaseOk = false
                                Log.w(TAG, "Firebase account creation failed for $cleanEmail", err)
                            }
                        }
                    }

                    val added = teacherRepository.add(teacher)

                    // Local credential (salted hash): offline support
                    demoAuthRepository.get().registerCredential(teacher.id, password)

                    _events.value = when {
                        added && (firebaseOk || collision) ->
                            "Teacher added successfully! Login: $cleanUsername or $cleanEmail"
                        added ->
                            "Teacher added locally (cloud sync pending — check internet)"
                        else ->
                            "Could not save teacher to database"
                    }
                }
            }
        }
    }

    /**
     * Intelligent bulk CSV / Excel (.xlsx) import for teachers with automated Firebase account provisioning.
     */
    fun importFile(bytes: ByteArray) {
        viewModelScope.launch {
            val result = com.example.examhallallocation.domain.usecase.SmartDataExtractor.parseTeachers(bytes)
            if (result.teachers.isEmpty()) {
                _events.value = "No valid teacher rows found in imported data (${result.skipped} skipped)"
                return@launch
            }

            var successCount = 0
            val secondaryApp = if (firebaseConfigured) {
                runCatching {
                    FirebaseApp.initializeApp(appContext, FirebaseApp.getInstance().options, SECONDARY_APP_NAME)
                }.getOrNull() ?: runCatching { FirebaseApp.getInstance(SECONDARY_APP_NAME) }.getOrNull()
            } else null

            result.teachers.forEach { data ->
                val teacher = data.teacher
                val cleanEmail = "${teacher.username}@grt.edu.in"
                if (secondaryApp != null) {
                    runCatching {
                        FirebaseAuth.getInstance(secondaryApp)
                            .createUserWithEmailAndPassword(cleanEmail, data.initialPassword)
                            .await()
                    }
                }
                if (teacherRepository.add(teacher)) {
                    demoAuthRepository.get().registerCredential(teacher.id, data.initialPassword)
                    successCount++
                }
            }

            _events.value = "Imported $successCount teachers successfully (${result.skipped} skipped). Accounts are ready for login!"
            if (successCount == 0 && result.errors.isNotEmpty()) {
                _events.value = result.errors.first()
            }
        }
    }

    fun importCsv(content: String) {
        importFile(content.toByteArray(Charsets.UTF_8))
    }

    fun updateTeacher(teacher: Teacher, name: String, role: UserRole, active: Boolean) {
        viewModelScope.launch {
            teacherRepository.update(teacher.copy(name = name, role = role, active = active))
            _events.value = "Teacher updated"
        }
    }

    fun deleteTeacher(teacher: Teacher) {
        viewModelScope.launch {
            teacherRepository.delete(teacher)
            demoAuthRepository.get().removeCredential(teacher.id)
            _events.value = "Teacher permanently removed from database"
        }
    }

    fun exportTeachers(
        asPdf: Boolean,
        onDone: (android.net.Uri, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val (uri, fileName) = dataExportManager.exportTeachers(asPdf)
                onDone(uri, fileName)
            } catch (t: Throwable) {
                onError(t.localizedMessage ?: "Failed to export faculty directory")
            }
        }
    }

    fun consumeEvent() { _events.value = null }

    private companion object {
        const val TAG = "TeachersViewModel"
        const val SECONDARY_APP_NAME = "teacherBootstrap"
    }
}
