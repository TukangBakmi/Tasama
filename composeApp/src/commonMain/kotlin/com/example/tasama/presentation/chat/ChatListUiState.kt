package com.example.tasama.presentation.chat

import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.PresenceState

data class ChatListUiState(
    val channels: List<ChatChannel> = emptyList(),
    val channelUsers: Map<String, User> = emptyMap(),
    val userPresence: Map<String, PresenceState> = emptyMap(),
    val contacts: List<User> = emptyList(),
    val filteredContacts: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchedUser: SearchedUser? = null,
    val isSearchingUser: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedChannelIds: Set<String> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
    val contactToDelete: User? = null,
    val typingNames: Map<String, String> = emptyMap(),
    val drafts: Map<String, String> = emptyMap()
)

data class SearchedUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null
)
