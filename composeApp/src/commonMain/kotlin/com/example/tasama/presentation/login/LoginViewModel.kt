package com.example.tasama.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun toggleMode() {
        _uiState.update { 
            it.copy(
                isRegister = !it.isRegister, 
                error = null,
                email = "",
                password = "",
                name = ""
            ) 
        }
    }

    fun login() {
        val email = _uiState.value.email
        val password = _uiState.value.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.signIn(email, password)
                _uiState.update { 
                    it.copy(
                        isLoggedIn = true, 
                        isLoading = false,
                        email = "",
                        password = "",
                        name = ""
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Login failed", isLoading = false) }
            }
        }
    }

    fun register() {
        val email = _uiState.value.email
        val password = _uiState.value.password
        val name = _uiState.value.name

        if (email.isBlank() || password.isBlank() || name.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.signUp(email, password, name)
                _uiState.update { 
                    it.copy(
                        isLoggedIn = true, 
                        isLoading = false,
                        email = "",
                        password = "",
                        name = ""
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Registration failed", isLoading = false) }
            }
        }
    }

    fun onGoogleSignInSuccess(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.signInWithGoogle(idToken)
                _uiState.update { 
                    it.copy(
                        isLoggedIn = true, 
                        isLoading = false,
                        email = "",
                        password = "",
                        name = ""
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Google Sign-In failed", isLoading = false) }
            }
        }
    }

    fun loginAnonymously() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.signInAnonymously()
                _uiState.update { 
                    it.copy(
                        isLoggedIn = true, 
                        isLoading = false,
                        email = "",
                        password = "",
                        name = ""
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Guest login failed", isLoading = false) }
            }
        }
    }

    fun resetPassword() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your email to reset password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authRepository.sendPasswordResetEmail(email)
                _uiState.update { it.copy(error = "Password reset email sent!", isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to send reset email", isLoading = false) }
            }
        }
    }
}
