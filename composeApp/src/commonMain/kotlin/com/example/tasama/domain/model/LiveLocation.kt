package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float? = null,
    val heading: Float? = null,
    val speed: Float? = null,
    val timestamp: Long = 0L
)
