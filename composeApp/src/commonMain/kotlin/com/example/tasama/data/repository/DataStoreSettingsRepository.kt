package com.example.tasama.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.model.BatteryMode
import com.example.tasama.domain.model.DefaultRouteType
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
        
        val PARTNER_MAP_ENABLED = booleanPreferencesKey("partner_map_enabled")
        val BATTERY_MODE = stringPreferencesKey("battery_mode")
        val SMART_FOLLOW_ENABLED = booleanPreferencesKey("smart_follow_enabled")
        val LIVE_ETA_ENABLED = booleanPreferencesKey("live_eta_enabled")
        val WEATHER_WIDGET_ENABLED = booleanPreferencesKey("weather_widget_enabled")
        val DASHBOARD_ENABLED = booleanPreferencesKey("dashboard_enabled")
        val PLACES_ENABLED = booleanPreferencesKey("places_enabled")
        val REMINDER_NOTIFICATIONS_ENABLED = booleanPreferencesKey("reminder_notifications_enabled")
        val STORY_MARKERS_ENABLED = booleanPreferencesKey("story_markers_enabled")
        val REMINDER_MARKERS_ENABLED = booleanPreferencesKey("reminder_markers_enabled")
        val TRAFFIC_LAYER_ENABLED = booleanPreferencesKey("traffic_layer_enabled")
        val MAP_DARK_THEME_ENABLED = booleanPreferencesKey("map_dark_theme_enabled")
        val DEFAULT_ROUTE_TYPE = stringPreferencesKey("default_route_type")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            theme = AppTheme.valueOf(preferences[PreferencesKeys.THEME] ?: AppTheme.SYSTEM.name),
            currency = preferences[PreferencesKeys.CURRENCY] ?: "IDR",
            notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS] ?: true,
            partnerMapEnabled = preferences[PreferencesKeys.PARTNER_MAP_ENABLED] ?: true,
            batteryMode = BatteryMode.valueOf(preferences[PreferencesKeys.BATTERY_MODE] ?: BatteryMode.BALANCED.name),
            smartFollowEnabled = preferences[PreferencesKeys.SMART_FOLLOW_ENABLED] ?: true,
            liveEtaEnabled = preferences[PreferencesKeys.LIVE_ETA_ENABLED] ?: true,
            weatherWidgetEnabled = preferences[PreferencesKeys.WEATHER_WIDGET_ENABLED] ?: true,
            dashboardEnabled = preferences[PreferencesKeys.DASHBOARD_ENABLED] ?: true,
            placesEnabled = preferences[PreferencesKeys.PLACES_ENABLED] ?: true,
            reminderNotificationsEnabled = preferences[PreferencesKeys.REMINDER_NOTIFICATIONS_ENABLED] ?: true,
            storyMarkersEnabled = preferences[PreferencesKeys.STORY_MARKERS_ENABLED] ?: true,
            reminderMarkersEnabled = preferences[PreferencesKeys.REMINDER_MARKERS_ENABLED] ?: true,
            trafficLayerEnabled = preferences[PreferencesKeys.TRAFFIC_LAYER_ENABLED] ?: false,
            mapDarkThemeEnabled = preferences[PreferencesKeys.MAP_DARK_THEME_ENABLED] ?: false,
            defaultRouteType = DefaultRouteType.valueOf(preferences[PreferencesKeys.DEFAULT_ROUTE_TYPE] ?: DefaultRouteType.CAR.name)
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

    override suspend fun updatePartnerMapEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.PARTNER_MAP_ENABLED] = enabled }
    }

    override suspend fun updateBatteryMode(mode: BatteryMode) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.BATTERY_MODE] = mode.name }
    }

    override suspend fun updateSmartFollowEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.SMART_FOLLOW_ENABLED] = enabled }
    }

    override suspend fun updateLiveEtaEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LIVE_ETA_ENABLED] = enabled }
    }

    override suspend fun updateWeatherWidgetEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.WEATHER_WIDGET_ENABLED] = enabled }
    }

    override suspend fun updateDashboardEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.DASHBOARD_ENABLED] = enabled }
    }

    override suspend fun updatePlacesEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.PLACES_ENABLED] = enabled }
    }

    override suspend fun updateReminderNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.REMINDER_NOTIFICATIONS_ENABLED] = enabled }
    }

    override suspend fun updateStoryMarkersEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.STORY_MARKERS_ENABLED] = enabled }
    }

    override suspend fun updateReminderMarkersEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.REMINDER_MARKERS_ENABLED] = enabled }
    }

    override suspend fun updateTrafficLayerEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.TRAFFIC_LAYER_ENABLED] = enabled }
    }

    override suspend fun updateMapDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.MAP_DARK_THEME_ENABLED] = enabled }
    }

    override suspend fun updateDefaultRouteType(type: DefaultRouteType) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.DEFAULT_ROUTE_TYPE] = type.name }
    }
}
