package com.example.tasama.data.repository

import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.repository.AIChatRepository
import com.example.tasama.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.datetime.Clock as KtClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FirebaseAIChatRepository(
    private val authRepository: AuthRepository
) : AIChatRepository {
    private val firestore = Firebase.firestore
    private val collection = firestore.collection("ai_chat_history")

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

    override suspend fun saveMessage(message: ChatMessage) {
        try {
            val userId = authRepository.getCurrentUserId() ?: return
            val now = KtClock.System.now().toEpochMilliseconds()
            val id = message.id.ifEmpty { "ai_msg_$now" }
            val finalMessage = message.copy(id = id, userId = userId)
            collection.document(id).set(finalMessage)
        } catch (e: Exception) {
            println("Firestore saveMessage error: ${e.message}")
        }
    }

    override suspend fun clearHistory() {
        try {
            val userId = authRepository.getCurrentUserId() ?: return
            val documents = collection.where { "userId" equalTo userId }.get().documents
            documents.forEach { it.reference.delete() }
        } catch (e: Exception) {
            println("Firestore clearHistory error: ${e.message}")
        }
    }
}
