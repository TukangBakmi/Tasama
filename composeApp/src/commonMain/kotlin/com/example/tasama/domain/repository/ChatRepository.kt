package com.example.tasama.domain.repository

import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getChannels(): Flow<List<ChatChannel>>
    fun getMessages(channelId: String): Flow<List<ChatMessage>>
    suspend fun getMoreMessages(channelId: String, limit: Int, beforeTimestamp: Long): List<ChatMessage>
    suspend fun sendMessage(channelId: String, text: String)
    suspend fun createChannelWithUser(otherUserId: String): String
    suspend fun getUserName(userId: String): String?
    suspend fun getUserIdFromShortId(shortId: String): String?
    fun getCurrentUserId(): String?
    suspend fun markChannelAsRead(channelId: String)
    suspend fun deleteChannel(channelId: String)
    suspend fun markMessageAsRead(channelId: String, messageId: String)
}
