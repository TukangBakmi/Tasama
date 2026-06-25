package com.example.tasama.data.remote

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FcmRequest(
    val message: FcmMessage
)

@Serializable
data class FcmMessage(
    val token: String,
    val notification: FcmNotification,
    val data: Map<String, String>
)

@Serializable
data class FcmNotification(
    val title: String,
    val body: String
)

class FcmService(
    private val projectId: String,
    private val accessToken: String
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }

    suspend fun sendNotification(token: String, title: String, body: String): Boolean {
        if (accessToken.isBlank()) return false
        
        return try {
            val response = client.post("https://fcm.googleapis.com/v1/projects/$projectId/messages:send") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(
                    FcmRequest(
                        message = FcmMessage(
                            token = token,
                            notification = FcmNotification(title = title, body = body),
                            data = mapOf("title" to title, "body" to body)
                        )
                    )
                )
            }
            if (!response.status.isSuccess()) {
                println("FCM V1 Error: ${response.bodyAsText()}")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            println("FCM Error: ${e.message}")
            false
        }
    }
}
