package com.example.tasama.data.repository

import com.example.tasama.domain.model.Activity
import com.example.tasama.domain.model.ActivityCategory
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.repository.ActivityRepository
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.PlaceRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class FirebasePlaceRepository(
    private val authRepository: AuthRepository,
    private val activityRepository: ActivityRepository
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
            val isUpdate = place.id.isNotBlank()
            
            val id = if (isUpdate) {
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
            
            logPartnerActivity(
                if (isUpdate) "PLACE_UPDATED" else "PLACE_ADDED",
                if (isUpdate) "Updated place: ${place.name}" else "Added a new place: ${place.name}"
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePlace(placeId: String): Result<Unit> {
        return try {
            val doc = firestore.collection("places").document(placeId)
            val snapshot = doc.get()
            val placeName = if (snapshot.exists) snapshot.data<Place>().name else "a place"
            
            doc.delete()
            logPartnerActivity("PLACE_DELETED", "Deleted place: $placeName")
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

    private suspend fun logPartnerActivity(type: String, details: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val user = authRepository.getUser(uid) ?: return
        val partnerId = user.partnerId
        
        activityRepository.logActivity(
            Activity(
                userId = uid,
                userName = user.name,
                category = ActivityCategory.PARTNER,
                type = type,
                title = when(type) {
                    "PLACE_ADDED" -> "Place Added"
                    "PLACE_UPDATED" -> "Place Updated"
                    "PLACE_DELETED" -> "Place Deleted"
                    else -> "Partner Update"
                },
                details = details,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                metadata = (if (partnerId != null) mapOf("targetUids" to "$uid,$partnerId") else emptyMap()) + 
                           mapOf("placeName" to (details.substringAfter("place: ").ifEmpty { "a place" }))
            )
        )
    }
}
