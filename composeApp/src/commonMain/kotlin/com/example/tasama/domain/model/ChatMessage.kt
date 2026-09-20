package com.example.tasama.domain.model

import kotlinx.serialization.Serializable
import com.example.tasama.util.FlexibleLongSerializer

@Serializable
data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val text: String = "",
    val sender: MessageSender = MessageSender.USER,
    @Serializable(with = FlexibleLongSerializer::class)
    val timestamp: Long = 0L,
    val isFromMe: Boolean = false,
    val deliveredTo: Map<String, @Serializable(with = FlexibleLongSerializer::class) Long> = emptyMap(),
    val readBy: Map<String, @Serializable(with = FlexibleLongSerializer::class) Long> = emptyMap(),
    val deletedFor: List<String> = emptyList(),
    val repliedMessageId: String? = null,
    val repliedMessageSenderId: String? = null,
    val repliedMessageSenderName: String? = null,
    val repliedMessageText: String? = null,
    val repliedMessageType: String? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val repliedMessageTimestamp: Long? = null
)
