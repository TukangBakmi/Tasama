package com.example.tasama.data.repository

import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.DistanceInfo
import com.example.tasama.domain.repository.RouteInfo
import com.example.tasama.domain.repository.TravelMode
import com.example.tasama.util.Location
import com.example.tasama.util.calculateDistance
import com.example.tasama.util.format
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

class FreeDirectionsRepository : DirectionsRepository {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

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

    override suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: TravelMode
    ): Result<RouteInfo> {
        return try {
            val profile = when (mode) {
                TravelMode.DRIVING -> "foot-walking" // OSRM default public instance doesn't support 'car' well on some endpoints, but let's try 'driving'
                TravelMode.WALKING -> "foot-walking"
                TravelMode.MOTORCYCLE -> "driving"
            }
            
            // Using OSRM (Open Source Routing Machine) public demo API
            val url = "https://router.project-osrm.org/route/v1/$profile/$originLon,$originLat;$destLon,$destLat?overview=full&geometries=polyline"
            
            val response: OsrmResponse = httpClient.get(url).body()
            
            if (response.routes.isEmpty()) {
                return Result.failure(Exception("No route found"))
            }
            
            val route = response.routes[0]
            val points = decodePolyline(route.geometry)
            
            Result.success(
                RouteInfo(
                    polylinePoints = points,
                    distanceMeters = route.distance.roundToInt(),
                    durationSeconds = route.duration.roundToInt()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun openExternalNavigation(lat: Double, lon: Double, mode: TravelMode) {
        // Architecture designed to be implemented by platform-specific repositories 
    }

    private fun formatDistance(meters: Int): String {
        return if (meters < 1000) {
            "${meters}m"
        } else {
            "${(meters / 1000.0).format(1)}km"
        }
    }

    private fun decodePolyline(encoded: String): List<Location> {
        val poly = ArrayList<Location>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = Location(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }
}

@Serializable
data class OsrmResponse(
    val routes: List<OsrmRoute>
)

@Serializable
data class OsrmRoute(
    val geometry: String,
    val distance: Double,
    val duration: Double
)

