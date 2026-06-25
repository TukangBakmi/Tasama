package com.example.tasama.presentation.chat

import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.User

data class ChatListUiState(
    val channels: List<ChatChannel> = emptyList(),
    val channelUsers: Map<String, User> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchedUser: SearchedUser? = null,
    val isSearchingUser: Boolean = false
)

data class SearchedUser(
    val id: String,
    val name: String
)
