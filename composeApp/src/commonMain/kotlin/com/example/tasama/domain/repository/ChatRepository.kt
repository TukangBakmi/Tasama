package com.example.tasama.domain.repository

import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository : SessionCleanupRepository {
    fun getChannels(): Flow<List<ChatChannel>>
    fun getChannel(channelId: String): Flow<ChatChannel?>
    fun getMessages(channelId: String): Flow<List<ChatMessage>>
    suspend fun getMoreMessages(channelId: String, limit: Int, beforeTimestamp: Long): List<ChatMessage>
    suspend fun sendMessage(
        channelId: String,
        text: String,
        repliedMessageId: String? = null,
        repliedMessageSenderId: String? = null,
        repliedMessageSenderName: String? = null,
        repliedMessageText: String? = null,
        repliedMessageType: String? = null,
        repliedMessageTimestamp: Long? = null
    )
    suspend fun createChannelWithUser(otherUserId: String): String
    suspend fun getUserName(userId: String): String?
    suspend fun getUserIdFromShortId(shortId: String): String?
    fun getCurrentUserId(): String?
    suspend fun markChannelAsRead(channelId: String)
    suspend fun deleteChannel(channelId: String)
    suspend fun deleteChannels(channelIds: List<String>)
    suspend fun markMessageAsRead(channelId: String, messageId: String)
    suspend fun markMessageAsDelivered(channelId: String, messageId: String)
    suspend fun deleteMessages(channelId: String, messageIds: List<String>)
    suspend fun restoreMessages(channelId: String, messageIds: List<String>)
    suspend fun setActiveChannel(channelId: String?)
}
