package com.example.tasama.domain.repository

interface DirectionsRepository {
    suspend fun getEta(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): Result<EtaInfo>
}

data class EtaInfo(
    val durationText: String,
    val durationSeconds: Int,
    val distanceText: String,
    val distanceMeters: Int
)
