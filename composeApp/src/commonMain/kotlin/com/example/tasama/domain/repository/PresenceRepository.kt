package com.example.tasama.domain.repository

import kotlinx.coroutines.flow.Flow

sealed class PresenceState {
    data object Online : PresenceState()
    data class Offline(val lastSeen: Long) : PresenceState()
}

interface PresenceRepository {
    /**
     * Start monitoring the current user's connection status and updating their presence.
     */
    fun startMonitoring(uid: String)

    /**
     * Set the foreground status of the app for the current user.
     */
    fun setForeground(uid: String, isForeground: Boolean)

    /**
     * Stop monitoring and clean up resources.
     */
    fun stopMonitoring()

    /**
     * Observe the presence state of a specific user.
     */
    fun getPresence(uid: String): Flow<PresenceState>
}
