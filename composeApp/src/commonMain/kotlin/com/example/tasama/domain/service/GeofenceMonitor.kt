package com.example.tasama.domain.service

import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.PlaceRepository
import com.example.tasama.domain.repository.SessionCleanupRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

class GeofenceMonitor(
    private val authRepository: Lazy<AuthRepository>,
    private val placeRepository: PlaceRepository,
    private val scope: CoroutineScope
) : SessionCleanupRepository {
    private val userStates = mutableMapOf<String, MutableMap<String, Boolean>>() // userId -> {placeId -> isInside}
    private val consecutivePoints = mutableMapOf<String, MutableMap<String, Int>>() // userId_placeId -> count
    private var monitoringJob: Job? = null

    override suspend fun cleanup() {
        println("DEBUG: Cleaning up GeofenceMonitor - cancelling monitoring job")
        monitoringJob?.cancel()
        monitoringJob = null
        userStates.clear()
        consecutivePoints.clear()
    }

    fun startMonitoring() {
        if (monitoringJob != null) return
        monitoringJob = scope.launch {
            authRepository.value.userId.collectLatest { currentUserId ->
                if (currentUserId != null) {
                    monitorLocalUserOnly(currentUserId)
                }
            }
        }
    }

    private suspend fun monitorLocalUserOnly(currentUserId: String) {
        authRepository.value.getUserFlow(currentUserId).collectLatest { me ->
            if (me == null) return@collectLatest
            val partnerId = me.partnerId
            
            val relationshipId = if (partnerId != null) {
                listOf(currentUserId, partnerId).sorted().joinToString("_")
            } else {
                currentUserId
            }

            placeRepository.getPlaces(relationshipId).collect { allPlaces ->
                checkUser(me, allPlaces)
            }
        }
    }

    private fun checkUser(user: User, places: List<Place>) {
        val lat = user.latitude ?: return
        val lon = user.longitude ?: return
        val userId = user.id
        
        val currentState = userStates.getOrPut(userId) { mutableMapOf() }
        
        places.forEach { place ->
            val distance = calculateDistance(lat, lon, place.latitude, place.longitude)
            val wasInside = currentState[place.id] ?: false
            
            // Hysteresis: Entry buffer (15m inner) and Exit buffer (50m outer)
            // Entering: must be within (radius - 15m) OR just within radius if radius is small
            val entryThreshold = maxOf(place.radius - 15.0, place.radius * 0.8)
            val isInsideForEntry = distance <= entryThreshold
            
            // Exiting: must be outside (radius + 50m)
            val isOutsideForExit = distance > place.radius + 50.0

            val counts = consecutivePoints.getOrPut(userId) { mutableMapOf() }
            
            if (isInsideForEntry && !wasInside) {
                // Potential Entry: Verify 2 consecutive points to filter jumps
                val currentCount = (counts[place.id] ?: 0) + 1
                if (currentCount >= 2) {
                    currentState[place.id] = true
                    counts[place.id] = 0
                    if (place.notifyOnEntry) {
                        onTransition(user, place, true)
                    }
                } else {
                    counts[place.id] = currentCount
                }
            } else if (isOutsideForExit && wasInside) {
                // Potential Exit: Verify 2 consecutive points
                val currentCount = (counts[place.id] ?: 0) + 1
                if (currentCount >= 2) {
                    currentState[place.id] = false
                    counts[place.id] = 0
                    if (place.notifyOnExit) {
                        onTransition(user, place, false)
                    }
                } else {
                    counts[place.id] = currentCount
                }
            } else {
                // Reset count if user is in "dead zone" or state hasn't changed
                counts[place.id] = 0
            }
        }
    }

    private fun onTransition(user: User, place: Place, entered: Boolean) {
        val partnerId = user.partnerId ?: return
        val userName = user.name.ifEmpty { "Partner" }
        val placeName = place.name
        
        val message = if (entered) {
            "❤️ $userName arrived at $placeName"
        } else {
            "👋 $userName left $placeName"
        }
        
        sendPushNotification(partnerId, message)
    }

    private fun sendPushNotification(targetUserId: String, message: String) {
        scope.launch {
            authRepository.value.sendNotification(
                targetUid = targetUserId,
                title = "Place Update",
                body = message,
                type = "PLACE_ALERT"
            )
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2).pow(2) + cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
