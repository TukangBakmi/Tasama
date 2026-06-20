package com.example.tasama.domain.repository

import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateTheme(theme: AppTheme)
    suspend fun updateCurrency(currency: String)
    suspend fun updateNotifications(enabled: Boolean)
}
