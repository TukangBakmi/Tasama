package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityCategory {
    SAVINGS,
    PARTNER,
    SYSTEM
}

@Serializable
data class Activity(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val affectedUserId: String? = null,
    val affectedUserName: String? = null,
    val category: ActivityCategory,
    val type: String,
    val title: String = "",
    val details: String = "",
    val icon: String = "",
    val timestamp: Long = 0,
    val metadata: Map<String, String> = emptyMap()
) {
    fun isUnreadFor(uid: String?): Boolean {
        if (uid == null) return false
        return !metadata.containsKey("readBy_$uid")
    }
}
