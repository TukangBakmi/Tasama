package com.example.tasama.presentation.ai

import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.SavingsTransaction
import com.example.tasama.presentation.components.TransientFeedback

data class AIUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isTyping: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val savingsSpaces: List<SavingsSpace> = emptyList(),
    val activeSpaceId: String? = null,
    val lastTransaction: SavingsTransaction? = null,
    val pendingCorrection: PendingCorrection? = null,
    val pendingSpaceTransaction: PendingSpaceTransaction? = null,
    val selectedMessageIds: Set<String> = emptySet(),
    val currentUserName: String = "User",
    val error: String? = null,
    val undoableTransaction: UndoableTransaction? = null
)

data class UndoableTransaction(
    val transactionId: String,
    val spaceId: String,
    val messageId: String,
    val expiryTime: Long
)

data class PendingCorrection(
    val originalTransaction: SavingsTransaction,
    val newTransaction: SavingsTransaction,
    val confirmationText: String
)

data class PendingSpaceTransaction(
    val spaceId: String,
    val spaceName: String,
    val transactions: List<SavingsTransaction>,
    val confirmationText: String
)
