package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val shortId: String = "",
    val email: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val fcmToken: String? = null
)
