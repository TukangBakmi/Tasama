package com.example.tasama

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import com.example.tasama.di.initKoin
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.domain.model.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import androidx.appcompat.app.AppCompatDelegate
import android.app.UiModeManager
import android.content.Context

class TasamaApp : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin {
            androidLogger()
            androidContext(this@TasamaApp)
        }

        // Apply theme as early as possible to avoid startup flash
        applyPersistedTheme()

        val intent = Intent("com.google.firebase.MESSAGING_EVENT")
        intent.setPackage(packageName)
        val resolved = packageManager.resolveService(intent, 0)
        android.util.Log.d("TasamaFCM", "FCM Service resolved: $resolved")
        
        createNotificationChannel()

        // Initialize Google Maps with the latest renderer
        com.google.android.gms.maps.MapsInitializer.initialize(this, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST) {
            android.util.Log.d("MapsInitializer", "Maps SDK initialized with: $it")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "chat_messages"
            val name = "Chat Messages"
            val descriptionText = "Notifications for new chat messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun applyPersistedTheme() {
        try {
            val settingsRepository: SettingsRepository = get()
            val theme = runBlocking { settingsRepository.settings.first().theme }
            
            val mode = when (theme) {
                AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                val applicationMode = when (theme) {
                    AppTheme.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    AppTheme.DARK -> UiModeManager.MODE_NIGHT_YES
                    AppTheme.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                }
                try {
                    val method = uiModeManager.javaClass.getMethod("setApplicationNightMode", Int::class.javaPrimitiveType)
                    method.invoke(uiModeManager, applicationMode)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.e("TasamaApp", "Failed to apply persisted theme", e)
        }
    }
}
