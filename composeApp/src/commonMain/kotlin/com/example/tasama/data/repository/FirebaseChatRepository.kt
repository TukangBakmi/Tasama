package com.example.tasama.data.repository

import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.PresenceRepository
import com.example.tasama.domain.repository.PresenceState
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

class FirebaseChatRepository(
    private val authRepository: AuthRepository,
    private val presenceRepository: PresenceRepository
) : ChatRepository {
    private val firestore = Firebase.firestore
    private val database = Firebase.database
    private val channelsCollection = firestore.collection("chat_channels")
    private var activeChannelsListenerCount = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sharedChannels: StateFlow<List<ChatChannel>> = authRepository.userId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                channelsCollection.where { "participantIds" contains uid }
                    .snapshots
                    .onStart {
                        activeChannelsListenerCount++
                        println("DEBUG: [CHAT] Firestore getChannels listener STARTED. Total active: $activeChannelsListenerCount")
                    }
                    .onCompletion {
                        activeChannelsListenerCount--
                        println("DEBUG: [CHAT] Firestore getChannels listener STOPPED. Total active: $activeChannelsListenerCount")
                    }
                    .map { snapshot ->
                        println("DEBUG: [CHAT] Firestore getChannels snapshot received for $uid. Documents: ${snapshot.documents.size}")
                        snapshot.documents.map { it.data(ChatChannel.serializer()) }
                            .filter { channel ->
                                val deletedAt = channel.deletedAt[uid] ?: 0L
                                deletedAt < channel.lastMessageTimestamp
                            }
                            .sortedByDescending { it.lastMessageTimestamp }
                    }
                    .catch { e ->
                        if (e.message?.contains("permission", ignoreCase = true) == true) {
                            println("DEBUG: [CHAT] Permission denied in getChannels (expected during logout)")
                        } else {
                            println("ERROR: [CHAT] Error in getChannels: ${e.message}")
                        }
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = (authRepository as FirebaseAuthRepository).applicationScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    override suspend fun cleanup() {
        println("DEBUG: [SESSION] Cleaning up FirebaseChatRepository")
        // No longer managing its own scope, tied to sessionScope
    }

    override fun getChannels(): Flow<List<ChatChannel>> = sharedChannels

    override fun getChannel(channelId: String): Flow<ChatChannel?> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else {
                channelsCollection.document(channelId).snapshots
                    .map { 
                        if (it.exists) it.data(ChatChannel.serializer()) else null
                    }
                    .catch { e ->
                        if (e.message?.contains("permission", ignoreCase = true) == true) {
                            println("DEBUG: [CHAT] Permission denied in getChannel (expected during logout)")
                        } else {
                            println("ERROR: [CHAT] Error in getChannel: ${e.message}")
                        }
                        emit(null)
                    }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMessages(channelId: String): Flow<List<ChatMessage>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                val channelRef = channelsCollection.document(channelId)
                
                channelRef.snapshots.flatMapLatest { channelSnapshot ->
                    val channel = try { channelSnapshot.data(ChatChannel.serializer()) } catch (e: Exception) { null }
                    val deletedAt = channel?.deletedAt?.get(uid) ?: 0L
                    
                    channelRef.collection("messages")
                        .where { "timestamp" greaterThan deletedAt }
                        .orderBy("timestamp", direction = Direction.DESCENDING)
                        .limit(20)
                        .snapshots()
                        .map { snapshot ->
                            val now = Clock.System.now().toEpochMilliseconds()
                            
                            // Optimization: Identify messages that need delivery acknowledgment
                            val undeliveredDocs = snapshot.documents.filter { doc ->
                                val msg = doc.data(ChatMessage.serializer())
                                msg.userId != uid && !msg.deliveredTo.containsKey(uid)
                            }

                            if (undeliveredDocs.isNotEmpty()) {
                                authRepository.sessionScope.launch {
                                    try {
                                        val batch = firestore.batch()
                                        undeliveredDocs.forEach { doc ->
                                            batch.updateFields(doc.reference) {
                                                "deliveredTo.$uid" to now
                                            }
                                        }
                                        
                                        // Update channel's lastMessageDeliveredTo if any of these are the last message
                                        val channelData = try { channelRef.get().data(ChatChannel.serializer()) } catch (_: Exception) { null }
                                        if (channelData != null && undeliveredDocs.any { it.id == channelData.lastMessageId }) {
                                            batch.updateFields(channelRef) {
                                                "lastMessageDeliveredTo.$uid" to now
                                            }
                                        }
                                        batch.commit()
                                    } catch (_: Exception) {}
                                }
                            }

                            snapshot.documents.mapNotNull { doc ->
                                try {
                                    val msg = doc.data(ChatMessage.serializer())
                                    msg.copy(isFromMe = msg.userId == uid)
                                } catch (e: Exception) {
                                    println("ERROR: [CHAT] Message decoding failed for ${doc.id}: ${e.message}")
                                    null
                                }
                            }
                            .filter { !it.deletedFor.contains(uid) }
                            .sortedBy { it.timestamp }
                        }
                }.catch { e ->
                    if (e.message?.contains("permission", ignoreCase = true) == true) {
                        println("DEBUG: [CHAT] Permission denied in getMessages (expected during logout)")
                    } else {
                        println("ERROR: [CHAT] Error in getMessages: ${e.message}")
                    }
                    emit(emptyList())
                }
            }
        }
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
                .mapNotNull { doc ->
                    try {
                        val msg = doc.data(ChatMessage.serializer())
                        msg.copy(isFromMe = msg.userId == uid)
                    } catch (e: Exception) {
                        println("ERROR: [CHAT] getMoreMessages decoding failed for ${doc.id}: ${e.message}")
                        null
                    }
                }
                .filter { !it.deletedFor.contains(uid) }
                .sortedBy { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun sendMessage(
        channelId: String,
        text: String,
        repliedMessageId: String?,
        repliedMessageSenderId: String?,
        repliedMessageSenderName: String?,
        repliedMessageText: String?,
        repliedMessageType: String?,
        repliedMessageTimestamp: Long?
    ) {
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
            deletedFor = emptyList(),
            repliedMessageId = repliedMessageId,
            repliedMessageSenderId = repliedMessageSenderId,
            repliedMessageSenderName = repliedMessageSenderName,
            repliedMessageText = repliedMessageText,
            repliedMessageType = repliedMessageType,
            repliedMessageTimestamp = repliedMessageTimestamp
        )
        
        val newUnreadCounts = channel.unreadCounts.toMutableMap()
        
        var shouldIncrementUnread = true
        val newDeliveredTo = mutableMapOf<String, Long>()
        val newReadBy = mutableMapOf<String, Long>()

        if (otherParticipantId != null) {
            // Check if the other user has this channel as their active channel
            try {
                val otherUserDoc = firestore.collection("users").document(otherParticipantId).get()
                val activeChannel = otherUserDoc.get<String?>("activeChannelId")
                
                // If they have the channel open, mark as read immediately
                if (activeChannel == channelId) {
                    shouldIncrementUnread = false
                    newDeliveredTo[otherParticipantId] = now
                    newReadBy[otherParticipantId] = now
                } else {
                    // Check if they are online to mark as delivered
                    try {
                        val presenceStatus = presenceRepository.getPresence(otherParticipantId).firstOrNull()
                        if (presenceStatus is PresenceState.Online) {
                            newDeliveredTo[otherParticipantId] = now
                        }
                    } catch (e: Exception) {
                        println("DEBUG: [CHAT] Error checking presence: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: [CHAT] Error fetching user active channel: ${e.message}")
            }
        }

        val finalMessage = newMessage.copy(
            deliveredTo = newDeliveredTo,
            readBy = newReadBy
        )

        if (shouldIncrementUnread && otherParticipantId != null) {
            newUnreadCounts[otherParticipantId] = (newUnreadCounts[otherParticipantId] ?: 0) + 1
        }

        channelRef.collection("messages").document(id).set(ChatMessage.serializer(), finalMessage)
        channelRef.updateFields {
            "lastMessage" to (text as Any?)
            "lastMessageId" to (id as Any?)
            "lastMessageTimestamp" to (now as Any?)
            "lastMessageSenderId" to (userId as Any?)
            "lastMessageDeliveredTo" to (newDeliveredTo as Any?)
            "lastMessageReadBy" to (newReadBy as Any?)
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
        
        // Also mark all messages in this channel as read for this user
        val now = Clock.System.now().toEpochMilliseconds()
        
        // Optimization: Only query messages that haven't been read by this user yet
        // We query from lastReadTime. The loop below will handle !msg.readBy.containsKey(userId)
        val lastReadTime = channel.lastMessageReadBy[userId] ?: 0L
        val unreadMessages = channelRef.collection("messages")
            .where { "timestamp" greaterThanOrEqualTo lastReadTime }
            .get()
            .documents

        if (unreadMessages.isEmpty() && (channel.unreadCounts[userId] ?: 0) == 0) return

        val batch = firestore.batch()
        val channelUpdates = mutableMapOf<String, Any?>()
        channelUpdates["unreadCounts"] = newUnreadCounts

        unreadMessages.forEach { doc ->
            val msg = doc.data(ChatMessage.serializer())
            if (msg.userId != userId && !msg.readBy.containsKey(userId)) {
                batch.updateFields(doc.reference) {
                    "readBy.$userId" to now
                }
                
                if (channel.lastMessageId == msg.id) {
                    channelUpdates["lastMessageReadBy.$userId"] = now
                }
            }
        }

        // Single update call for the channel document
        batch.updateFields(channelRef) {
            channelUpdates.forEach { (field, value) ->
                field to value
            }
        }
        batch.commit()
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

    override suspend fun restoreMessages(channelId: String, messageIds: List<String>) {
        val uid = authRepository.getCurrentUserId() ?: return
        val messagesCollection = channelsCollection.document(channelId).collection("messages")

        messageIds.forEach { messageId ->
            try {
                val docRef = messagesCollection.document(messageId)
                val msg = docRef.get().data(ChatMessage.serializer())
                val newDeletedFor = msg.deletedFor.toMutableList()
                if (newDeletedFor.contains(uid)) {
                    newDeletedFor.remove(uid)
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

    override fun getTypingUsers(channelId: String): Flow<Set<String>> {
        return database.reference("typing/$channelId").valueEvents.map { snapshot ->
            snapshot.children
                .filter { child -> child.child("isTyping").value<Boolean?>() == true }
                .mapNotNull { it.key }
                .toSet()
        }.catch { emit(emptySet()) }
    }

    override suspend fun setTypingStatus(channelId: String, isTyping: Boolean) {
        val uid = authRepository.getCurrentUserId() ?: return
        val typingRef = database.reference("typing/$channelId/$uid")
        if (isTyping) {
            typingRef.child("isTyping").setValue(true)
            typingRef.onDisconnect().removeValue()
        } else {
            typingRef.removeValue()
        }
    }
}
