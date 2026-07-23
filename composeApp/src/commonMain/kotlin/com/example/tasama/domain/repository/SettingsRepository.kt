package com.example.tasama.domain.repository

import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.model.BatteryMode
import com.example.tasama.domain.model.DefaultRouteType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateTheme(theme: AppTheme)
    suspend fun updateCurrency(currency: String)
    suspend fun updateNotifications(enabled: Boolean)

    // Partner Map Settings
    suspend fun updatePartnerMapEnabled(enabled: Boolean)
    suspend fun updateBatteryMode(mode: BatteryMode)
    suspend fun updateSmartFollowEnabled(enabled: Boolean)
    suspend fun updateLiveEtaEnabled(enabled: Boolean)
    suspend fun updateWeatherWidgetEnabled(enabled: Boolean)
    suspend fun updateDashboardEnabled(enabled: Boolean)
    suspend fun updatePlacesEnabled(enabled: Boolean)
    suspend fun updateReminderNotificationsEnabled(enabled: Boolean)
    suspend fun updateStoryMarkersEnabled(enabled: Boolean)
    suspend fun updateReminderMarkersEnabled(enabled: Boolean)
    suspend fun updateTrafficLayerEnabled(enabled: Boolean)
    suspend fun updateMapDarkThemeEnabled(enabled: Boolean)
    suspend fun updateDefaultRouteType(type: DefaultRouteType)
}
