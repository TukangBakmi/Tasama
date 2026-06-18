package com.example.tasama.data.repository

import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.model.MessageStatus
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.where
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
    private val channelsCollection = firestore.collection("chat_channels")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getChannels(): Flow<List<ChatChannel>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                channelsCollection.where { "participantIds" contains uid }
                    .snapshots
                    .map { snapshot ->
                        snapshot.documents.map { it.data(ChatChannel.serializer()) }
                            .sortedByDescending { it.lastMessageTimestamp }
                    }
                    .catch { emit(emptyList()) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMessages(channelId: String): Flow<List<ChatMessage>> {
        val currentUserId = authRepository.getCurrentUserId()
        return channelsCollection.document(channelId).collection("messages")
            .orderBy("timestamp", direction = Direction.DESCENDING)
            .limit(20)
            .snapshots
            .map { snapshot ->
                snapshot.documents.map {
                    val msg = it.data(ChatMessage.serializer())
                    msg.copy(isFromMe = msg.userId == currentUserId)
                }
                .sortedBy { it.timestamp }
            }
            .catch { emit(emptyList()) }
    }

    override suspend fun getMoreMessages(channelId: String, limit: Int, beforeTimestamp: Long): List<ChatMessage> {
        val uid = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            channelsCollection.document(channelId).collection("messages")
                .where { "timestamp" lessThan beforeTimestamp }
                .orderBy("timestamp", direction = Direction.DESCENDING)
                .limit(limit)
                .get()
                .documents
                .map {
                    val msg = it.data(ChatMessage.serializer())
                    msg.copy(isFromMe = msg.userId == uid)
                }
                .sortedBy { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun sendMessage(channelId: String, text: String) {
        val userId = authRepository.getCurrentUserId() ?: return
        val senderName = authRepository.getUserName(userId) ?: "User"
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "msg_$now"
        val newMessage = ChatMessage(
            id = id,
            userId = userId,
            senderName = senderName,
            text = text,
            sender = MessageSender.USER,
            timestamp = now,
            status = MessageStatus.SENT
        )
        
        val channelRef = channelsCollection.document(channelId)
        val channel = channelRef.get().data(ChatChannel.serializer())
        
        val newUnreadCounts = channel.unreadCounts.toMutableMap()
        channel.participantIds.forEach { participantId ->
            if (participantId != userId) {
                newUnreadCounts[participantId] = (newUnreadCounts[participantId] ?: 0) + 1
            }
        }

        channelRef.collection("messages").document(id).set(ChatMessage.serializer(), newMessage)
        channelRef.update(
            "lastMessage" to text,
            "lastMessageTimestamp" to now,
            "unreadCounts" to newUnreadCounts
        )

        // Send Notification (In a real app, this would be handled by a Cloud Function)
        // For this task, we'll simulate the "trigger" or explain it needs a backend.
        // However, I can implement a client-side call to a hypothetical notification service 
        // if I had one, or just document that Firestore Triggers are preferred.
        // Let's assume we want to call a function to notify other participants.
        sendPushNotificationToParticipants(channel, newMessage)
    }

    private suspend fun sendPushNotificationToParticipants(channel: ChatChannel, message: ChatMessage) {
        val currentUserId = authRepository.getCurrentUserId() ?: return
        val otherParticipants = channel.participantIds.filter { it != currentUserId }
        
        for (participantId in otherParticipants) {
            val user = authRepository.getUser(participantId)
            val token = user?.fcmToken
            if (token != null) {
                // In a production app, you'd send this to your backend or use Firebase Admin SDK
                // Cloud Functions are the standard way to handle this on Firestore writes.
                println("Push notification would be sent to token: $token with message: ${message.text}")
            }
        }
    }

    override suspend fun createChannelWithUser(otherUserId: String): String {
        val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
        val currentUserName = authRepository.getUserName(currentUserId) ?: "User"
        val otherUserName = authRepository.getUserName(otherUserId) ?: "Partner"

        val channelId = if (currentUserId < otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        
        val existing = channelsCollection.document(channelId).get()
        if (!existing.exists) {
            val channel = ChatChannel(
                id = channelId,
                participantIds = listOf(currentUserId, otherUserId),
                participantNames = mapOf(currentUserId to currentUserName, otherUserId to otherUserName),
                lastMessage = "Started a conversation",
                lastMessageTimestamp = Clock.System.now().toEpochMilliseconds(),
                unreadCounts = mapOf(currentUserId to 0, otherUserId to 0)
            )
            channelsCollection.document(channelId).set(ChatChannel.serializer(), channel)
        }
        return channelId
    }

    override suspend fun markChannelAsRead(channelId: String) {
        val userId = authRepository.getCurrentUserId() ?: return
        val channelRef = channelsCollection.document(channelId)
        val channel = channelRef.get().data(ChatChannel.serializer())
        
        val newUnreadCounts = channel.unreadCounts.toMutableMap()
        newUnreadCounts[userId] = 0
        
        channelRef.update("unreadCounts" to newUnreadCounts)
        
        // Also mark all messages in this channel as read for this user
        // Firestore doesn't support multiple inequality filters on different fields.
        // We filter by userId and then check status in code.
        val messages = channelRef.collection("messages")
            .where { "userId" notEqualTo userId }
            .get()
            .documents
        
        messages.forEach { doc ->
            val msg = doc.data(ChatMessage.serializer())
            if (msg.status != MessageStatus.READ) {
                doc.reference.update("status" to MessageStatus.READ.name)
            }
        }
    }

    override suspend fun deleteChannel(channelId: String) {
        // In a real app, you might want to only hide it for the user, but request says "delete the room chat"
        // We'll delete the document and its messages collection.
        val channelRef = channelsCollection.document(channelId)
        
        // Delete messages first
        val messages = channelRef.collection("messages").get().documents
        messages.forEach { it.reference.delete() }
        
        // Delete channel
        channelRef.delete()
    }

    override suspend fun markMessageAsRead(channelId: String, messageId: String) {
        channelsCollection.document(channelId).collection("messages").document(messageId)
            .update("status" to MessageStatus.READ.name)
    }

    override suspend fun getUserName(userId: String): String? {
        return authRepository.getUserName(userId)
    }

    override suspend fun getUserIdFromShortId(shortId: String): String? {
        return authRepository.getUserIdFromShortId(shortId)
    }

    override fun getCurrentUserId(): String? {
        return authRepository.getCurrentUserId()
    }
}
