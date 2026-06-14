package com.example.tasama.presentation.chat

import com.example.tasama.domain.model.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val currentMessage: String = ""
)
