package com.example.tasama.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.data.remote.GroqService
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.model.MessageSender
import com.example.tasama.domain.model.SavingsTransaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.AIChatRepository
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import kotlinx.coroutines.Job
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
    private val savingsRepository: SavingsRepository,
    private val aiChatRepository: AIChatRepository,
    private val groqService: GroqService,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()

    private var dataJob: Job? = null
    private var lastTxJob: Job? = null

    init {
        observeUserSession()
    }

    private fun observeUserSession() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                if (uid == null) {
                    dataJob?.cancel()
                    _uiState.value = AIUiState()
                } else {
                    observeMessages()
                    observeSavingsSpaces()
                }
            }
        }
    }

    private fun observeSavingsSpaces() {
        viewModelScope.launch {
            savingsRepository.getSavingsSpaces().collect { spaces ->
                _uiState.update { state ->
                    state.copy(
                        savingsSpaces = spaces,
                        activeSpaceId = state.activeSpaceId ?: spaces.firstOrNull()?.id
                    )
                }
            }
        }
    }

    fun setActiveSpace(spaceId: String) {
        _uiState.update { it.copy(activeSpaceId = spaceId) }
        observeLastTransaction(spaceId)
    }

    private fun observeLastTransaction(spaceId: String) {
        lastTxJob?.cancel()
        lastTxJob = viewModelScope.launch {
            savingsRepository.getTransactions(spaceId).collect { transactions ->
                _uiState.update { it.copy(lastTransaction = transactions.firstOrNull()) }
            }
        }
    }

    private fun observeMessages() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun loadMoreMessages() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMessages) return

        val oldestMessage = _uiState.value.messages.firstOrNull { it.id != "welcome" } ?: return
        
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false, error = e.message ?: "Failed to load messages") }
            }
        }
    }

    fun sendMessage() {
        val trimmedText = _uiState.value.inputText.trim()
        if (trimmedText.isEmpty()) return

        val now = Clock.System.now().toEpochMilliseconds()
        val userMessage = ChatMessage(
            id = "user_$now",
            text = trimmedText,
            sender = MessageSender.USER,
            timestamp = now,
            isFromMe = true
        )

        viewModelScope.launch {
            try {
                aiChatRepository.saveMessage(userMessage)
                _uiState.update {
                    it.copy(
                        inputText = "",
                        isTyping = true
                    )
                }
                processAIResponse(trimmedText)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to send message") }
            }
        }
    }

    private fun processAIResponse(userText: String) {
        val activeSpace = _uiState.value.savingsSpaces.find { it.id == _uiState.value.activeSpaceId }
        val spaceName = activeSpace?.name ?: "Personal"
        val membersList = activeSpace?.members?.joinToString(", ") { it.name } ?: "Anda"

        viewModelScope.launch {
            val prompt = """
                Anda adalah Sir Quack, asisten keuangan pribadi yang cerdas dan ramah dalam aplikasi Tasama. 
                Anda saat ini berada di dalam Savings Space: "$spaceName".
                Anggota yang ada di space ini: $membersList.

                Tugas Anda adalah:
                1. Membantu pengguna mencatat transaksi keuangan (nabung/pengeluaran).
                2. Menjawab pertanyaan umum atau sekadar mengobrol santai (casual chat).
                3. Selalu bersikap ramah dan menggunakan persona bebek yang cerdas (Sir Quack).

                ATURAN TRANSAKSI:
                - Jika pengguna ingin menabung (income): "[Nama] [Amount] [Note (opsional)]"
                  Contoh: "Budi 100k nabung" -> Type: INCOME, Amount: 100000, Note: nabung.
                - Jika pengguna ingin mencatat pengeluaran (expense): "[Amount] [Note]"
                  Contoh: "20k kopi" -> Type: EXPENSE, Amount: 20000, Note: kopi.
                - Jika menyebut nama di awal untuk pengeluaran (misal: "Budi 50k bakso"), artinya Budi menabung dulu lalu dipakai belanja. Buat 2 transaksi:
                  a. Type: INCOME, Category: Budi, Amount: 50000, Note: nabung
                  b. Type: EXPENSE, Category: General, Amount: 50000, Note: bakso
                - Jika pengguna ingin UNDO (membatalkan) transaksi terakhir: "[UNDO]"
                  Contoh: "undo transaksi terakhir", "batalin dong" -> is_undo: true.
                - Jika pengguna ingin KOREKSI (mengubah) transaksi terakhir: "[CORRECTION]"
                  Contoh: "salah, harusnya 50k", "ganti jumlahnya jadi 100k" -> is_correction: true, mention new amount/note.

                KONVERSI NOMINAL:
                - "k" = x1.000, "jt" = x1.000.000.

                OUTPUT FORMAT (JSON):
                Jika terdeteksi niat transaksi baru, isi `is_transaction: true`.
                Jika terdeteksi niat undo, isi `is_undo: true`.
                Jika terdeteksi niat koreksi, isi `is_correction: true`.
                Jika hanya chat biasa, isi semua false.

                {
                  "is_transaction": boolean,
                  "is_undo": boolean,
                  "is_correction": boolean,
                  "transactions": [ ... sama seperti sebelumnya ... ],
                  "correction_data": {
                     "amount": number (optional),
                     "note": "string (optional)"
                  },
                  "reply": "Respon konfirmasi atau jawaban chat"
                }

                INFO TRANSAKSI TERAKHIR (Gunakan jika is_undo atau is_correction):
                ${_uiState.value.lastTransaction?.let { "ID: ${it.id}, Amount: ${it.amount}, Note: ${it.note}" } ?: "Tidak ada transaksi terbaru"}

                INPUT USER: "$userText"
            """.trimIndent()

            val responseText = groqService.generateContent(prompt)
            handleAIResponse(responseText)
        }
    }

    private suspend fun handleAIResponse(response: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        val activeSpaceId = _uiState.value.activeSpaceId
        val userId = authRepository.getCurrentUserId() ?: ""
        val lastTx = _uiState.value.lastTransaction
        var finalReply = response

        try {
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}")
            
            if (jsonStart != -1 && jsonEnd != -1) {
                val jsonStr = response.substring(jsonStart, jsonEnd + 1)
                val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject
                
                val isTransaction = jsonElement["is_transaction"]?.jsonPrimitive?.booleanOrNull ?: false
                val isUndo = jsonElement["is_undo"]?.jsonPrimitive?.booleanOrNull ?: false
                val isCorrection = jsonElement["is_correction"]?.jsonPrimitive?.booleanOrNull ?: false

                if (isUndo && activeSpaceId != null && lastTx != null) {
                    savingsRepository.deleteTransaction(activeSpaceId, lastTx.id)
                    finalReply = jsonElement["reply"]?.jsonPrimitive?.content ?: "Oke, transaksi Rp${lastTx.amount} sudah saya batalkan."
                } else if (isCorrection && activeSpaceId != null && lastTx != null) {
                    val correctionData = jsonElement["correction_data"]?.jsonObject
                    val newAmount = correctionData?.get("amount")?.jsonPrimitive?.longOrNull?.toDouble() ?: lastTx.amount
                    val newNote = correctionData?.get("note")?.jsonPrimitive?.content ?: lastTx.note
                    
                    val newTx = lastTx.copy(amount = newAmount, note = newNote)
                    
                    val confirmText = jsonElement["reply"]?.jsonPrimitive?.content 
                        ?: "Saya akan ubah transaksi terakhir dari Rp${lastTx.amount} menjadi Rp$newAmount. Konfirmasi?"
                    
                    _uiState.update { it.copy(
                        pendingCorrection = PendingCorrection(lastTx, newTx, confirmText),
                        isTyping = false
                    ) }
                    
                    // Add AI message for confirmation
                    val aiMessage = ChatMessage(
                        id = "ai_$now",
                        text = confirmText,
                        sender = MessageSender.AI,
                        timestamp = now
                    )
                    aiChatRepository.saveMessage(aiMessage)
                    return // Wait for user confirmation
                } else if (isTransaction && activeSpaceId != null) {
                    val transactionsArray = jsonElement["transactions"]?.jsonArray
                    transactionsArray?.forEachIndexed { index, item ->
                        val data = item.jsonObject
                        val typeStr = data["type"]?.jsonPrimitive?.content ?: "EXPENSE"
                        val type = if (typeStr == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                        val amount = data["amount"]?.jsonPrimitive?.longOrNull?.toDouble() ?: 0.0
                        val category = data["category"]?.jsonPrimitive?.content ?: "General"
                        val note = data["note"]?.jsonPrimitive?.content ?: ""

                        // Simpan ke Savings Space
                        savingsRepository.addTransaction(
                            spaceId = activeSpaceId,
                            transaction = SavingsTransaction(
                                id = "ai_${now}_$index",
                                spaceId = activeSpaceId,
                                userId = userId,
                                userName = category, // In this context, category often stores the name
                                amount = amount,
                                type = type,
                                note = note,
                                timestamp = now
                            )
                        )
                    }
                    finalReply = jsonElement["reply"]?.jsonPrimitive?.content ?: response
                } else {
                    finalReply = jsonElement["reply"]?.jsonPrimitive?.content ?: response
                }
            }
        } catch (_: Exception) { }

        val aiMessage = ChatMessage(
            id = "ai_$now",
            text = finalReply,
            sender = MessageSender.AI,
            timestamp = now
        )

        aiChatRepository.saveMessage(aiMessage)
        _uiState.update { it.copy(isTyping = false) }
    }

    fun confirmCorrection() {
        val pending = _uiState.value.pendingCorrection ?: return
        val spaceId = _uiState.value.activeSpaceId ?: return
        
        viewModelScope.launch {
            try {
                savingsRepository.updateTransaction(spaceId, pending.newTransaction)
                _uiState.update { it.copy(pendingCorrection = null) }
                
                val now = Clock.System.now().toEpochMilliseconds()
                val aiMessage = ChatMessage(
                    id = "ai_$now",
                    text = "Berhasil diperbarui! Transaksi sekarang menjadi Rp${pending.newTransaction.amount}.",
                    sender = MessageSender.AI,
                    timestamp = now
                )
                aiChatRepository.saveMessage(aiMessage)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update transaction") }
            }
        }
    }

    fun cancelCorrection() {
        _uiState.update { it.copy(pendingCorrection = null) }
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val aiMessage = ChatMessage(
                id = "ai_$now",
                text = "Oke, perubahan dibatalkan.",
                sender = MessageSender.AI,
                timestamp = now
            )
            aiChatRepository.saveMessage(aiMessage)
        }
    }

    fun undoLastTransaction() {
        val lastTx = _uiState.value.lastTransaction ?: return
        val spaceId = _uiState.value.activeSpaceId ?: return
        
        viewModelScope.launch {
            try {
                savingsRepository.deleteTransaction(spaceId, lastTx.id)
                val now = Clock.System.now().toEpochMilliseconds()
                val aiMessage = ChatMessage(
                    id = "ai_$now",
                    text = "Transaksi Rp${lastTx.amount} berhasil dibatalkan.",
                    sender = MessageSender.AI,
                    timestamp = now
                )
                aiChatRepository.saveMessage(aiMessage)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to undo transaction") }
            }
        }
    }

    fun toggleMessageSelection(messageId: String) {
        _uiState.update { state ->
            val newSelection = state.selectedMessageIds.toMutableSet()
            if (newSelection.contains(messageId)) {
                newSelection.remove(messageId)
            } else {
                newSelection.add(messageId)
            }
            state.copy(selectedMessageIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedMessageIds = emptySet()) }
    }

    fun deleteSelectedMessages() {
        val selectedIds = _uiState.value.selectedMessageIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            aiChatRepository.deleteMessages(selectedIds)
            _uiState.update { it.copy(selectedMessageIds = emptySet()) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            aiChatRepository.clearHistory()
        }
    }
}
