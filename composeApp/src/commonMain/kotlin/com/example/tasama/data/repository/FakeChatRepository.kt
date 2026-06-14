package com.example.tasama.data.repository

import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeChatRepository : ChatRepository {
    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = "1",
                text = "Hey! How is our savings for the Japan trip going?",
                sender = MessageSender.USER,
                timestamp = 0L,
                isFromMe = false
            ),
            ChatMessage(
                id = "2",
                text = "It's going well! We just reached 80% of our goal.",
                sender = MessageSender.USER,
                timestamp = 0L,
                isFromMe = true
            ),
            ChatMessage(
                id = "3",
                text = "That's awesome! Let's save a bit more this month.",
                sender = MessageSender.USER,
                timestamp = 0L,
                isFromMe = false
            )
        )
    )

    override fun getMessages(): Flow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun sendMessage(text: String) {
        val newMessage = ChatMessage(
            id = (_messages.value.size + 1).toString(),
            text = text,
            sender = MessageSender.USER,
            timestamp = 0L,
            isFromMe = true
        )
        _messages.update { it + newMessage }
    }
}
