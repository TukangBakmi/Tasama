package com.example.tasama.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class GroqRequest(
    val model: String = "llama-3.1-8b-instant",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.7,
    val max_completion_tokens: Int = 1024
)

@Serializable
data class GroqMessage(
    val role: String, // "system", "user", atau "assistant"
    val content: String
)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice>? = null,
    val error: GroqError? = null
)

@Serializable
data class GroqChoice(
    val message: GroqMessage
)

@Serializable
data class GroqError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)