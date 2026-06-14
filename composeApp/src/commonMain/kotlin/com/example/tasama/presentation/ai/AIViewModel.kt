package com.example.tasama.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.data.remote.GeminiService
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class AIViewModel(
    private val repository: TransactionRepository,
    private val geminiService: GeminiService
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
                        text = "Halo! Saya adalah Tasama AI. Saya bisa membantu menganalisis transaksi Anda. Coba tanya 'Berapa total pengeluaran saya?' atau 'Berikan saran untuk hemat uang.'",
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
            val transactions = repository.getTransactions()
            
            // Construct a prompt with transaction context
            val transactionContext = transactions.joinToString("\n") { 
                "- ${it.category}: Rp ${it.amount} (${it.type})"
            }

            val prompt = """
                Anda adalah Tasama AI, asisten keuangan pribadi yang ramah.
                Berikut adalah data transaksi pengguna:
                $transactionContext
                
                Gunakan data di atas untuk menjawab pertanyaan berikut dari pengguna:
                "$userText"
                
                Berikan jawaban yang singkat, membantu, dan dalam Bahasa Indonesia.
            """.trimIndent()

            val responseText = geminiService.generateContent(prompt)

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
