package com.example.tasama.data.repository

import com.example.tasama.domain.model.WeatherInfo
import com.example.tasama.domain.repository.WeatherRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WeatherRepositoryImpl : WeatherRepository {
    private val httpClient = HttpClient {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    override suspend fun getWeather(lat: Double, lon: Double): Result<WeatherInfo> {
        return try {
            val response: OpenMeteoResponse = httpClient.get("https://api.open-meteo.com/v1/forecast") {
                parameter("latitude", lat)
                parameter("longitude", lon)
                parameter("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m")
                parameter("timezone", "auto")
                parameter("forecast_days", 1)
            }.body()

            val current = response.current
            Result.success(
                WeatherInfo(
                    temperature = current.temperature,
                    condition = getWeatherCondition(current.weatherCode),
                    iconCode = getWeatherEmoji(current.weatherCode),
                    feelsLike = current.apparentTemperature,
                    humidity = current.humidity.toInt(),
                    precipitationProbability = (current.precipitation * 100).toInt().coerceIn(0, 100),
                    windSpeed = current.windSpeed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getWeatherEmoji(code: Int): String {
        return when (code) {
            0 -> "☀️" // Clear sky
            1, 2, 3 -> "🌤️" // Mainly clear, partly cloudy, and overcast
            45, 48 -> "🌫️" // Fog
            51, 53, 55 -> "🌧️" // Drizzle
            61, 63, 65 -> "🌧️" // Rain
            71, 73, 75 -> "❄️" // Snow fall
            77 -> "❄️" // Snow grains
            80, 81, 82 -> "🌦️" // Rain showers
            85, 86 -> "🌨️" // Snow showers
            95 -> "⛈️" // Thunderstorm
            96, 99 -> "⛈️" // Thunderstorm with hail
            else -> "❓"
        }
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear, partly cloudy, and overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow fall"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with hail"
            else -> "Unknown"
        }
    }
}

@Serializable
data class OpenMeteoResponse(
    val current: CurrentWeather
)

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("precipitation") val precipitation: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double
)
