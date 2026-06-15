package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val text: String = "",
    val sender: MessageSender = MessageSender.USER,
    val timestamp: Long = 0L,
    val isFromMe: Boolean = false
)
