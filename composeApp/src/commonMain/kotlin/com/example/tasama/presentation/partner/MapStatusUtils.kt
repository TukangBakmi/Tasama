package com.example.tasama.presentation.partner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.tasama.domain.model.User

enum class ConnectionStatus {
    LIVE, WEAK, OFFLINE
}

@Composable
fun rememberPartnerStatus(user: User?, now: Long): ConnectionStatus {
    if (user == null) return ConnectionStatus.OFFLINE
    
    return remember(user.lastLocationUpdate, user.accuracy, now) {
        val lastUpdate = user.lastLocationUpdate ?: 0L
        val delayMs = now - lastUpdate
        val accuracy = user.accuracy ?: 0f

        when {
            delayMs < 60_000 && accuracy < 100 -> ConnectionStatus.LIVE
            delayMs < 300_000 -> ConnectionStatus.WEAK
            else -> ConnectionStatus.OFFLINE
        }
    }
}

fun formatLastUpdated(lastUpdate: Long?, now: Long): String {
    if (lastUpdate == null) return "never"
    val diffSec = (now - lastUpdate) / 1000
    return when {
        diffSec < 60 -> "just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        else -> "${diffSec / 86400}d ago"
    }
}
