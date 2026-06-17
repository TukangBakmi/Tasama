package com.example.tasama.data.repository

import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class FirebaseChatRepository(
    private val authRepository: AuthRepository
) : ChatRepository {
    private val firestore = Firebase.firestore
    private val collection = firestore.collection("chat_messages")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMessages(): Flow<List<ChatMessage>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                collection.where { "userId" equalTo uid }
                    .orderBy("timestamp", direction = dev.gitlive.firebase.firestore.Direction.DESCENDING)
                    .limit(20)
                    .snapshots
                    .map { snapshot ->
                        snapshot.documents.map { it.data<ChatMessage>() }
                            .sortedBy { it.timestamp }
                    }
                    .catch { e ->
                        println("Firestore Error: ${e.message}")
                        emit(emptyList())
                    }
            }
        }
    }

    override suspend fun getMoreMessages(limit: Int, beforeTimestamp: Long): List<ChatMessage> {
        val uid = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            collection.where { "userId" equalTo uid }
                .where { "timestamp" lessThan beforeTimestamp }
                .orderBy("timestamp", direction = dev.gitlive.firebase.firestore.Direction.DESCENDING)
                .limit(limit)
                .get()
                .documents
                .map { it.data<ChatMessage>() }
                .sortedBy { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun sendMessage(text: String) {
        val userId = authRepository.getCurrentUserId() ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "msg_$now"
        val newMessage = ChatMessage(
            id = id,
            userId = userId,
            text = text,
            sender = MessageSender.USER,
            timestamp = now,
            isFromMe = true
        )
        collection.document(id).set(newMessage)
    }
}
