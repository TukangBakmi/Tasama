package com.example.tasama.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.tasama.MainActivity
import com.example.tasama.domain.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TasamaMessagingService : FirebaseMessagingService(), KoinComponent {
    private val authRepository: AuthRepository by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d("TasamaFCM", "New token generated: $token")
        val uid = authRepository.getCurrentUserId()
        if (uid != null) {
            serviceScope.launch {
                try {
                    authRepository.updateFcmToken(uid, token)
                } catch (e: Exception) {
                    android.util.Log.e("TasamaFCM", "Failed to update token", e)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        android.util.Log.d("TasamaFCM", "Message received from: ${message.from}")
        
        // Extract data from both notification and data payloads
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        val senderName = message.data["sender_name"] ?: title // Fallback to title
        
        if (title != null && body != null) {
            android.util.Log.d("TasamaFCM", "Showing notification: $title - $body")
            showNotification(title, body, senderName)
        } else {
            android.util.Log.w("TasamaFCM", "Message received but title/body is missing. Data: ${message.data}")
        }
    }

    private fun showNotification(title: String, body: String, senderName: String?) {
        val channelId = "chat_messages"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // MessagingStyle setup
        val user = androidx.core.app.Person.Builder().setName("You").build()
        val sender = androidx.core.app.Person.Builder().setName(senderName ?: title).build()
        val messagingStyle = NotificationCompat.MessagingStyle(user)
            .addMessage(body, System.currentTimeMillis(), sender)
            .setConversationTitle(if (senderName != null && senderName != title) title else null)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email) // Reliable system icon
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        val notificationId = senderName?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
