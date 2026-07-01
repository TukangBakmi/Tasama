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

    fun startMonitoring() {
        scope.launch {
            authRepository.userId.filterNotNull().collectLatest { currentUserId ->
                monitorUserAndPartner(currentUserId)
            }
        }
    }

    private suspend fun monitorUserAndPartner(currentUserId: String) {
        authRepository.getUserFlow(currentUserId).collectLatest { user ->
            if (user == null) return@collectLatest
            
            val partnerId = user.partnerId
            
            // Collect places for both (assuming they share or we check both)
            val myPlacesFlow = placeRepository.getPlaces(currentUserId)
            val partnerPlacesFlow = partnerId?.let { placeRepository.getPlaces(it) } ?: flowOf(emptyList())
            
            combine(
                authRepository.getUserFlow(currentUserId),
                if (partnerId != null) authRepository.getUserFlow(partnerId) else flowOf(null),
                myPlacesFlow,
                partnerPlacesFlow
            ) { me, partner, myPlaces, pPlaces ->
                val allPlaces = (myPlaces + pPlaces).distinctBy { it.id }
                if (me != null) checkUser(me, allPlaces)
                if (partner != null) checkUser(partner, allPlaces)
            }.collect()
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
        // In a real app, this would call a backend API or update a 'notifications' collection in Firestore
        // which triggers a Cloud Function to send an FCM.
        println("Sending notification to $targetUserId: $message")
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
