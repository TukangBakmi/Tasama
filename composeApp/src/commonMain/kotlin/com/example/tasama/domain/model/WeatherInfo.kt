package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherInfo(
    val temperature: Double,
    val condition: String,
    val iconCode: String,
    val feelsLike: Double,
    val humidity: Int,
    val precipitationProbability: Int,
    val windSpeed: Double
)
