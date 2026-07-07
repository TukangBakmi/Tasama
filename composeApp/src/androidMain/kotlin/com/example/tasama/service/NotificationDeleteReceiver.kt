package com.example.tasama.service

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val groupKey = intent.getStringExtra("groupKey") ?: return

        val childCount = manager.activeNotifications.count {
            it.notification.group == groupKey &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
        }

        if (childCount <= 1) {
            manager.cancel(groupKey.hashCode())
        }
    }
}