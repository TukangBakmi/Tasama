package com.example.tasama.domain.repository

import com.example.tasama.domain.model.Place
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    fun getPlaces(userId: String): Flow<List<Place>>
    suspend fun addPlace(userId: String, place: Place)
    suspend fun deletePlace(userId: String, placeId: String)
}
