package com.example.tasama.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.example.tasama.MainActivity
import com.example.tasama.domain.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val channelIdData = message.data["channelId"]
        val senderPhoto = message.data["sender_photo"]
        val senderAvatar = message.data["sender_avatar"]
        
        if (title != null && body != null) {
            android.util.Log.d("TasamaFCM", "Showing notification: $title - $body, channelId: $channelIdData")
            serviceScope.launch {
                showNotification(title, body, senderName, channelIdData, senderPhoto, senderAvatar)
            }
        } else {
            android.util.Log.w("TasamaFCM", "Message received but title/body is missing. Data: ${message.data}")
        }
    }

    private suspend fun showNotification(
        title: String,
        body: String,
        senderName: String?,
        channelIdData: String?,
        senderPhoto: String?,
        senderAvatar: String?
    ) {
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
            if (channelIdData != null) {
                putExtra("channelId", channelIdData)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Load profile icon
        val icon = getPersonIcon(senderPhoto, senderAvatar)

        // MessagingStyle setup
        val user = androidx.core.app.Person.Builder().setName("You").build()
        val sender = androidx.core.app.Person.Builder()
            .setName(senderName ?: title)
            .setIcon(icon)
            .build()
        val messagingStyle = NotificationCompat.MessagingStyle(user)
            .addMessage(body, System.currentTimeMillis(), sender)
            .setConversationTitle(if (senderName != null && senderName != title) title else null)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        if (senderPhoto != null && senderPhoto.isNotEmpty()) {
            val bitmap = loadBitmap(senderPhoto)
            if (bitmap != null) {
                notificationBuilder.setLargeIcon(bitmap)
            }
        }

        val notificationId = senderName?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private suspend fun getPersonIcon(photoUrl: String?, avatar: String?): IconCompat? {
        if (photoUrl != null && photoUrl.isNotEmpty()) {
            val bitmap = loadBitmap(photoUrl)
            if (bitmap != null) {
                return IconCompat.createWithAdaptiveBitmap(bitmap)
            }
        }

        if (avatar != null && avatar.isNotEmpty()) {
            // Map avatar_1 to R.drawable.avatar1
            val resName = avatar.lowercase().replace("_", "")
            val resId = resources.getIdentifier(
                resName,
                "drawable",
                packageName
            )
            if (resId != 0) {
                return IconCompat.createWithResource(this, resId)
            }
        }

        return null
    }

    private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(this@TasamaMessagingService)

            val request = ImageRequest.Builder(this@TasamaMessagingService)
                .data(url)
                .allowHardware(false)
                .build()

            val result = loader.execute(request)

            when (result) {
                is SuccessResult -> result.image.asDrawable(resources).let { drawable ->
                    (drawable as? BitmapDrawable)?.bitmap
                }
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("TasamaFCM", "Failed to load bitmap", e)
            null
        }
    }
}
