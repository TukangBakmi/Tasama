package com.example.tasama.presentation.chat

import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.User

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val channelName: String = "Chat",
    val otherUser: User? = null,
    val error: String? = null
)
