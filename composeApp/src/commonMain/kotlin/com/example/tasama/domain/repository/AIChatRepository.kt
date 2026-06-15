package com.example.tasama.domain.repository

import com.example.tasama.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface AIChatRepository {
    fun getMessages(): Flow<List<ChatMessage>>
    suspend fun saveMessage(message: ChatMessage)
    suspend fun clearHistory()
}
