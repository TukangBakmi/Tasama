package com.example.tasama.presentation.login

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val isRegister: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)
