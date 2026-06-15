package com.example.tasama.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class GeminiService(
    private val apiKey: String = "AIzaSyBaens9ojdQjWK5BzpvMDZ7yecGaYAaPns"
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        defaultRequest {
            url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent")
            url.parameters.append("key", apiKey)
        }
    }

    suspend fun generateContent(prompt: String): String {
        return try {
            val response: GeminiResponse = client.post {
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiRequest(
                        contents = listOf(
                            Content(
                                parts = listOf(
                                    Part(text = prompt)
                                )
                            )
                        )
                    )
                )
            }.body()
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from AI."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
