package com.example.tasama.domain.repository

import com.example.tasama.util.Location

enum class TravelMode {
    DRIVING, WALKING, MOTORCYCLE
}

interface DirectionsRepository {
    fun getDistance(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): DistanceInfo

    suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode
    ): Result<RouteInfo>

    fun openExternalNavigation(lat: Double, lon: Double, mode: TravelMode)
}

data class DistanceInfo(
    val distanceText: String,
    val distanceMeters: Int
)

data class RouteInfo(
    val polylinePoints: List<Location>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val bounds: RouteBounds? = null
)

data class RouteBounds(
    val southwest: Location,
    val northeast: Location
)

