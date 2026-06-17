package com.example.tasama.presentation.ai

import com.example.tasama.domain.model.ChatMessage

data class AIUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isTyping: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true
)
