package com.example.examhallallocation.data.repository

import com.example.examhallallocation.domain.model.UserSession

/** Authentication outcome. */
sealed interface AuthResult {
    data class Success(val session: UserSession) : AuthResult
    data class InvalidCredentials(val message: String) : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Authentication repository. Two implementations exist:
 * - DemoAuthRepository: local, used when the app ships without Firebase config.
 * - FirebaseAuthRepository: active automatically when google-services.json is present.
 * Role always comes from the account record, never from UI selection.
 */
interface AuthRepository {
    suspend fun login(username: String, password: String): AuthResult
    suspend fun currentUser(): UserSession?
    suspend fun logout()
    val isDemoMode: Boolean
}
