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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock

class FirebaseChatRepository(
    private val authRepository: AuthRepository
) : ChatRepository {
    private val firestore = Firebase.firestore
    private val channelsCollection = firestore.collection("chat_channels")
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getChannels(): Flow<List<ChatChannel>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                channelsCollection.where { "participantIds" contains uid }
                    .snapshots
                    .map { snapshot ->
                        snapshot.documents.map { it.data(ChatChannel.serializer()) }
                            .filter { channel -> 
                                val deletedAt = channel.deletedAt[uid] ?: 0L
                                deletedAt < channel.lastMessageTimestamp
                            }
                            .sortedByDescending { it.lastMessageTimestamp }
                    }
                    .catch { emit(emptyList()) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMessages(channelId: String): Flow<List<ChatMessage>> {
        val uid = authRepository.getCurrentUserId() ?: return flowOf(emptyList())
        val channelRef = channelsCollection.document(channelId)
        
        return channelRef.snapshots.flatMapLatest { channelSnapshot ->
            val channel = try { channelSnapshot.data(ChatChannel.serializer()) } catch (e: Exception) { null }
            val deletedAt = channel?.deletedAt?.get(uid) ?: 0L
            
            channelRef.collection("messages")
                .where { "timestamp" greaterThan deletedAt }
                .orderBy("timestamp", direction = Direction.DESCENDING)
                .limit(20)
                .snapshots
                .map { snapshot ->
                    snapshot.documents.map { doc ->
                        val msg = doc.data(ChatMessage.serializer())
                        
                        // Auto-update SENT messages to DELIVERED when fetched by recipient
                        if (msg.userId != uid && msg.status == MessageStatus.SENT) {
                            repositoryScope.launch {
                                try {
                                    doc.reference.updateFields {
                                        "status" to MessageStatus.DELIVERED.name
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        msg.copy(isFromMe = msg.userId == uid)
                    }
                    .sortedBy { it.timestamp }
                }
        }.catch { emit(emptyList()) }
    }

    override suspend fun getMoreMessages(channelId: String, limit: Int, beforeTimestamp: Long): List<ChatMessage> {
        val uid = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            val channel = channelsCollection.document(channelId).get().data(ChatChannel.serializer())
            val deletedAt = channel.deletedAt[uid] ?: 0L
            
            channelsCollection.document(channelId).collection("messages")
                .where { "timestamp" lessThan beforeTimestamp }
                .where { "timestamp" greaterThan deletedAt }
                .orderBy("timestamp", direction = Direction.DESCENDING)
                .limit(limit)
                .get()
                .documents
                .map {
                    val msg = it.data(ChatMessage.serializer())
                    msg.copy(isFromMe = msg.userId == uid)
                }
                .sortedBy { it.timestamp }
        } catch (_: Exception) {
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
        channelRef.updateFields {
            "lastMessage" to (text as Any?)
            "lastMessageTimestamp" to (now as Any?)
            "unreadCounts" to (newUnreadCounts as Any?)
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
                unreadCounts = mapOf(currentUserId to 0, otherUserId to 0),
                deletedAt = emptyMap()
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
        
        channelRef.updateFields { "unreadCounts" to newUnreadCounts }
        
        // Also mark all messages in this channel as read for this user
        val messages = channelRef.collection("messages")
            .where { "userId" notEqualTo userId }
            .get()
            .documents
        
        messages.forEach { doc ->
            val msg = doc.data(ChatMessage.serializer())
            if (
                msg.userId != userId &&
                (msg.status == MessageStatus.DELIVERED || msg.status == MessageStatus.SENT)
            ) {
                doc.reference.updateFields {
                    "status" to MessageStatus.READ.name
                }
            }
        }
    }

    override suspend fun deleteChannel(channelId: String) {
        val userId = authRepository.getCurrentUserId() ?: return
        val channelRef = channelsCollection.document(channelId)
        val channel = channelRef.get().data(ChatChannel.serializer())
        
        val newDeletedAt = channel.deletedAt.toMutableMap()
        newDeletedAt[userId] = Clock.System.now().toEpochMilliseconds()
        
        channelRef.updateFields { "deletedAt" to newDeletedAt }
    }

    override suspend fun markMessageAsRead(channelId: String, messageId: String) {
        channelsCollection.document(channelId).collection("messages").document(messageId)
            .updateFields { "status" to MessageStatus.READ.name }
    }

    override suspend fun markMessageAsDelivered(
        channelId: String,
        messageId: String
    ) {
        channelsCollection
            .document(channelId)
            .collection("messages")
            .document(messageId)
            .updateFields {
                "status" to MessageStatus.DELIVERED.name
            }
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
