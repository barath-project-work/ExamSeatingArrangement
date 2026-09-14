package com.example.examhallallocation.data.repository

import android.util.Log
import com.example.examhallallocation.data.local.TeacherDao
import com.example.examhallallocation.domain.model.UserSession
import com.example.examhallallocation.domain.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-backed authentication. Uses Firebase Auth for credentials and the
 * Firestore `teachers` collection (documents keyed by the app's stable `tea_<username>`
 * ids) for name/role/active, so authorization data is shared across devices.
 * When Firebase is not configured (no google-services.json), the DI selector binds
 * the demo repository instead.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth?,
    private val firestore: FirebaseFirestore?,
    private val teacherDao: TeacherDao,
) : AuthRepository {

    override val isDemoMode: Boolean get() = false

    val isAvailable: Boolean get() = firebaseAuth != null && firestore != null

    override suspend fun login(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext AuthResult.Error("Firebase is not configured in this build.")

        val raw = username.trim()
        val normalized = raw.lowercase()
        val email = if (normalized.contains("@")) normalized else "$normalized@grt.edu.in"
        val userHandle = if (normalized.contains("@")) normalized.substringBefore("@") else normalized
        val isAdmin = userHandle == "admin" || email.startsWith("admin@") || email.equals("admin@grt.edu.in", ignoreCase = true)

        runCatching {
            val authResult = firebaseAuth!!.signInWithEmailAndPassword(email, password).await()
            authResult.user?.uid ?: error("No user")
            val profile = findProfile(userHandle)

            if (profile?.active == false) {
                firebaseAuth.signOut()
                return@withContext AuthResult.InvalidCredentials(
                    "This account has been deactivated. Contact the exam cell."
                )
            }

            val resolvedRole = if (isAdmin || profile?.role == UserRole.ADMIN) {
                UserRole.ADMIN
            } else {
                profile?.role ?: UserRole.NORMAL_TEACHER
            }

            val resolvedTeacherId = if (resolvedRole == UserRole.ADMIN) "tea_admin" else (profile?.id ?: "tea_$userHandle")
            val resolvedName = profile?.name ?: if (resolvedRole == UserRole.ADMIN) "Exam Cell Admin" else userHandle

            // Ensure admin profile document exists in Firestore
            if (resolvedRole == UserRole.ADMIN) {
                runCatching {
                    firestore?.collection(TEACHERS)?.document(resolvedTeacherId)?.set(
                        mapOf(
                            "id" to resolvedTeacherId,
                            "name" to resolvedName,
                            "username" to "admin",
                            "email" to email,
                            "role" to "ADMIN",
                            "active" to true,
                            "department" to "Exam Cell"
                        ),
                        SetOptions.merge()
                    )?.await()
                }
            }

            val session = UserSession(
                teacherId = resolvedTeacherId,
                name = resolvedName,
                username = if (resolvedRole == UserRole.ADMIN) "admin" else userHandle,
                role = resolvedRole,
            )

            // Cache profile in Room so teacher & admin screens work fully offline.
            runCatching {
                if (normalized != userHandle) {
                    teacherDao.findByUsername(normalized)?.let { stale ->
                        teacherDao.delete(stale)
                    }
                }
                if (resolvedRole == UserRole.ADMIN) {
                    teacherDao.findByUsername("admin")?.let { existing ->
                        if (existing.role != UserRole.ADMIN.name) {
                            teacherDao.delete(existing)
                        }
                    }
                }
                teacherDao.insert(
                    com.example.examhallallocation.data.local.TeacherEntity(
                        id = session.teacherId,
                        name = session.name,
                        username = session.username,
                        role = session.role.name,
                        active = true,
                    )
                )
            }
            AuthResult.Success(session)
        }.getOrElse { e ->
            Log.w(TAG, "Firebase login failed", e)
            AuthResult.InvalidCredentials("Invalid username or password. Please try again.")
        }
    }

    override suspend fun currentUser(): UserSession? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        val user = firebaseAuth!!.currentUser ?: return@withContext null
        val email = user.email.orEmpty().lowercase()
        val username = if (email.contains("@")) email.substringBefore("@") else email
        val isAdmin = username == "admin" || email.startsWith("admin@") || email.equals("admin@grt.edu.in", ignoreCase = true)

        // Fast path: cached Room profile (offline-friendly).
        val cached = teacherDao.findByUsername(username)
        if (cached != null && cached.active) {
            val role = if (isAdmin) {
                UserRole.ADMIN
            } else {
                runCatching { UserRole.valueOf(cached.role) }.getOrDefault(UserRole.NORMAL_TEACHER)
            }
            return@withContext UserSession(
                teacherId = if (role == UserRole.ADMIN) "tea_admin" else cached.id,
                name = cached.name,
                username = cached.username,
                role = role,
            )
        }

        // Slow path: resolve profile from the cloud.
        val profile = findProfile(username)
        val role = if (isAdmin || profile?.role == UserRole.ADMIN) UserRole.ADMIN else (profile?.role ?: UserRole.NORMAL_TEACHER)
        val session = UserSession(
            teacherId = if (role == UserRole.ADMIN) "tea_admin" else (profile?.id ?: "tea_$username"),
            name = profile?.name ?: if (role == UserRole.ADMIN) "Exam Cell Admin" else username,
            username = if (role == UserRole.ADMIN) "admin" else username,
            role = role,
        )

        runCatching {
            teacherDao.insert(
                com.example.examhallallocation.data.local.TeacherEntity(
                    id = session.teacherId,
                    name = session.name,
                    username = session.username,
                    role = session.role.name,
                    active = true,
                )
            )
        }
        session
    }

    override suspend fun logout() {
        firebaseAuth?.signOut()
    }

    /** Looks up the teacher profile document by doc id, username, or email */
    private suspend fun findProfile(identifier: String): Profile? {
        val fs = firestore ?: return null
        val handle = if (identifier.contains("@")) identifier.substringBefore("@") else identifier
        val isAdmin = handle == "admin" || identifier.startsWith("admin@")

        // 1. Direct doc lookup by tea_<handle>
        val directDoc = runCatching {
            fs.collection(TEACHERS).document("tea_$handle").get().await()
        }.getOrNull()

        if (directDoc != null && directDoc.exists()) {
            val rawRole = directDoc.getString(ROLE_FIELD) ?: ""
            return Profile(
                id = directDoc.id,
                name = directDoc.getString(NAME_FIELD) ?: if (isAdmin) "Exam Cell Admin" else handle,
                role = if (isAdmin) UserRole.ADMIN else runCatching { UserRole.valueOf(rawRole) }.getOrDefault(UserRole.NORMAL_TEACHER),
                active = directDoc.getBoolean(ACTIVE_FIELD) ?: true,
            )
        }

        // 2. Query by username == handle
        val queryDoc = runCatching {
            fs.collection(TEACHERS)
                .whereEqualTo(USERNAME_FIELD, handle)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
        }.getOrNull()

        if (queryDoc != null && queryDoc.exists()) {
            val rawRole = queryDoc.getString(ROLE_FIELD) ?: ""
            return Profile(
                id = queryDoc.id,
                name = queryDoc.getString(NAME_FIELD) ?: if (isAdmin) "Exam Cell Admin" else handle,
                role = if (isAdmin) UserRole.ADMIN else runCatching { UserRole.valueOf(rawRole) }.getOrDefault(UserRole.NORMAL_TEACHER),
                active = queryDoc.getBoolean(ACTIVE_FIELD) ?: true,
            )
        }

        // 3. Query by username == identifier (if full email stored)
        if (identifier.contains("@")) {
            val emailDoc = runCatching {
                fs.collection(TEACHERS)
                    .whereEqualTo(USERNAME_FIELD, identifier)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
            }.getOrNull()

            if (emailDoc != null && emailDoc.exists()) {
                val rawRole = emailDoc.getString(ROLE_FIELD) ?: ""
                return Profile(
                    id = emailDoc.id,
                    name = emailDoc.getString(NAME_FIELD) ?: if (isAdmin) "Exam Cell Admin" else handle,
                    role = if (isAdmin) UserRole.ADMIN else runCatching { UserRole.valueOf(rawRole) }.getOrDefault(UserRole.NORMAL_TEACHER),
                    active = emailDoc.getBoolean(ACTIVE_FIELD) ?: true,
                )
            }
        }

        return null
    }

    private data class Profile(val id: String, val name: String, val role: UserRole, val active: Boolean)

    private companion object {
        const val TAG = "FirebaseAuthRepo"
        const val TEACHERS = "teachers"
        const val ROLE_FIELD = "role"
        const val NAME_FIELD = "name"
        const val USERNAME_FIELD = "username"
        const val ACTIVE_FIELD = "active"
    }
}
