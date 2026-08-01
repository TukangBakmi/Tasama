package com.example.tasama.domain.repository

interface DirectionsRepository {
    fun getDistance(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): DistanceInfo
}

data class DistanceInfo(
    val distanceText: String,
    val distanceMeters: Int
)
