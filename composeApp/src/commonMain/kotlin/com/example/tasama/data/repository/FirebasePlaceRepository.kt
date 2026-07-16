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
            snapshot.documents.map { doc ->
                val place: Place = doc.data()
                place.copy(id = doc.id)
            }
        }
    }

    override suspend fun addPlace(userId: String, place: Place) {
        val collection = firestore.collection("users").document(userId).collection("places")
        
        val id = if (place.id.isNotBlank()) {
            place.id
        } else {
            // Generate a random ID if none exists
            (1..20).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
        }

        val newPlace = place.copy(id = id, createdBy = userId)
        collection.document(id).set(newPlace)
    }

    override suspend fun deletePlace(userId: String, placeId: String) {
        firestore.collection("users").document(userId).collection("places").document(placeId).delete()
    }

    override suspend fun deleteAllPlaces(userId: String) {
        val collection = firestore.collection("users").document(userId).collection("places")
        val snapshot = collection.get()
        snapshot.documents.forEach { doc ->
            doc.reference.delete()
        }
    }
}
