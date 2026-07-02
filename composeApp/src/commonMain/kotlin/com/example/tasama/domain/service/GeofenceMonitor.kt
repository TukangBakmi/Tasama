package com.example.tasama.domain.service

import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.PlaceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

class GeofenceMonitor(
    private val authRepository: AuthRepository,
    private val placeRepository: PlaceRepository,
    private val scope: CoroutineScope
) {
    private val userStates = mutableMapOf<String, MutableMap<String, Boolean>>() // userId -> {placeId -> isInside}
    private var monitoringJob: Job? = null

    fun startMonitoring() {
        if (monitoringJob != null) return
        monitoringJob = scope.launch {
            authRepository.userId.filterNotNull().collectLatest { currentUserId ->
                monitorLocalUserOnly(currentUserId)
            }
        }
    }

    private suspend fun monitorLocalUserOnly(currentUserId: String) {
        authRepository.getUserFlow(currentUserId).collectLatest { me ->
            val partnerId = me?.partnerId ?: return@collectLatest
            
            // Only monitor if we have a partner
            val myPlacesFlow = placeRepository.getPlaces(currentUserId)
            val partnerPlacesFlow = placeRepository.getPlaces(partnerId)

            combine(myPlacesFlow, partnerPlacesFlow) { myPlaces, pPlaces ->
                (myPlaces + pPlaces).distinctBy { it.id }
            }.collect { allPlaces ->
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
            val isInside = distance <= place.radius
            val wasInside = currentState[place.id] ?: false
            
            if (isInside && !wasInside) {
                // Entered
                currentState[place.id] = true
                onTransition(user, place, true)
            } else if (!isInside && wasInside) {
                // Left with jitter protection
                if (distance > place.radius + 50) { // 50m buffer
                    currentState[place.id] = false
                    onTransition(user, place, false)
                }
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
            authRepository.sendNotification(
                targetUid = targetUserId,
                title = "Location Update",
                body = message
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
