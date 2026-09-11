package com.example.tasama.domain.repository

import com.example.tasama.domain.model.Activity
import com.example.tasama.domain.model.ActivityCategory
import kotlinx.coroutines.flow.Flow

interface ActivityRepository : SessionCleanupRepository {
    fun getActivities(category: ActivityCategory? = null): Flow<List<Activity>>
    fun hasUnread(category: ActivityCategory? = null): Flow<Boolean>
    suspend fun logActivity(activity: Activity)
    suspend fun markAsRead(activityIds: List<String>)
    suspend fun markAllAsRead(category: ActivityCategory? = null)
    suspend fun deleteActivity(activityId: String)
}
