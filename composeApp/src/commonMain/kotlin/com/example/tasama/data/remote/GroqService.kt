package com.example.tasama.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class GroqService(
    private val apiKey: String
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        defaultRequest {
            url("https://api.groq.com/openai/v1/chat/completions")
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }
    }

    suspend fun generateContent(prompt: String): String {
        return try {
            val response: GroqResponse = client.post {
                contentType(ContentType.Application.Json)
                setBody(
                    GroqRequest(
                        messages = listOf(
                            GroqMessage(role = "user", content = prompt)
                        )
                    )
                )
            }.body()

            when {
                response.error != null -> "API Error: ${response.error.message}"
                response.choices.isNullOrEmpty() -> "Tidak ada respons dari AI."
                else -> response.choices.first().message.content
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}