package com.example.tasama.data.repository

import com.example.tasama.domain.model.LiveLocation
import com.example.tasama.domain.repository.LiveLocationRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class FirebaseLiveLocationRepository : LiveLocationRepository {
    private val database = Firebase.database
    private val rootRef = database.reference("live_locations")

    override suspend fun updateLiveLocation(uid: String, location: LiveLocation) {
        try {
            println("LIVE_LOCATION_WRITE_START")
            println("userId = $uid")
            println("path = live_locations/$uid")
            println("latitude = ${location.latitude}")
            println("longitude = ${location.longitude}")
            
            rootRef.child(uid).setValue(location)
            
            println("LIVE_LOCATION_WRITE_SUCCESS")
        } catch (e: Exception) {
            println("LIVE_LOCATION_WRITE_FAILED")
            println("exception = ${e.message}")
            e.printStackTrace()
        }
    }

    override suspend fun removeLiveLocation(uid: String) {
        try {
            rootRef.child(uid).removeValue()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getLiveLocation(uid: String): Flow<LiveLocation?> {
        return rootRef.child(uid).valueEvents.map { snapshot ->
            try {
                snapshot.value<LiveLocation>()
            } catch (e: Exception) {
                null
            }
        }.catch { e ->
            println("Error listening to live location for $uid: ${e.message}")
            emit(null)
        }
    }
}
