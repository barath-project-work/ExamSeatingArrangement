package com.example.examhallallocation.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.examhallallocation.data.repository.AuthRepository
import com.example.examhallallocation.data.repository.AuthResult
import com.example.examhallallocation.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val databaseSeeder: com.example.examhallallocation.data.seed.DatabaseSeeder,
) : ViewModel() {

    sealed interface LoginUiState {
        data object Idle : LoginUiState
        data object Loading : LoginUiState
        data class Error(val message: String) : LoginUiState
        data class Authenticated(val role: UserRole) : LoginUiState
    }

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    val isDemoMode: Boolean get() = authRepository.isDemoMode

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState.Error("Please enter both username and password")
            return
        }
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.login(username, password)) {
                is AuthResult.Success -> {
                    // Synchronize with Cloud Firestore so a fresh device instantly gets all data
                    runCatching { databaseSeeder.hydrateFromCloud() }
                    _state.value = LoginUiState.Authenticated(result.session.role)
                }
                is AuthResult.InvalidCredentials -> _state.value = LoginUiState.Error(result.message)
                is AuthResult.Error -> _state.value = LoginUiState.Error(result.message)
            }
        }
    }

    fun consumeError() {
        if (_state.value is LoginUiState.Error) _state.value = LoginUiState.Idle
    }
}
