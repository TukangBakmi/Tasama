package com.example.tasama.presentation.chat

import com.example.tasama.domain.model.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true
)
