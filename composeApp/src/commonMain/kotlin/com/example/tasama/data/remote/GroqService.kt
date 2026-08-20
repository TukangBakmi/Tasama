package com.example.tasama.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.network.sockets.ConnectTimeoutException

@Serializable
data class GroqResponseFormat(val type: String)

// Extend GroqRequest from GroqModels.kt with response_format if needed, 
// but since I can't easily extend @Serializable data classes across files with new fields without modifying them,
// I'll define a local version that matches the API expectations for JSON mode if the base one doesn't have it.
@Serializable
data class GroqToolRequest(
    val messages: List<GroqMessage>,
    val model: String = "openai/gpt-oss-20b",
    val temperature: Double = 0.5,
    val max_tokens: Int = 1024,
    val response_format: GroqResponseFormat? = null
)

sealed class GroqException(message: String) : Exception(message) {
    class Network(message: String) : GroqException(message)
    class Timeout(message: String) : GroqException(message)
    class RateLimit(message: String) : GroqException(message)
    class Auth(message: String) : GroqException(message)
    class Server(message: String) : GroqException(message)
    class Unknown(message: String) : GroqException(message)
}

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

    suspend fun generateContent(prompt: String, jsonMode: Boolean = false): String {
        try {
            val response = client.post {
                contentType(ContentType.Application.Json)
                setBody(
                    GroqToolRequest(
                        messages = listOf(GroqMessage(role = "user", content = prompt)),
                        response_format = if (jsonMode) GroqResponseFormat("json_object") else null
                    )
                )
            }

            if (!response.status.isSuccess()) {
                val errorBody = try { response.body<GroqResponse>().error } catch (_: Exception) { null }
                val message = errorBody?.message ?: "HTTP ${response.status.value}"
                
                when (response.status) {
                    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw GroqException.Auth(message)
                    HttpStatusCode.TooManyRequests -> throw GroqException.RateLimit(message)
                    HttpStatusCode.RequestTimeout, HttpStatusCode.GatewayTimeout -> throw GroqException.Timeout(message)
                    HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable -> throw GroqException.Server(message)
                    else -> throw GroqException.Unknown(message)
                }
            }

            val groqResponse: GroqResponse = response.body()
            
            if (groqResponse.error != null) {
                throw GroqException.Unknown(groqResponse.error.message ?: "Unknown API error")
            }
            
            return groqResponse.choices?.firstOrNull()?.message?.content 
                ?: throw GroqException.Unknown("Empty response from AI")
                
        } catch (e: HttpRequestTimeoutException) {
            throw GroqException.Timeout("Request timed out")
        } catch (e: ConnectTimeoutException) {
            throw GroqException.Timeout("Connection timed out")
        } catch (e: GroqException) {
            throw e
        } catch (e: Exception) {
            throw GroqException.Network(e.message ?: "Network error")
        }
    }
}
