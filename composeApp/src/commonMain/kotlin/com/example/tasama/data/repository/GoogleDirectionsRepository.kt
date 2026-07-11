package com.example.tasama.data.repository

import com.example.tasama.data.remote.DirectionsResponse
import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.EtaInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

import kotlin.math.*

class GoogleDirectionsRepository(
    private val apiKey: String
) : DirectionsRepository {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    override suspend fun getEta(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: com.example.tasama.domain.repository.TravelMode?
    ): Result<EtaInfo> {
        return try {
            val apiMode = when (mode) {
                com.example.tasama.domain.repository.TravelMode.WALKING -> "walking"
                com.example.tasama.domain.repository.TravelMode.MOTORCYCLE -> "two_wheeler"
                com.example.tasama.domain.repository.TravelMode.DRIVING -> "driving"
                null -> {
                    val roughDistance = calculateRoughDistance(originLat, originLon, destLat, destLon)
                    if (roughDistance < 1500) "walking" else "driving"
                }
            }

            var response = fetchDirections(originLat, originLon, destLat, destLon, apiMode)

            // If two_wheeler fails, fallback to bicycling or driving as motorcycle isn't supported everywhere
            if (response.status == "INVALID_REQUEST" && apiMode == "two_wheeler") {
                response = fetchDirections(originLat, originLon, destLat, destLon, "driving")
            }

            if (response.status == "OK" && response.routes.isNotEmpty()) {
                val route = response.routes[0]
                val leg = route.legs[0]
                val duration = leg.durationInTraffic ?: leg.duration
                Result.success(
                    EtaInfo(
                        durationText = duration.text,
                        durationSeconds = duration.value,
                        distanceText = leg.distance.text,
                        distanceMeters = leg.distance.value,
                        encodedPolyline = route.overviewPolyline?.points
                    )
                )
            } else if (response.status == "ZERO_RESULTS" && apiMode == "driving") {
                // Fallback to walking if driving failed
                val walkingResponse = fetchDirections(originLat, originLon, destLat, destLon, "walking")
                if (walkingResponse.status == "OK" && walkingResponse.routes.isNotEmpty()) {
                    val route = walkingResponse.routes[0]
                    val leg = route.legs[0]
                    Result.success(
                        EtaInfo(
                            durationText = leg.duration.text,
                            durationSeconds = leg.duration.value,
                            distanceText = leg.distance.text,
                            distanceMeters = leg.distance.value,
                            encodedPolyline = route.overviewPolyline?.points
                        )
                    )
                } else {
                    val errorMsg = "Directions API error (walking): ${walkingResponse.status}"
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorMsg = "Directions API error: ${response.status}${response.errorMessage?.let { " - $it" } ?: ""}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateRoughDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * PI / 180) * cos(lat2 * PI / 180) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private suspend fun fetchDirections(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        mode: String
    ): DirectionsResponse {
        return client.get("https://maps.googleapis.com/maps/api/directions/json") {
            parameter("origin", "$originLat,$originLon")
            parameter("destination", "$destLat,$destLon")
            parameter("key", apiKey)
            parameter("mode", mode)
            
            // Add headers for Android-restricted API keys
            header("X-Android-Package", "com.example.tasama")
            header("X-Android-Cert", "3045AAC2B275824A6A9318D56A787FB5E8D3D1D2")

            if (mode == "driving") {
                parameter("departure_time", "now")
            }
        }.body()
    }
}
