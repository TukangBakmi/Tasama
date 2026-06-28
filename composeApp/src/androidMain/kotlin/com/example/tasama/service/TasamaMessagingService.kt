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
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.example.tasama.MainActivity
import com.example.tasama.R
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val REPLY_KEY = "reply_key"
        const val NOTIF_CHANNEL_ID = "chat_messages"
    }

    // ─── Token refresh ────────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d("TasamaFCM", "New token: $token")
        val uid = authRepository.getCurrentUserId() ?: return
        serviceScope.launch {
            runCatching { authRepository.updateFcmToken(uid, token) }
                .onFailure { android.util.Log.e("TasamaFCM", "Token update failed", it) }
        }
    }

    // ─── Incoming message ─────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        android.util.Log.d("TasamaFCM", "=== onMessageReceived CALLED ===")

        val data = message.data
        val senderName   = data["sender_name"] ?: data["title"] ?: "Someone"
        val body         = data["body"] ?: message.notification?.body ?: return
        val chatId       = data["channelId"] ?: run {
            android.util.Log.e("TasamaFCM", "❌ channelId missing")
            return
        }
        val senderId     = data["senderId"] ?: senderName
        val senderPhoto  = data["sender_photo"]?.takeIf { it.isNotEmpty() }
        val senderAvatar = data["sender_avatar"]?.takeIf { it.isNotEmpty() }

        // Show notification directly on IO — no coroutine delay
        serviceScope.launch(Dispatchers.IO) {
            showMessagingNotification(
                chatId       = chatId,
                senderId     = senderId,
                senderName   = senderName,
                body         = body,
                senderPhoto  = senderPhoto,
                senderAvatar = senderAvatar,
            )
        }
    }

    // ─── Core notification builder ────────────────────────────────────────────

    private suspend fun showMessagingNotification(
        chatId: String,
        senderId: String,
        senderName: String,
        body: String,
        senderPhoto: String?,
        senderAvatar: String?,
    ) {
        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notifManager)

        val notifId = chatId.hashCode()
        val me = Person.Builder().setName("You").build()
        val sender = Person.Builder()
            .setName(senderName)
            .setKey(senderId)
            // No icon yet — post immediately first
            .build()

        val style = notifManager.activeNotifications
            .find { it.id == notifId }
            ?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(me)

        style.addMessage(
            NotificationCompat.MessagingStyle.Message(
                body,
                System.currentTimeMillis(),
                sender,
            )
        )

        // ── Post immediately without avatar ──────────────────────────────────────
        postNotification(notifManager, notifId, chatId, style, largeIcon = null)

        // ── Load avatar in background and update ─────────────────────────────────
        val bitmap = senderPhoto?.let { loadBitmap(it) }
        if (bitmap != null) {
            postNotification(notifManager, notifId, chatId, style, largeIcon = bitmap)
        }
    }

    private fun postNotification(
        notifManager: NotificationManager,
        notifId: Int,
        chatId: String,
        style: NotificationCompat.MessagingStyle,
        largeIcon: Bitmap?,
    ) {
        val contentIntent = PendingIntent.getActivity(
            this,
            notifId,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("channelId", chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(style)
            .setLargeIcon(largeIcon)
            .setContentIntent(contentIntent)
            .addAction(buildReplyAction(chatId, notifId))
            .addAction(buildMarkReadAction(chatId, notifId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(chatId)
            .setOnlyAlertOnce(true) // ← true so avatar update doesn't re-ring
            .build()

        notifManager.notify(notifId, notification)
    }

    // ─── Reply action ─────────────────────────────────────────────────────────

    private fun buildReplyAction(chatId: String, notifId: Int): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel("Reply")
            .build()

        val replyIntent = PendingIntent.getBroadcast(
            this,
            notifId,
            Intent(this, NotificationReplyReceiver::class.java).apply {
                putExtra("channelId", chatId)
                putExtra("notifId", notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        return NotificationCompat.Action.Builder(0, "Reply", replyIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    // ─── Mark as read action ──────────────────────────────────────────────────

    private fun buildMarkReadAction(chatId: String, notifId: Int): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            this,
            notifId + 1,
            Intent(this, NotificationMarkReadReceiver::class.java).apply {
                putExtra("channelId", chatId)
                putExtra("notifId", notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Action.Builder(0, "Mark as read", intent).build()
    }

    // ─── Channel setup ────────────────────────────────────────────────────────

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(NOTIF_CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications for new chat messages"
                enableVibration(true)
                setShowBadge(true)
            }
        )
    }

    // ─── Avatar helpers ───────────────────────────────────────────────────────

    private suspend fun resolvePersonIcon(photoUrl: String?, avatar: String?): IconCompat? {
        photoUrl?.let { url ->
            loadBitmap(url)?.let { return IconCompat.createWithAdaptiveBitmap(it) }
        }
        avatar?.let { name ->
            val resName = name.lowercase().replace("_", "")
            val resId = resources.getIdentifier(resName, "drawable", packageName)
            if (resId != 0) return IconCompat.createWithResource(this, resId)
        }
        return null
    }

    private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val result = ImageLoader(this@TasamaMessagingService).execute(
                ImageRequest.Builder(this@TasamaMessagingService)
                    .data(url)
                    .allowHardware(false)
                    .build()
            )
            (result as? SuccessResult)
                ?.image
                ?.asDrawable(resources)
                ?.let { it as? BitmapDrawable }
                ?.bitmap
        }.getOrElse {
            android.util.Log.e("TasamaFCM", "Bitmap load failed: $url", it)
            null
        }
    }
}