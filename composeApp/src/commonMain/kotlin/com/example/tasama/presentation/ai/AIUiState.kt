package com.example.tasama.presentation.ai

import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.SavingsTransaction

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
    val selectedMessageIds: Set<String> = emptySet(),
    val error: String? = null
)

data class PendingCorrection(
    val originalTransaction: SavingsTransaction,
    val newTransaction: SavingsTransaction,
    val confirmationText: String
)
