package com.example.tasama.domain.model

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

enum class BatteryMode {
    PERFORMANCE, BALANCED, BATTERY_SAVER
}

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val currency: String = "IDR",
    val notificationsEnabled: Boolean = true,
    
    // Partner Map Settings
    val partnerMapEnabled: Boolean = true,
    val batteryMode: BatteryMode = BatteryMode.BALANCED,
    val smartFollowEnabled: Boolean = true,
    val weatherWidgetEnabled: Boolean = true,
    val dashboardEnabled: Boolean = true,
    val placesEnabled: Boolean = true,
    val reminderNotificationsEnabled: Boolean = true,
    val reminderMarkersEnabled: Boolean = true,
    val trafficLayerEnabled: Boolean = false,
    val mapDarkThemeEnabled: Boolean = false,

    // AI Undo persistent state
    val undoTransactionId: String? = null,
    val undoSpaceId: String? = null,
    val undoMessageId: String? = null,
    val undoCreatedAt: Long? = null
)
