package com.example.tasama.data.repository

import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.DistanceInfo
import com.example.tasama.util.Location
import com.example.tasama.util.calculateDistance
import com.example.tasama.util.format
import kotlin.math.roundToInt

class FreeDirectionsRepository : DirectionsRepository {

    override fun getDistance(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): DistanceInfo {
        val origin = Location(originLat, originLon)
        val dest = Location(destLat, destLon)
        val distanceMeters = calculateDistance(origin, dest).roundToInt()
        
        val distanceText = formatDistance(distanceMeters)

        return DistanceInfo(
            distanceText = distanceText,
            distanceMeters = distanceMeters
        )
    }

    private fun formatDistance(meters: Int): String {
        return if (meters < 1000) {
            "${meters}m"
        } else {
            "${(meters / 1000.0).format(1)}km"
        }
    }
}
