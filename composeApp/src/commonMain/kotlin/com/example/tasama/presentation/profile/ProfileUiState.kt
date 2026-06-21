package com.example.tasama.presentation.profile

import com.example.tasama.domain.model.AppTheme

data class ProfileUiState(
    val userName: String = "",
    val userEmail: String = "",
    val userId: String = "",
    val userShortId: String = "",
    val profilePictureUrl: String? = null,
    val partnerId: String? = null,
    val partnerName: String? = null,
    val currency: String = "USD",
    val theme: AppTheme = AppTheme.SYSTEM,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isGuest: Boolean = false
)
