package com.example.tasama.data.repository

import com.example.tasama.domain.model.Place
import com.example.tasama.domain.repository.PlaceRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebasePlaceRepository : PlaceRepository {
    private val firestore = Firebase.firestore

    override fun getPlaces(userId: String): Flow<List<Place>> {
        return firestore.collection("users").document(userId).collection("places").snapshots.map { snapshot ->
            snapshot.documents.map { it.data() }
        }
    }

    override suspend fun addPlace(userId: String, place: Place) {
        val collection = firestore.collection("users").document(userId).collection("places")
        // Generate a random ID if GitLive doesn't provide an easy auto-id document()
        val id = (1..20).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
        val newPlace = place.copy(id = id, createdBy = userId)
        collection.document(id).set(newPlace)
    }

    override suspend fun deletePlace(userId: String, placeId: String) {
        firestore.collection("users").document(userId).collection("places").document(placeId).delete()
    }
}
