package com.example.tasama.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class AIViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()

    init {
        // Initial welcome message
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        id = "1",
                        text = "Halo! Saya adalah Tasama AI. Ada yang bisa saya bantu dengan keuangan Anda hari ini?",
                        sender = MessageSender.AI
                    )
                )
            )
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            id = Random.nextInt().toString(),
            text = text,
            sender = MessageSender.USER
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isTyping = true
            )
        }

        processAIResponse(text)
    }

    private fun processAIResponse(userText: String) {
        viewModelScope.launch {
            // Simulate AI thinking
            delay(1500)

            val responseText = if (userText.contains("makan", ignoreCase = true) || userText.contains("beli", ignoreCase = true)) {
                "Baik, saya telah mencatat pengeluaran tersebut. Apakah ada kategori khusus untuk ini?"
            } else if (userText.contains("saldo", ignoreCase = true)) {
                "Saldo Anda saat ini sedang dikalkulasi. Tunggu sebentar ya."
            } else {
                "Saya mengerti. Tasama AI siap membantu memonitor keuangan Anda."
            }

            val aiMessage = ChatMessage(
                id = Random.nextInt().toString(),
                text = responseText,
                sender = MessageSender.AI
            )

            _uiState.update {
                it.copy(
                    messages = it.messages + aiMessage,
                    isTyping = false
                )
            }
        }
    }
}
