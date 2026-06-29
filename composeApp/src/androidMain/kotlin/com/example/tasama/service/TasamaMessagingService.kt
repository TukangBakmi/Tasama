package com.example.tasama.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.example.tasama.domain.repository.ChatRepository
import kotlin.math.abs

class TasamaMessagingService : FirebaseMessagingService(), KoinComponent {

    private val authRepository: AuthRepository by inject()
    private val chatRepository: ChatRepository by inject()

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "chat_messages"
        const val REPLY_KEY = "reply_key"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val uid = authRepository.getCurrentUserId() ?: return

        scope.launch {
            runCatching {
                authRepository.updateFcmToken(uid, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data

        val senderName =
            data["sender_name"]
                ?: data["title"]
                ?: "Unknown"

        val senderId =
            data["senderId"]
                ?: senderName

        val body =
            data["body"]
                ?: message.notification?.body
                ?: return

        val chatId =
            data["channelId"]
                ?: return

        val messageId =
            data["messageId"]
                ?: return

        val senderPhoto =
            data["sender_photo"]

        val senderAvatar =
            data["sender_avatar"]

        scope.launch {
            showMessagingNotification(
                chatId = chatId,
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                body = body,
                senderPhoto = senderPhoto,
                senderAvatar = senderAvatar
            )
        }
    }

    private suspend fun showMessagingNotification(
        chatId: String,
        messageId: String,
        senderId: String,
        senderName: String,
        body: String,
        senderPhoto: String?,
        senderAvatar: String?
    ) {

        val manager =
            getSystemService(NOTIFICATION_SERVICE)
                    as NotificationManager

        ensureChannel(manager)

        val me = Person.Builder()
            .setName("You")
            .setKey(authRepository.getCurrentUserId())
            .build()

        val sender = Person.Builder()
            .setName(senderName)
            .setKey(senderId)
            .setIcon(
                resolvePersonIcon(
                    senderPhoto,
                    senderAvatar,
                    senderName
                )
            )
            .build()

        val style =
            manager.activeNotifications
                .firstOrNull {
                    it.id == chatId.hashCode()
                }
                ?.notification
                ?.let {
                    NotificationCompat.MessagingStyle
                        .extractMessagingStyleFromNotification(it)
                }
                ?: NotificationCompat.MessagingStyle(me)

        style.setGroupConversation(false)

        style.addMessage(
            NotificationCompat.MessagingStyle.Message(
                body,
                System.currentTimeMillis(),
                sender
            )
        )

        createConversationShortcut(
            chatId,
            senderName,
            sender
        )

        postNotification(
            manager = manager,
            notificationId = chatId.hashCode(),
            chatId = chatId,
            sender = sender,
            style = style
        )

        chatRepository.markMessageAsDelivered(
            chatId,
            messageId
        )
    }

    private fun postNotification(
        manager: NotificationManager,
        notificationId: Int,
        chatId: String,
        sender: Person,
        style: NotificationCompat.MessagingStyle
    ) {

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("channelId", chatId)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setStyle(style)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShortcutId(chatId)
            .addPerson(sender)
            .addAction(buildReplyAction(chatId, notificationId))
            .addAction(buildMarkReadAction(chatId, notificationId))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setLocusId(LocusIdCompat(chatId))
        }

        manager.notify(notificationId, builder.build())
    }

    private fun createConversationShortcut(
        chatId: String,
        senderName: String,
        sender: Person
    ) {

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("channelId", chatId)
        }

        val shortcut = ShortcutInfoCompat.Builder(this, chatId)
            .setShortLabel(senderName)
            .setLongLived(true)
            .setIntent(intent)
            .setPerson(sender)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(
            this,
            shortcut
        )
    }

    private fun buildReplyAction(
        chatId: String,
        notificationId: Int
    ): NotificationCompat.Action {

        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel("Reply")
            .build()

        val replyIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            Intent(this, NotificationReplyReceiver::class.java).apply {
                putExtra("channelId", chatId)
                putExtra("notifId", notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Action.Builder(
            0,
            "Reply",
            replyIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun buildMarkReadAction(
        chatId: String,
        notificationId: Int
    ): NotificationCompat.Action {

        val intent = PendingIntent.getBroadcast(
            this,
            notificationId + 100,
            Intent(this, NotificationMarkReadReceiver::class.java).apply {
                putExtra("channelId", chatId)
                putExtra("notifId", notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Action.Builder(
            0,
            "Mark as read",
            intent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()
    }

    private fun ensureChannel(manager: NotificationManager) {

        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Chat Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Tasama Chat Notifications"

            enableLights(true)
            enableVibration(true)
            setShowBadge(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setConversationId(CHANNEL_ID, CHANNEL_ID)
            }
        }

        manager.createNotificationChannel(channel)
    }

    private suspend fun resolvePersonIcon(
        photoUrl: String?,
        avatar: String?,
        senderName: String
    ): IconCompat {

        photoUrl?.let {
            loadBitmap(it)?.let { bitmap ->
                return IconCompat.createWithBitmap(bitmap)
            }
        }

        avatar?.let {
            val resId = resources.getIdentifier(
                it.lowercase().replace("_", ""),
                "drawable",
                packageName
            )

            if (resId != 0) {
                return IconCompat.createWithResource(this, resId)
            }
        }

        // Fallback ke inisial
        return IconCompat.createWithBitmap(createInitialAvatar(senderName))
    }

    private fun createInitialAvatar(name: String): Bitmap {

        val size = 128

        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Warna dibuat konsisten berdasarkan nama
        val colors = listOf(
            "#F44336".toColorInt(),
            "#E91E63".toColorInt(),
            "#9C27B0".toColorInt(),
            "#3F51B5".toColorInt(),
            "#2196F3".toColorInt(),
            "#009688".toColorInt(),
            "#4CAF50".toColorInt(),
            "#FF9800".toColorInt()
        )

        paint.color = colors[abs(name.hashCode()) % colors.size]

        canvas.drawCircle(
            size / 2f,
            size / 2f,
            size / 2f,
            paint
        )

        val initial = name
            .trim()
            .firstOrNull()
            ?.uppercase()
            ?: "?"

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.45f
        paint.typeface = Typeface.DEFAULT_BOLD

        val y = size / 2f - (paint.descent() + paint.ascent()) / 2

        canvas.drawText(
            initial,
            size / 2f,
            y,
            paint
        )

        return bitmap
    }

    private suspend fun loadBitmap(
        url: String
    ): Bitmap? = withContext(Dispatchers.IO) {

        runCatching {

            val loader = ImageLoader(this@TasamaMessagingService)

            val request = ImageRequest.Builder(this@TasamaMessagingService)
                .data(url)
                .allowHardware(false)
                .build()

            val result = loader.execute(request)

            if (result is SuccessResult) {

                return@withContext result.image
                    .asDrawable(resources)
                    .let { drawable ->
                        (drawable as BitmapDrawable).bitmap
                    }
            }

            null

        }.getOrElse {

            android.util.Log.e(
                "TasamaFCM",
                "Failed loading avatar",
                it
            )

            null
        }
    }
}