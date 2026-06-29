package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val shortId: String = "",
    val email: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val fcmToken: String? = null,
    val partnerId: String? = null,
    val partnerRequestFrom: String? = null, // UID of user who sent a request
    val partnerRequestTo: String? = null,   // UID of user to whom request was sent
    val anniversaryDate: Long? = null,      // Anniversary timestamp
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastLocationUpdate: Long? = null,
    val lastActive: Long? = null,
    val batteryLevel: Float? = null,
    val isCharging: Boolean? = null
)
