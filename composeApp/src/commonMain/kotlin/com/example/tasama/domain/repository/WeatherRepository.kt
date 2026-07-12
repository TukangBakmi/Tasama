package com.example.tasama.domain.repository

import com.example.tasama.domain.model.WeatherInfo

interface WeatherRepository {
    suspend fun getWeather(lat: Double, lon: Double): Result<WeatherInfo>
}
