package com.example.tasama.domain.model

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val currency: String = "IDR",
    val notificationsEnabled: Boolean = true
)
