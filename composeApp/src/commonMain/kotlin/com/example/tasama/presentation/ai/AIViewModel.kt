package com.example.tasama.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.data.remote.GroqService
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.AIChatRepository
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

class AIViewModel(
    private val repository: TransactionRepository,
    private val aiChatRepository: AIChatRepository,
    private val groqService: GroqService
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
                    // Only save welcome if we are sure there is no history (after initial check)
                    // and not because of a Firestore error
                    val welcomeMessage = ChatMessage(
                        id = "welcome",
                        text = "Halo! Saya adalah Sir Quack. Saya bisa membantu mencatat keuangan Anda di Tasama. Coba ketik 'Budi nabung 100k' atau 'Makan siang 50k'.",
                        sender = MessageSender.AI,
                        timestamp = Clock.System.now().toEpochMilliseconds()
                    )
                    aiChatRepository.saveMessage(welcomeMessage)
                } else {
                    _uiState.update { state ->
                        // Merge logic: Keep paged messages (older than the latest batch)
                        // but replace the latest batch with the new data from the flow
                        val latestOldestTimestamp = messages.firstOrNull()?.timestamp ?: 0L
                        val pagedMessages = state.messages.filter { it.timestamp < latestOldestTimestamp }
                        
                        val combined = (pagedMessages + messages)
                            .distinctBy { it.id }
                            .sortedBy { it.timestamp }
                        state.copy(messages = combined)
                    }
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun loadMoreMessages() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMessages) return

        val oldestMessage = _uiState.value.messages.firstOrNull { it.id != "welcome" } ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            
            val moreMessages = aiChatRepository.getMoreMessages(
                limit = 20,
                beforeTimestamp = oldestMessage.timestamp
            )
            
            if (moreMessages.isEmpty()) {
                _uiState.update { it.copy(isLoadingMore = false, hasMoreMessages = false) }
            } else {
                _uiState.update { state ->
                    val combined = (moreMessages + state.messages)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                    state.copy(
                        messages = combined,
                        isLoadingMore = false,
                        hasMoreMessages = moreMessages.size >= 20
                    )
                }
            }
        }
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
            val prompt = """
                Anda adalah Sir Quack, asisten keuangan pribadi yang cerdas dan ramah dalam aplikasi Tasama. 
                Tugas Anda adalah memparsing input pengguna menjadi satu atau lebih transaksi keuangan dalam format JSON.

                ATURAN PARSING:
                1. FORMAT INCOME: "[Nama] [Amount] [Note (opsional)]"
                   - Nama Pengguna WAJIB ada di depan.
                   - Urutan: Nama -> Nominal -> Catatan.
                   - Contoh: "Budi 100k nabung" -> Category: Budi, Type: INCOME, Amount: 100000, Note: nabung.

                2. FORMAT EXPENSE: "[Amount] [Note]"
                   - Urutan: Nominal -> Catatan.
                   - Nominal WAJIB di depan Catatan.
                   - Contoh: "20k kopi" -> Category: General, Type: EXPENSE, Amount: 20000, Note: kopi.

                3. FORMAT EXPENSE PRIBADI: "[Nama] [Amount] [Note]"
                   - JIKA Nama disebutkan di awal (misal: "Budi 50k bakso"):
                     ARTINYA Budi mengeluarkan uang pribadi untuk pengeluaran bersama.
                     WAJIB BUAT 2 TRANSAKSI SEKALIGUS:
                     a. Type: INCOME, Category: [Nama], Amount: [Amount], Note: "nabung"
                     b. Type: EXPENSE, Category: "General", Amount: [Amount], Note: [Note]

                PENTING:
                - Jika input hanya "income [amount]" tanpa menyebutkan Nama, set "is_transaction" ke false.
                - Jangan pernah menebak nama jika tidak ada.
                - Jika "is_transaction" false, beri tahu pengguna bahwa nama atau urutan nominal harus benar.

                KATEGORI KHUSUS:
                - Bunga: "Bunga [Amount]" -> Category: "Bunga", Type: INCOME, Note: "bunga"
                - Cashback: "Cashback [Amount]" -> Category: "Cashback", Type: INCOME, Note: "cashback"

                KONVERSI NOMINAL:
                - "k" = x1.000, "jt" = x1.000.000 (contoh: 100k -> 100000, 1.5jt -> 1500000).

                WAJIB merespons HANYA dengan format JSON yang valid. Jangan berikan teks pembuka atau penutup di luar blok JSON.
                {
                  "is_transaction": true,
                  "transactions": [
                    {
                      "type": "INCOME" | "EXPENSE",
                      "amount": number,
                      "category": "string (Nama atau Kategori)",
                      "note": "string"
                    }
                  ],
                  "reply": "Kalimat konfirmasi ramah yang diawali dengan 'Transaksi berhasil dicatat! ' diikuti detail singkat (Nama, Nominal, dan Catatan)."
                }
                Jika bukan perintah transaksi (seperti menyapa atau obrolan lain) atau format salah, set "is_transaction" ke false, "transactions" ke [], dan isi "reply" dengan jawaban normal atau petunjuk Anda.

                INPUT USER: "$userText"
            """.trimIndent()

            val responseText = groqService.generateContent(prompt)
            handleAIResponse(responseText)
        }
    }

    private suspend fun handleAIResponse(response: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        var finalReply = response

        try {
            // Cek apakah response mengandung JSON
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}")
            
            if (jsonStart != -1 && jsonEnd != -1) {
                val jsonStr = response.substring(jsonStart, jsonEnd + 1)
                val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject
                
                val isTransaction = jsonElement["is_transaction"]?.jsonPrimitive?.booleanOrNull ?: false
                if (isTransaction) {
                    val transactionsArray = jsonElement["transactions"]?.jsonArray
                    if (transactionsArray != null) {
                        transactionsArray.forEachIndexed { index, item ->
                            val data = item.jsonObject
                            val typeStr = data["type"]?.jsonPrimitive?.content ?: "EXPENSE"
                            val type = if (typeStr == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                            val amount = data["amount"]?.jsonPrimitive?.longOrNull ?: 0L
                            val category = data["category"]?.jsonPrimitive?.content ?: "General"
                            val note = data["note"]?.jsonPrimitive?.content ?: ""
                            
                            // Simpan ke Firestore
                            repository.addTransaction(
                                Transaction(
                                    id = "ai_${now}_$index",
                                    amount = amount,
                                    type = type,
                                    category = category,
                                    note = note,
                                    createdAt = now
                                )
                            )
                        }
                    }
                    finalReply = jsonElement["reply"]?.jsonPrimitive?.content ?: response
                } else {
                    finalReply = jsonElement["reply"]?.jsonPrimitive?.content ?: response
                }
            }
        } catch (e: Exception) {
            // Jika gagal parsing, gunakan response asli
        }

        val aiMessage = ChatMessage(
            id = "ai_$now",
            text = finalReply,
            sender = MessageSender.AI,
            timestamp = now
        )

        aiChatRepository.saveMessage(aiMessage)
        _uiState.update { it.copy(isTyping = false) }
    }
}
