package com.example.tasama.data.repository

import com.example.tasama.domain.model.Activity
import com.example.tasama.domain.model.ActivityCategory
import com.example.tasama.domain.repository.ActivityRepository
import com.example.tasama.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

class FirebaseActivityRepository(
    private val authRepository: AuthRepository
) : ActivityRepository {
    private val firestore = Firebase.firestore
    private val activitiesCollection = firestore.collection("activities")

    override suspend fun cleanup() {
        println("DEBUG: Cleaning up FirebaseActivityRepository")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getActivities(category: ActivityCategory?): Flow<List<Activity>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                activitiesCollection
                    .where(FieldPath("targetUids"), arrayContains = uid)
                    .snapshots
                    .map { snapshot ->
                        val activities = snapshot.documents.map { doc ->
                            try {
                                doc.data<Activity>()
                            } catch (e: Exception) {
                                println("DEBUG: Error parsing activity ${doc.id}: ${e.message}")
                                null
                            }
                        }.filterNotNull()
                        
                        val filtered = if (category != null) {
                            activities.filter { it.category == category }
                        } else {
                            activities
                        }

                        val sorted = filtered.sortedByDescending { it.timestamp }
                        println("DEBUG: Found ${sorted.size} activities for uid $uid, category $category (Total fetched: ${activities.size})")
                        sorted
                    }.catch { e ->
                        println("Error fetching activities for uid $uid, category $category: ${e.message}")
                        emit(emptyList())
                    }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun hasUnread(category: ActivityCategory?): Flow<Boolean> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(false)
            else {
                getActivities(category).map { activities ->
                    activities.any { !it.metadata.containsKey("readBy_$uid") }
                }
            }
        }
    }

    override suspend fun logActivity(activity: Activity) {
        val id = if (activity.id.isEmpty()) "act_${Clock.System.now().toEpochMilliseconds()}" else activity.id
        
        val currentUid = authRepository.getCurrentUserId()
        val user = currentUid?.let { authRepository.getUser(it) }
        val partnerId = user?.partnerId
        
        val targetUids = if (activity.metadata.containsKey("targetUids")) {
            activity.metadata["targetUids"]?.split(",") ?: emptyList()
        } else {
            // Default targets: current user and partner
            listOfNotNull(currentUid, partnerId)
        }
        
        val data = mutableMapOf(
            "id" to id,
            "userId" to activity.userId,
            "userName" to activity.userName,
            "category" to activity.category.name,
            "type" to activity.type,
            "title" to activity.title,
            "details" to activity.details,
            "icon" to activity.icon,
            "timestamp" to if (activity.timestamp > 0) activity.timestamp else Clock.System.now().toEpochMilliseconds(),
            "metadata" to activity.metadata,
            "targetUids" to targetUids
        )

        activity.affectedUserId?.let { data["affectedUserId"] = it }
        activity.affectedUserName?.let { data["affectedUserName"] = it }

        activitiesCollection.document(id).set(data)
    }

    override suspend fun markAsRead(activityIds: List<String>) {
        val uid = authRepository.getCurrentUserId() ?: return
        activityIds.forEach { id ->
            try {
                activitiesCollection.document(id).updateFields {
                    FieldPath("metadata", "readBy_$uid") to Clock.System.now().toEpochMilliseconds().toString()
                }
            } catch (e: Exception) {
                println("Error marking activity as read: ${id} - ${e.message}")
            }
        }
    }

    override suspend fun markAllAsRead(category: ActivityCategory?) {
        val uid = authRepository.getCurrentUserId() ?: return
        try {
            val activities = getActivities(category).first()
            val unreadIds = activities.filter { !it.metadata.containsKey("readBy_$uid") }.map { it.id }
            if (unreadIds.isNotEmpty()) {
                markAsRead(unreadIds)
            }
        } catch (e: Exception) {
            println("Error marking all as read: ${e.message}")
        }
    }

    override suspend fun deleteActivity(activityId: String) {
        try {
            activitiesCollection.document(activityId).delete()
        } catch (e: Exception) {
            println("Error deleting activity: ${e.message}")
        }
    }
}
