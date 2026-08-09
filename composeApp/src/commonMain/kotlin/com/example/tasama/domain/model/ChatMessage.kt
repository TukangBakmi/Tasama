package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val text: String = "",
    val sender: MessageSender = MessageSender.USER,
    val timestamp: Long = 0L,
    val isFromMe: Boolean = false,
    val deliveredTo: Map<String, Long> = emptyMap(),
    val readBy: Map<String, Long> = emptyMap(),
    val deletedFor: List<String> = emptyList(),
    val repliedMessageId: String? = null,
    val repliedMessageSenderId: String? = null,
    val repliedMessageSenderName: String? = null,
    val repliedMessageText: String? = null,
    val repliedMessageType: String? = null,
    val repliedMessageTimestamp: Long? = null
)
