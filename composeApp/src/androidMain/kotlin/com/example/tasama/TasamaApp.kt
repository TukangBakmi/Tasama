package com.example.tasama

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.tasama.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class TasamaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()

        // Initialize Google Maps with the latest renderer
        com.google.android.gms.maps.MapsInitializer.initialize(this, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST) {
            android.util.Log.d("MapsInitializer", "Maps SDK initialized with: $it")
        }

        initKoin {
            androidLogger()
            androidContext(this@TasamaApp)
        }
    }

    private fun createNotificationChannel() {
        val channelId = "chat_messages"
        val name = "Chat Messages"
        val descriptionText = "Notifications for new chat messages"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
