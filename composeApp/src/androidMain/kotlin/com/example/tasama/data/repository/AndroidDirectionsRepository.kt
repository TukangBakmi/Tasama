package com.example.tasama.data.repository

import android.content.Context
import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.DistanceInfo

class AndroidDirectionsRepository(
    private val context: Context,
    private val freeDirectionsRepository: FreeDirectionsRepository = FreeDirectionsRepository()
) : DirectionsRepository {

    override fun getDistance(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): DistanceInfo {
        return freeDirectionsRepository.getDistance(originLat, originLon, destLat, destLon)
    }
}
