package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
