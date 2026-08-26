package com.example.tasama.presentation.chat

import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.PresenceState

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val channelName: String = "Chat",
    val otherUser: User? = null,
    val presence: PresenceState = PresenceState.Offline(0L),
    val selectedMessageIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val replyingToMessage: ChatMessage? = null,
    val isSending: Boolean = false,
    val scrollToMessageId: String? = null,
    val highlightedMessageId: String? = null,
    val error: String? = null
)
