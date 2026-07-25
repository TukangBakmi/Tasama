package com.example.tasama.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.DistanceInfo
import com.example.tasama.domain.repository.RouteInfo
import com.example.tasama.domain.repository.TravelMode
import com.example.tasama.util.Location
import com.example.tasama.util.calculateDistance
import com.example.tasama.util.format
import kotlin.math.roundToInt

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

    override suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode
    ): Result<RouteInfo> {
        return freeDirectionsRepository.getRoute(originLat, originLon, destLat, destLon, mode)
    }

    override fun openExternalNavigation(lat: Double, lon: Double, mode: TravelMode) {
        val modeStr = when (mode) {
            TravelMode.DRIVING -> "d"
            TravelMode.WALKING -> "w"
            TravelMode.MOTORCYCLE -> "l" // 'l' is for two-wheeler in some regions, or default to 'd'
        }
        
        val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon&mode=$modeStr")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            // Fallback to browser if Maps app is not available
            val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=${mode.name.lowercase()}")
            val browserIntent = Intent(Intent.ACTION_VIEW, browserUri)
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (meters < 1000) {
            "${meters}m"
        } else {
            "${(meters / 1000.0).format(1)}km"
        }
    }
}
