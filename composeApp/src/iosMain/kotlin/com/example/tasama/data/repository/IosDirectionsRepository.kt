package com.example.tasama.data.repository

import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.DistanceInfo
import com.example.tasama.domain.repository.RouteInfo
import com.example.tasama.domain.repository.TravelMode
import com.example.tasama.util.Location
import com.example.tasama.util.calculateDistance
import com.example.tasama.util.format
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.math.roundToInt

class IosDirectionsRepository(
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
        val transportType = when (mode) {
            TravelMode.DRIVING -> "d"
            TravelMode.WALKING -> "w"
            TravelMode.MOTORCYCLE -> "d" // Apple Maps doesn't have a specific motorcycle mode
        }
        
        val appleMapsUrl = "http://maps.apple.com/?daddr=$lat,$lon&dirflg=$transportType"
        val googleMapsUrl = "comgooglemaps://?daddr=$lat,$lon&directionsmode=${when(mode){
            TravelMode.DRIVING -> "driving"
            TravelMode.WALKING -> "walking"
            TravelMode.MOTORCYCLE -> "driving"
        }}"

        val application = UIApplication.sharedApplication
        val gUrl = NSURL.URLWithString(googleMapsUrl)
        
        if (gUrl != null && application.canOpenURL(gUrl)) {
            application.openURL(gUrl)
        } else {
            val aUrl = NSURL.URLWithString(appleMapsUrl)
            if (aUrl != null) {
                application.openURL(aUrl)
            }
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
