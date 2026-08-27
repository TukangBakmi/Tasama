package com.example.tasama.domain.repository

import com.example.tasama.domain.model.Place
import kotlinx.coroutines.flow.Flow

interface PlaceRepository : SessionCleanupRepository {
    fun getPlaces(relationshipId: String): Flow<List<Place>>
    suspend fun addPlace(place: Place): Result<Unit>
    suspend fun deletePlace(placeId: String): Result<Unit>
    suspend fun deleteAllPlaces(relationshipId: String): Result<Unit>
}
