package com.example.tasama.domain.model

import kotlinx.serialization.Serializable
import com.example.tasama.util.FlexibleLongSerializer

@Serializable
data class LiveLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float? = null,
    val heading: Float? = null,
    val speed: Float? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val timestamp: Long = 0L
)
