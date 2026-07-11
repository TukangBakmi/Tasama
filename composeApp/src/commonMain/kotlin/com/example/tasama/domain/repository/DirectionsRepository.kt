package com.example.tasama.domain.repository

enum class TravelMode {
    DRIVING, WALKING, MOTORCYCLE
}

interface DirectionsRepository {
    suspend fun getEta(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode? = null
    ): Result<EtaInfo>
}

data class EtaInfo(
    val durationText: String,
    val durationSeconds: Int,
    val distanceText: String,
    val distanceMeters: Int,
    val encodedPolyline: String? = null
)
