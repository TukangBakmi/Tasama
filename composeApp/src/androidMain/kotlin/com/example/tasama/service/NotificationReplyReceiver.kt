package com.example.tasama.service

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.tasama.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationReplyReceiver : BroadcastReceiver(), KoinComponent {

    private val chatRepository: ChatRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        val reply = intent?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(TasamaMessagingService.REPLY_KEY)
            ?.toString() ?: return
        val chatId  = intent.getStringExtra("channelId") ?: return
        val notifId = intent.getIntExtra("notifId", 0)

        val pendingResult = goAsync()
        scope.launch {
            try {
                chatRepository.sendMessage(chatId, reply)
                chatRepository.markChannelAsRead(chatId)
            } finally {
                pendingResult.finish()
            }
        }

        (context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.cancel(notifId)
    }
}