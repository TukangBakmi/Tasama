package com.example.tasama.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DirectionsResponse(
    val routes: List<Route> = emptyList(),
    val status: String,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
data class Route(
    val legs: List<Leg> = emptyList(),
    @SerialName("overview_polyline") val overviewPolyline: Polyline? = null
)

@Serializable
data class Leg(
    val distance: Distance,
    val duration: Duration,
    @SerialName("duration_in_traffic") val durationInTraffic: Duration? = null
)

@Serializable
data class Distance(
    val text: String,
    val value: Int
)

@Serializable
data class Duration(
    val text: String,
    val value: Int
)

@Serializable
data class Polyline(
    val points: String
)
