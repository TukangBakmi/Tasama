package com.example.tasama.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME = stringPreferencesKey("theme")
        val CURRENCY = stringPreferencesKey("currency")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            theme = AppTheme.valueOf(preferences[PreferencesKeys.THEME] ?: AppTheme.SYSTEM.name),
            currency = preferences[PreferencesKeys.CURRENCY] ?: "IDR",
            notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS] ?: true
        )
    }

    override suspend fun updateTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme.name
        }
    }

    override suspend fun updateCurrency(currency: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY] = currency
        }
    }

    override suspend fun updateNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS] = enabled
        }
    }
}
