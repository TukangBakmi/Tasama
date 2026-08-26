package com.example.tasama.data.repository

import com.example.tasama.domain.repository.PresenceRepository
import com.example.tasama.domain.repository.PresenceState
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class FirebasePresenceRepository : PresenceRepository {
    private val database = Firebase.database
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Serializable
    private data class PresenceRaw(
        val state: String? = null,
        val lastSeen: Long? = null
    )

    override fun startMonitoring(uid: String) {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            val presenceRef = database.reference("presence/$uid")
            val connectedRef = database.reference(".info/connected")

            connectedRef.valueEvents.collect { snapshot ->
                val connected: Boolean = snapshot.value()
                if (connected) {
                    // Set onDisconnect for this connection
                    presenceRef.onDisconnect().updateChildren(
                        mapOf(
                            "state" to "offline",
                            "lastSeen" to ServerValue.TIMESTAMP
                        )
                    )

                    // Mark as online
                    presenceRef.updateChildren(
                        mapOf(
                            "state" to "online",
                            "lastSeen" to ServerValue.TIMESTAMP
                        )
                    )
                }
            }
        }
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    override fun getPresence(uid: String): Flow<PresenceState> {
        return database.reference("presence/$uid").valueEvents.map { snapshot ->
            val data = try {
                snapshot.value<PresenceRaw>()
            } catch (e: Exception) {
                null
            }

            if (data?.state == "online") {
                PresenceState.Online
            } else {
                PresenceState.Offline(data?.lastSeen ?: 0L)
            }
        }
    }
}
