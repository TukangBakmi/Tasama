package com.example.tasama.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationMarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val notifId = intent?.getIntExtra("notifId", 0) ?: return
        (context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.cancel(notifId)
    }
}