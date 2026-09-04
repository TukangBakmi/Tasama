package com.example.tasama.presentation.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.PresenceState
import com.example.tasama.presentation.components.TransientFeedback

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val textFieldValue: TextFieldValue = TextFieldValue(""),
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
    val participantNames: Map<String, String> = emptyMap(),
    val typingUsers: Set<String> = emptySet(),
    val typingIndicatorText: String? = null,
    val scrollToMessageId: String? = null,
    val highlightedMessageId: String? = null,
    val error: String? = null
)
