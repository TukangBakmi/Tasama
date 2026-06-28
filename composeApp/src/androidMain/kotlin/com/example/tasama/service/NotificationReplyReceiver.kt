package com.example.tasama.service

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val reply = intent?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(TasamaMessagingService.REPLY_KEY)
            ?.toString() ?: return
        val chatId  = intent.getStringExtra("channelId") ?: return
        val notifId = intent.getIntExtra("notifId", 0)

        (context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.cancel(notifId)
    }
}