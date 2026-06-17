package com.example.tasama.domain.repository

import com.example.tasama.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessages(): Flow<List<ChatMessage>>
    suspend fun getMoreMessages(limit: Int, beforeTimestamp: Long): List<ChatMessage>
    suspend fun sendMessage(text: String)
}
