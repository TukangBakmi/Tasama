package com.example.tasama.presentation.chat

import com.example.tasama.domain.model.ChatChannel

data class ChatListUiState(
    val channels: List<ChatChannel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchedUser: SearchedUser? = null,
    val isSearchingUser: Boolean = false
)

data class SearchedUser(
    val id: String,
    val name: String
)
