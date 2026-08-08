package com.example.tasama.data.service

import android.app.NotificationManager
import android.content.Context
import com.example.tasama.domain.service.NotificationService

class AndroidNotificationService(private val context: Context) : NotificationService {
    override fun clearNotifications(channelId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // In TasamaMessagingService, we use chatId.hashCode() as the notification ID
        notificationManager.cancel(channelId.hashCode())
    }
}
