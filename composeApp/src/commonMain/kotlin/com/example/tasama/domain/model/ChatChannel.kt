package com.example.tasama.domain.model

import kotlinx.serialization.Serializable
import com.example.tasama.util.FlexibleLongSerializer

@Serializable
data class ChatChannel(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageId: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = "",
    val lastMessageDeliveredTo: Map<String, @Serializable(with = FlexibleLongSerializer::class) Long> = emptyMap(),
    val lastMessageReadBy: Map<String, @Serializable(with = FlexibleLongSerializer::class) Long> = emptyMap(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val deletedAt: Map<String, @Serializable(with = FlexibleLongSerializer::class) Long> = emptyMap()
)
