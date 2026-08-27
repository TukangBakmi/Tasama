package com.example.tasama.domain.repository

import com.example.tasama.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface AIChatRepository : SessionCleanupRepository {
    fun getMessages(): Flow<List<ChatMessage>>
    suspend fun getMoreMessages(limit: Int, beforeTimestamp: Long): List<ChatMessage>
    suspend fun saveMessage(message: ChatMessage)
    suspend fun deleteMessages(messageIds: List<String>)
    suspend fun restoreMessages(messages: List<ChatMessage>)
    suspend fun clearHistory()
}
