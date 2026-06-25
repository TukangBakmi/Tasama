package com.example.tasama.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.tasama.MainActivity
import com.example.tasama.domain.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TasamaMessagingService : FirebaseMessagingService(), KoinComponent {
    private val authRepository: AuthRepository by lazy { getKoin().get() }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d("TasamaFCM", "New token generated: $token")
        val uid = authRepository.getCurrentUserId()
        if (uid != null) {
            serviceScope.launch {
                try {
                    authRepository.updateFcmToken(uid, token)
                    android.util.Log.d("TasamaFCM", "Token updated for user: $uid")
                } catch (e: Exception) {
                    android.util.Log.e("TasamaFCM", "Failed to update token", e)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        android.util.Log.d("TasamaFCM", "Message received from: ${message.from}")
        android.util.Log.d("TasamaFCM", "Notification Payload: ${message.notification?.body}")
        android.util.Log.d("TasamaFCM", "Data Payload: ${message.data}")
        
        // Check if message contains a notification payload
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        
        if (title != null && body != null) {
            android.util.Log.d("TasamaFCM", "Showing notification: $title - $body")
            showNotification(title, body)
        } else {
            android.util.Log.w("TasamaFCM", "Message received but title or body is null")
        }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "chat_messages"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Chat Messages",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
