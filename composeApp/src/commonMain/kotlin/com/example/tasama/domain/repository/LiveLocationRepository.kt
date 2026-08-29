package com.example.tasama.domain.repository

import com.example.tasama.domain.model.LiveLocation
import kotlinx.coroutines.flow.Flow

interface LiveLocationRepository {
    suspend fun updateLiveLocation(uid: String, location: LiveLocation)
    suspend fun removeLiveLocation(uid: String)
    fun getLiveLocation(uid: String): Flow<LiveLocation?>
}
