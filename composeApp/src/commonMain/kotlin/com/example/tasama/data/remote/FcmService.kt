package com.example.tasama.data.remote

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FcmRequest(
    val to: String,
    val notification: FcmNotification,
    val data: Map<String, String>
)

@Serializable
data class FcmNotification(
    val title: String,
    val body: String,
    val sound: String = "default"
)

class FcmService(
    private val serverKey: String
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
            requestTimeoutMillis = 15_000
        }
    }

    suspend fun sendNotification(token: String, title: String, body: String): Boolean {
        if (serverKey.isBlank()) return false
        
        return try {
            val response = client.post("https://fcm.googleapis.com/fcm/send") {
                header(HttpHeaders.Authorization, "key=$serverKey")
                contentType(ContentType.Application.Json)
                setBody(
                    FcmRequest(
                        to = token,
                        notification = FcmNotification(title = title, body = body),
                        data = mapOf("title" to title, "body" to body)
                    )
                )
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            println("FCM Error: ${e.message}")
            false
        }
    }
}
