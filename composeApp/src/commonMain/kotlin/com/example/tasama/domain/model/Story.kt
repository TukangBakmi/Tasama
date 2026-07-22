package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Story(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val date: Long = 0L,
    val category: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val photoUrls: List<String> = emptyList(),
    val address: String = "",
    val createdBy: String = "",
    val route: List<RoutePoint> = emptyList(),
    val totalDistance: Double? = null,
    val totalDuration: Long? = null,
    val visitedPlaces: List<String> = emptyList()
)
