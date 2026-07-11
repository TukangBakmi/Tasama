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
        destLon: Double
    ): Result<EtaInfo> {
        return try {
            val response = fetchDirections(originLat, originLon, destLat, destLon, "driving")
            
            if (response.status == "OK" && response.routes.isNotEmpty()) {
                val leg = response.routes[0].legs[0]
                val duration = leg.durationInTraffic ?: leg.duration
                Result.success(
                    EtaInfo(
                        durationText = duration.text,
                        durationSeconds = duration.value,
                        distanceText = leg.distance.text,
                        distanceMeters = leg.distance.value
                    )
                )
            } else if (response.status == "ZERO_RESULTS") {
                // Fallback to walking
                val walkingResponse = fetchDirections(originLat, originLon, destLat, destLon, "walking")
                if (walkingResponse.status == "OK" && walkingResponse.routes.isNotEmpty()) {
                    val leg = walkingResponse.routes[0].legs[0]
                    Result.success(
                        EtaInfo(
                            durationText = leg.duration.text,
                            durationSeconds = leg.duration.value,
                            distanceText = leg.distance.text,
                            distanceMeters = leg.distance.value
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
