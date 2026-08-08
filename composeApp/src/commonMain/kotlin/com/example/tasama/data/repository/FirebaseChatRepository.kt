package com.example.tasama.data.repository

import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
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

    override fun getChannel(channelId: String): Flow<ChatChannel?> {
        return channelsCollection.document(channelId).snapshots
            .map { 
                if (it.exists) it.data(ChatChannel.serializer()) else null
            }
            .catch { emit(null) }
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
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.map { doc ->
                        val msg = doc.data(ChatMessage.serializer())
                        
                        // Auto-update deliveredTo when fetched by recipient
                        if (msg.userId != uid && !msg.deliveredTo.containsKey(uid)) {
                            repositoryScope.launch {
                                try {
                                    val now = Clock.System.now().toEpochMilliseconds()
                                    doc.reference.updateFields {
                                        "deliveredTo.$uid" to now
                                    }
                                    
                                    // Update channel's lastMessageDeliveredTo if this is the last message
                                    val channelData = channelRef.get().data(ChatChannel.serializer())
                                    if (channelData.lastMessageId == msg.id) {
                                        channelRef.updateFields {
                                            "lastMessageDeliveredTo.$uid" to now
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        msg.copy(isFromMe = msg.userId == uid)
                    }
                    .filter { !it.deletedFor.contains(uid) }
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
                .filter { !it.deletedFor.contains(uid) }
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
        
        val channelRef = channelsCollection.document(channelId)
        val channel = channelRef.get().data(ChatChannel.serializer())
        
        val otherParticipantId = channel.participantIds.find { it != userId }
        
        val newMessage = ChatMessage(
            id = id,
            userId = userId,
            receiverId = otherParticipantId ?: "",
            senderName = senderName,
            text = text,
            sender = MessageSender.USER,
            timestamp = now,
            deliveredTo = emptyMap(),
            readBy = emptyMap(),
            deletedFor = emptyList()
        )
        
        val newUnreadCounts = channel.unreadCounts.toMutableMap()
        
        var shouldIncrementUnread = true
        if (otherParticipantId != null) {
            // Check if the other user has this channel as their active channel
            try {
                val otherUserDoc = firestore.collection("users").document(otherParticipantId).get()
                val activeChannel = otherUserDoc.get<String?>("activeChannelId")
                if (activeChannel == channelId) {
                    shouldIncrementUnread = false
                }
            } catch (_: Exception) {}
        }

        if (shouldIncrementUnread && otherParticipantId != null) {
            newUnreadCounts[otherParticipantId] = (newUnreadCounts[otherParticipantId] ?: 0) + 1
        }

        channelRef.collection("messages").document(id).set(ChatMessage.serializer(), newMessage)
        channelRef.updateFields {
            "lastMessage" to (text as Any?)
            "lastMessageId" to (id as Any?)
            "lastMessageTimestamp" to (now as Any?)
            "lastMessageSenderId" to (userId as Any?)
            "lastMessageDeliveredTo" to (emptyMap<String, Long>() as Any?)
            "lastMessageReadBy" to (emptyMap<String, Long>() as Any?)
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
        val now = Clock.System.now().toEpochMilliseconds()
        val messages = channelRef.collection("messages")
            .where { "userId" notEqualTo userId }
            .get()
            .documents
        
        messages.forEach { doc ->
            val msg = doc.data(ChatMessage.serializer())
            if (msg.userId != userId && !msg.readBy.containsKey(userId)) {
                doc.reference.updateFields {
                    "readBy.$userId" to now
                }
                
                // Update channel's lastMessageReadBy if this is the last message
                if (channel.lastMessageId == msg.id) {
                    channelRef.updateFields {
                        "lastMessageReadBy.$userId" to now
                    }
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

    override suspend fun deleteChannels(channelIds: List<String>) {
        val userId = authRepository.getCurrentUserId() ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Using a loop for simplicity, though a batch or transaction could be used
        channelIds.forEach { channelId ->
            try {
                val channelRef = channelsCollection.document(channelId)
                val channel = channelRef.get().data(ChatChannel.serializer())
                val newDeletedAt = channel.deletedAt.toMutableMap()
                newDeletedAt[userId] = now
                channelRef.updateFields { "deletedAt" to newDeletedAt }
            } catch (_: Exception) {}
        }
    }

    override suspend fun markMessageAsRead(channelId: String, messageId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val channelRef = channelsCollection.document(channelId)
        val now = Clock.System.now().toEpochMilliseconds()
        
        channelRef.collection("messages").document(messageId)
            .updateFields { "readBy.$uid" to now }

        // Update channel's lastMessageReadBy if this is the last message
        try {
            val channelData = channelRef.get().data(ChatChannel.serializer())
            if (channelData.lastMessageId == messageId) {
                channelRef.updateFields {
                    "lastMessageReadBy.$uid" to now
                }
            }
        } catch (_: Exception) {}
    }

    override suspend fun markMessageAsDelivered(
        channelId: String,
        messageId: String
    ) {
        val uid = authRepository.getCurrentUserId() ?: return
        val channelRef = channelsCollection.document(channelId)
        val now = Clock.System.now().toEpochMilliseconds()
        
        channelRef.collection("messages").document(messageId)
            .updateFields {
                "deliveredTo.$uid" to now
            }

        // Update channel's lastMessageDeliveredTo if this is the last message
        try {
            val channelData = channelRef.get().data(ChatChannel.serializer())
            if (channelData.lastMessageId == messageId) {
                channelRef.updateFields {
                    "lastMessageDeliveredTo.$uid" to now
                }
            }
        } catch (_: Exception) {}
    }

    override suspend fun deleteMessages(channelId: String, messageIds: List<String>) {
        val uid = authRepository.getCurrentUserId() ?: return
        val messagesCollection = channelsCollection.document(channelId).collection("messages")
        
        messageIds.forEach { messageId ->
            try {
                val docRef = messagesCollection.document(messageId)
                val msg = docRef.get().data(ChatMessage.serializer())
                val newDeletedFor = msg.deletedFor.toMutableList()
                if (!newDeletedFor.contains(uid)) {
                    newDeletedFor.add(uid)
                    docRef.updateFields { "deletedFor" to newDeletedFor }
                }
            } catch (_: Exception) {}
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

    override suspend fun setActiveChannel(channelId: String?) {
        val uid = authRepository.getCurrentUserId() ?: return
        firestore.collection("users").document(uid).updateFields {
            "activeChannelId" to channelId
        }
    }
}
