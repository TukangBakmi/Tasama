package com.example.tasama.data.repository

import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.PresenceRepository
import com.example.tasama.domain.repository.PresenceState
import com.example.tasama.domain.repository.SettingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ServerValue
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class FirebasePresenceRepository(
    private val authRepository: AuthRepository,
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
        println("DEBUG: [PRESENCE] startMonitoring for $uid")
        currentUid = uid
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            try {
                if (deviceId == null) {
                    deviceId = settingsRepository.getDeviceId()
                }
                val id = deviceId ?: return@launch

                val connectionRef = database.reference("presence/$uid/devices/$id/connections/$connectionId")
                val connectedRef = database.reference(".info/connected")

                connectedRef.valueEvents.collect { snapshot ->
                    val connected: Boolean = snapshot.value()
                    println("DEBUG: [PRESENCE] Connection state changed: $connected")
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
            } catch (e: Exception) {
                if (e.message?.contains("permission", ignoreCase = true) == true) {
                    println("DEBUG: [PRESENCE] Permission denied in monitoring (expected during logout)")
                } else {
                    println("ERROR: [PRESENCE] Monitoring error: ${e.message}")
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
        cleanupInternal()
    }

    override suspend fun cleanup() {
        println("DEBUG: [LOGOUT] Cleaning up FirebasePresenceRepository")
        cleanupInternal()
    }

    private fun cleanupInternal() {
        println("DEBUG: [LOGOUT] Cancelling monitoring job and marking user offline")
        monitoringJob?.cancel()
        monitoringJob = null
        val uid = currentUid
        val id = deviceId
        if (uid != null && id != null) {
            // Use a separate scope or runBlocking if we need to ensure this finishes before return
            // But cleanup is suspend, so we can use the repository scope
            scope.launch {
                try {
                    println("DEBUG: [LOGOUT] Setting offline status in RTDB for $uid")
                    withTimeoutOrNull(2000) {
                        database.reference("presence/$uid/devices/$id/connections/$connectionId").setValue(
                            mapOf(
                                "state" to "offline",
                                "lastSeen" to ServerValue.TIMESTAMP
                            )
                        )
                    }
                    println("DEBUG: [LOGOUT] Offline status set successfully")
                } catch (e: Exception) {
                    println("DEBUG: [LOGOUT] Failed to set offline status (likely already signed out): ${e.message}")
                }
                currentUid = null
            }
        }
    }

    override fun getPresence(uid: String): Flow<PresenceState> {
        println("DEBUG: [PRESENCE] getPresence flow created for $uid")
        return authRepository.userId.flatMapLatest { currentUid ->
            if (currentUid == null) {
                flowOf(PresenceState.Offline(0L))
            } else {
                database.reference("presence/$uid/devices").valueEvents.map { snapshot ->
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
                }.catch { e ->
                    if (e.message?.contains("permission", ignoreCase = true) == true) {
                        println("DEBUG: [PRESENCE] Permission denied for getPresence($uid) - expected during logout")
                    } else {
                        println("ERROR: [PRESENCE] Error in getPresence($uid): ${e.message}")
                    }
                    emit(PresenceState.Offline(0L))
                }
            }
        }
    }
}
