package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatChannel(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCounts: Map<String, Int> = emptyMap()
)
