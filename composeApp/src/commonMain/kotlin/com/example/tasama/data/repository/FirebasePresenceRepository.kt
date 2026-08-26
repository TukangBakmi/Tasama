package com.example.tasama.data.repository

import com.example.tasama.domain.repository.PresenceRepository
import com.example.tasama.domain.repository.PresenceState
import com.example.tasama.domain.repository.SettingsRepository
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
import kotlin.random.Random

class FirebasePresenceRepository(
    private val settingsRepository: SettingsRepository
) : PresenceRepository {
    private val database = Firebase.database
    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val connectionId = Random.nextLong().toString(16)
    private var isForeground = true
    private var currentUid: String? = null
    private var deviceId: String? = null

    @Serializable
    private data class DevicePresence(
        val connections: Map<String, ConnectionPresence>? = null
    )

    @Serializable
    private data class ConnectionPresence(
        val state: String? = null,
        val lastSeen: Long? = null
    )

    override fun startMonitoring(uid: String) {
        currentUid = uid
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            if (deviceId == null) {
                deviceId = settingsRepository.getDeviceId()
            }
            val id = deviceId ?: return@launch

            val connectionRef = database.reference("presence/$uid/devices/$id/connections/$connectionId")
            val connectedRef = database.reference(".info/connected")

            connectedRef.valueEvents.collect { snapshot ->
                val connected: Boolean = snapshot.value()
                if (connected) {
                    // Set onDisconnect for this connection
                    connectionRef.onDisconnect().setValue(
                        mapOf(
                            "state" to "offline",
                            "lastSeen" to ServerValue.TIMESTAMP
                        )
                    )

                    // Initial state depends on current foreground status
                    updatePresence()
                }
            }
        }
    }

    override fun setForeground(uid: String, isForeground: Boolean) {
        this.isForeground = isForeground
        if (currentUid == uid) {
            updatePresence()
        }
    }

    private fun updatePresence() {
        val uid = currentUid ?: return
        val id = deviceId ?: return
        scope.launch {
            val connectionRef = database.reference("presence/$uid/devices/$id/connections/$connectionId")
            connectionRef.setValue(
                mapOf(
                    "state" to if (isForeground) "online" else "offline",
                    "lastSeen" to ServerValue.TIMESTAMP
                )
            )
        }
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        val uid = currentUid
        val id = deviceId
        if (uid != null && id != null) {
            scope.launch {
                database.reference("presence/$uid/devices/$id/connections/$connectionId").setValue(
                    mapOf(
                        "state" to "offline",
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                )
                currentUid = null
            }
        }
    }

    override fun getPresence(uid: String): Flow<PresenceState> {
        return database.reference("presence/$uid/devices").valueEvents.map { snapshot ->
            val devices = try {
                snapshot.value<Map<String, DevicePresence>>()
            } catch (e: Exception) {
                null
            }

            val allConnections = devices?.values?.flatMap { it.connections?.values ?: emptyList() }
            
            val isOnline = allConnections?.any { it.state == "online" } ?: false
            if (isOnline) {
                PresenceState.Online
            } else {
                val lastSeen = allConnections?.maxOfOrNull { it.lastSeen ?: 0L } ?: 0L
                PresenceState.Offline(lastSeen)
            }
        }
    }
}
