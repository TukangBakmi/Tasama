package com.example.tasama.domain.model

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: MessageSender,
    val timestamp: Long = 0L
)

enum class MessageSender {
    USER, AI
}
