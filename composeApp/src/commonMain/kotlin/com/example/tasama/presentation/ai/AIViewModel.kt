package com.example.tasama.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.data.remote.GeminiService
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.repository.AIChatRepository
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.random.Random

class AIViewModel(
    private val repository: TransactionRepository,
    private val aiChatRepository: AIChatRepository,
    private val geminiService: GeminiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            aiChatRepository.getMessages().collect { messages ->
                if (messages.isEmpty()) {
                    // Initial welcome message if no history
                    val welcomeMessage = ChatMessage(
                        id = "welcome",
                        text = "Halo! Saya adalah Tasama AI. Saya bisa membantu menganalisis transaksi Anda. Coba tanya 'Berapa total pengeluaran saya?' atau 'Berikan saran untuk hemat uang.'",
                        sender = MessageSender.AI,
                        timestamp = Clock.System.now().toEpochMilliseconds()
                    )
                    aiChatRepository.saveMessage(welcomeMessage)
                } else {
                    _uiState.update { it.copy(messages = messages) }
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText
        if (text.isBlank()) return

        val now = Clock.System.now().toEpochMilliseconds()
        val userMessage = ChatMessage(
            id = "user_$now",
            text = text,
            sender = MessageSender.USER,
            timestamp = now
        )

        viewModelScope.launch {
            aiChatRepository.saveMessage(userMessage)
            _uiState.update {
                it.copy(
                    inputText = "",
                    isTyping = true
                )
            }
            processAIResponse(text)
        }
    }

    private fun processAIResponse(userText: String) {
        viewModelScope.launch {
            val transactions = repository.getTransactions()
            
            // Get last 6 messages for context
            val history = _uiState.value.messages.takeLast(6).joinToString("\n") { 
                "${it.sender.name}: ${it.text}"
            }
            
            // Construct a prompt with transaction context
            val transactionContext = transactions.joinToString("\n") { 
                "- ${it.category}: Rp ${it.amount} (${it.type})"
            }

            val prompt = """
                Anda adalah Tasama AI, asisten keuangan pribadi yang ramah dan ahli dalam Bahasa Indonesia.
                
                DATA TRANSAKSI PENGGUNA:
                $transactionContext
                
                PERCAKAPAN TERAKHIR:
                $history
                
                INSTRUKSI:
                1. Gunakan data transaksi di atas untuk memberikan jawaban yang akurat dan spesifik.
                2. Jawab pertanyaan pengguna berikut: "$userText"
                3. Berikan saran keuangan yang bijaksana jika diminta.
                4. Tetap singkat, padat, dan membantu.
                5. Jawab dalam Bahasa Indonesia yang santun.
            """.trimIndent()

            val responseText = geminiService.generateContent(prompt)

            val now = Clock.System.now().toEpochMilliseconds()
            val aiMessage = ChatMessage(
                id = "ai_$now",
                text = responseText,
                sender = MessageSender.AI,
                timestamp = now
            )

            aiChatRepository.saveMessage(aiMessage)
            _uiState.update { it.copy(isTyping = false) }
        }
    }
}
