package com.example.tasama.presentation.profile

data class ProfileUiState(
    val userName: String = "John Doe",
    val userEmail: String = "john.doe@example.com",
    val partnerName: String = "Jane Doe",
    val currency: String = "USD",
    val isExporting: Boolean = false
)
