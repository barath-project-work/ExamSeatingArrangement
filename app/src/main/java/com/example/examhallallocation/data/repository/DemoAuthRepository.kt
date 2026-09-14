package com.example.examhallallocation.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.examhallallocation.data.local.TeacherDao
import com.example.examhallallocation.domain.model.UserSession
import com.example.examhallallocation.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local authentication for demo/offline mode. Passwords are stored as salted SHA-256
 * hashes inside EncryptedSharedPreferences - never in plaintext.
 */
@Singleton
class DemoAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val teacherDao: TeacherDao,
) : AuthRepository {

    private val prefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "grt_auth_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Fall back to regular prefs if keystore is unavailable (rare, demo only).
            context.getSharedPreferences("grt_auth_fallback", Context.MODE_PRIVATE)
        }
    }

    override val isDemoMode: Boolean get() = true

    override suspend fun login(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val clean = username.trim().lowercase()
        val handle = if (clean.contains("@")) clean.substringBefore("@") else clean
        val teacher = teacherDao.findByUsername(clean)
            ?: teacherDao.findByUsername(handle)
            ?: return@withContext AuthResult.InvalidCredentials(INVALID_MESSAGE)

        if (!teacher.active) {
            return@withContext AuthResult.InvalidCredentials("This account has been deactivated. Contact the exam cell.")
        }

        val storedHash = prefs.getString(hashKey(teacher.id), null)
            ?: return@withContext AuthResult.InvalidCredentials(INVALID_MESSAGE)

        if (hash(password, saltFor(teacher.id)) == storedHash) {
            saveSession(teacher.id, teacher.name, teacher.username, teacher.role)
            AuthResult.Success(
                UserSession(
                    teacherId = teacher.id,
                    name = teacher.name,
                    username = teacher.username,
                    role = runCatching { UserRole.valueOf(teacher.role) }.getOrDefault(UserRole.NORMAL_TEACHER),
                )
            )
        } else {
            AuthResult.InvalidCredentials(INVALID_MESSAGE)
        }
    }

    override suspend fun currentUser(): UserSession? = withContext(Dispatchers.IO) {
        val id = prefs.getString(KEY_USER_ID, null) ?: return@withContext null
        val role = prefs.getString(KEY_USER_ROLE, null) ?: return@withContext null
        val teacher = teacherDao.findById(id) ?: return@withContext null
        if (!teacher.active) return@withContext null
        UserSession(
            teacherId = teacher.id,
            name = teacher.name,
            username = teacher.username,
            role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.NORMAL_TEACHER),
        )
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    /** Used by the seeder to register demo credentials without storing plaintext. */
    suspend fun registerCredential(teacherId: String, password: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(hashKey(teacherId), hash(password, saltFor(teacherId))).apply()
    }

    suspend fun removeCredential(teacherId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(hashKey(teacherId)).apply()
    }

    private fun saveSession(id: String, name: String, username: String, role: String) {
        prefs.edit()
            .putString(KEY_USER_ID, id)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_USERNAME, username)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    private fun saltFor(teacherId: String): String = "grt:$teacherId"

    private fun hashKey(teacherId: String): String = "pwd_$teacherId"

    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + password).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_USERNAME = "user_username"
        private const val KEY_USER_ROLE = "user_role"
        private const val INVALID_MESSAGE = "Invalid username or password. Please try again."
    }
}
