package com.example.tasama.data.repository

import com.example.tasama.domain.model.Place
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.PlaceRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class FirebasePlaceRepository(
    private val authRepository: AuthRepository
) : PlaceRepository {
    private val firestore = Firebase.firestore

    override suspend fun cleanup() {
        println("DEBUG: Cleaning up FirebasePlaceRepository")
    }

    override fun getPlaces(relationshipId: String): Flow<List<Place>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                firestore.collection("places")
                    .where { "relationshipId" equalTo relationshipId }
                    .snapshots
                    .map { snapshot ->
                        snapshot.documents.map { doc ->
                            val place: Place = doc.data()
                            place.copy(id = doc.id)
                        }
                    }
                    .catch { e ->
                        if (e.message?.contains("permission", ignoreCase = true) == true) {
                            println("DEBUG: [PLACE] getPlaces: Permission denied (expected during logout)")
                        } else {
                            println("ERROR: [PLACE] getPlaces error: ${e.message}")
                        }
                        emit(emptyList())
                    }
            }
        }
    }

    override suspend fun addPlace(place: Place): Result<Unit> {
        return try {
            val collection = firestore.collection("places")
            
            val id = if (place.id.isNotBlank()) {
                place.id
            } else {
                // Generate a random ID if none exists
                (1..20).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
            }

            val timestamp = Clock.System.now().toEpochMilliseconds()
            val newPlace = place.copy(
                id = id,
                createdAt = if (place.createdAt == 0L) timestamp else place.createdAt,
                updatedAt = timestamp
            )
            collection.document(id).set(newPlace)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePlace(placeId: String): Result<Unit> {
        return try {
            firestore.collection("places").document(placeId).delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllPlaces(relationshipId: String): Result<Unit> {
        return try {
            val snapshot = firestore.collection("places")
                .where { "relationshipId" equalTo relationshipId }
                .get()
            snapshot.documents.forEach { doc ->
                doc.reference.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
