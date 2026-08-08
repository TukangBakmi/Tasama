package com.example.tasama.domain.service

class NoOpNotificationService : NotificationService {
    override fun clearNotifications(channelId: String) {
        // No-op
    }
}
