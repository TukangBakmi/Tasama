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
import com.example.tasama.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant

class AIViewModel(
    private val savingsRepository: SavingsRepository,
    private val aiChatRepository: AIChatRepository,
    private val groqService: GroqService,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()

    private var dataJob: Job? = null
    private var lastTxJob: Job? = null
    private var undoTimerJob: Job? = null
    private var settingsJob: Job? = null

    init {
        observeUserSession()
        observeSettings()
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val createdAt = settings.undoCreatedAt
                val now = Clock.System.now().toEpochMilliseconds()
                
                if (createdAt != null && (now - createdAt) < 60000L) {
                    println("DEBUG: Setting undoableTransaction: ${settings.undoTransactionId}, msg: ${settings.undoMessageId}")
                    _uiState.update { 
                        it.copy(
                            undoableTransaction = UndoableTransaction(
                                transactionId = settings.undoTransactionId ?: "",
                                spaceId = settings.undoSpaceId ?: "",
                                messageId = settings.undoMessageId ?: "",
                                expiryTime = createdAt + 60000L
                            )
                        )
                    }
                    startUndoExpiryTimer(createdAt)
                } else {
                    if (createdAt != null) {
                        settingsRepository.setUndoTransaction(null, null, null, null)
                    }
                    _uiState.update { it.copy(undoableTransaction = null) }
                }
            }
        }
    }

    private fun startUndoExpiryTimer(createdAt: Long) {
        undoTimerJob?.cancel()
        val now = Clock.System.now().toEpochMilliseconds()
        val remaining = 60000L - (now - createdAt)
        
        if (remaining > 0) {
            undoTimerJob = viewModelScope.launch {
                delay(remaining)
                settingsRepository.setUndoTransaction(null, null, null, null)
                _uiState.update { it.copy(undoableTransaction = null) }
            }
        }
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
                    fetchCurrentUserName(uid)
                }
            }
        }
    }

    private fun fetchCurrentUserName(uid: String) {
        viewModelScope.launch {
            val name = authRepository.getUserName(uid) ?: "User"
            _uiState.update { it.copy(currentUserName = name) }
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
                - Jika pengguna ingin menabung (income): Panggil tool `CREATE_TRANSACTION` dengan type INCOME.
                - Jika pengguna ingin mencatat pengeluaran (expense): Panggil tool `CREATE_TRANSACTION` dengan type EXPENSE.
                - Jika pengguna ingin membatalkan transaksi terakhir: Panggil tool `DELETE_TRANSACTION`.
                - Jika pengguna ingin memperbaiki kesalahan data transaksi terakhir (seperti nominal salah): Panggil tool `UPDATE_TRANSACTION`.

                KONVERSI NOMINAL:
                - "k" = x1.000, "jt" = x1.000.000. (Contoh: "50k" -> 50000, "1.5jt" -> 1500000)

                OUTPUT FORMAT (JSON):
                Pesan harus mengandung salah satu intent tool call jika terdeteksi niat manipulasi data.
                
                {
                  "action": "CREATE_TRANSACTION" | "UPDATE_TRANSACTION" | "DELETE_TRANSACTION" | "NONE",
                  "transactions": [ 
                    { "type": "INCOME"|"EXPENSE", "amount": number, "category": "string", "note": "string" }
                  ],
                  "correction_data": {
                     "amount": number (optional),
                     "note": "string (optional)"
                  },
                  "reply": "Respon awal ke pengguna sebelum aksi dilakukan"
                }

                INFO TRANSAKSI TERAKHIR (Gunakan jika action adalah UPDATE_TRANSACTION atau DELETE_TRANSACTION):
                ${_uiState.value.lastTransaction?.let { "ID: ${it.id}, Amount: ${it.amount}, Note: ${it.note}" } ?: "Tidak ada transaksi terbaru"}
                Catatan: Jika user mengoreksi nominal (misal: "salah, harusnya 50k"), gunakan UPDATE_TRANSACTION dengan `amount` baru.

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
                
                val action = jsonElement["action"]?.jsonPrimitive?.content ?: "NONE"
                val aiReply = jsonElement["reply"]?.jsonPrimitive?.content ?: response

                when (action) {
                    "DELETE_TRANSACTION" -> {
                        if (activeSpaceId != null && lastTx != null) {
                            try {
                                savingsRepository.deleteTransaction(activeSpaceId, lastTx.id)
                                finalReply = aiReply
                                saveAiMessage(finalReply)
                                _uiState.update { 
                                    if (it.undoableTransaction?.transactionId == lastTx.id) {
                                        it.copy(undoableTransaction = null, isTyping = false)
                                    } else it.copy(isTyping = false)
                                }
                                return
                            } catch (e: Exception) {
                                finalReply = "Gagal membatalkan transaksi: ${e.message}"
                            }
                        } else {
                            finalReply = "Tidak ada transaksi terakhir yang bisa dibatalkan."
                        }
                    }
                    "UPDATE_TRANSACTION" -> {
                        if (activeSpaceId != null && lastTx != null) {
                            val correctionData = jsonElement["correction_data"]?.jsonObject
                            val newAmount = correctionData?.get("amount")?.jsonPrimitive?.doubleOrNull ?: lastTx.amount
                            val newNote = correctionData?.get("note")?.jsonPrimitive?.content ?: lastTx.note
                            
                            val newTx = lastTx.copy(amount = newAmount, note = newNote)
                            
                            val confirmText = aiReply
                            
                            _uiState.update { it.copy(
                                pendingCorrection = PendingCorrection(lastTx, newTx, confirmText),
                                isTyping = false
                            ) }
                            
                            saveAiMessage(confirmText)
                            return 
                        } else {
                            finalReply = "Tidak ada transaksi untuk diperbaiki."
                        }
                    }
                    "CREATE_TRANSACTION" -> {
                        if (activeSpaceId != null) {
                            val transactionsArray = jsonElement["transactions"]?.jsonArray
                            var successCount = 0
                            var lastCreatedTxId: String? = null
                            
                            transactionsArray?.forEachIndexed { index, item ->
                                try {
                                    val data = item.jsonObject
                                    val typeStr = data["type"]?.jsonPrimitive?.content ?: "EXPENSE"
                                    val type = if (typeStr == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                                    val amount = data["amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                    val category = data["category"]?.jsonPrimitive?.content ?: "General"
                                    val note = data["note"]?.jsonPrimitive?.content ?: ""

                                    val txId = "ai_${Clock.System.now().toEpochMilliseconds()}_$index"
                                    savingsRepository.addTransaction(
                                        activeSpaceId,
                                        SavingsTransaction(
                                            id = txId,
                                            spaceId = activeSpaceId,
                                            userId = userId,
                                            userName = category,
                                            amount = amount,
                                            type = type,
                                            note = note,
                                            timestamp = now
                                        )
                                    )
                                    successCount++
                                    lastCreatedTxId = txId
                                } catch (_: Exception) {}
                            }
                            
                            if (successCount > 0) {
                                finalReply = aiReply
                                val aiMsgId = saveAiMessage(finalReply)
                                if (lastCreatedTxId != null) {
                                    startUndoTimer(lastCreatedTxId, activeSpaceId, aiMsgId)
                                }
                                _uiState.update { it.copy(isTyping = false) }
                                return
                            } else {
                                finalReply = "Gagal mencatat transaksi."
                            }
                        }
                    }
                    else -> {
                        finalReply = aiReply
                    }
                }
            }
        } catch (_: Exception) { }

        saveAiMessage(finalReply)
        _uiState.update { it.copy(isTyping = false) }
    }

    private suspend fun saveAiMessage(text: String): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "ai_$now"
        val aiMessage = ChatMessage(
            id = id,
            text = text,
            sender = MessageSender.AI,
            timestamp = now
        )
        aiChatRepository.saveMessage(aiMessage)
        return id
    }

    private fun startUndoTimer(transactionId: String, spaceId: String, messageId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        println("DEBUG: startUndoTimer for msg: $messageId")
        viewModelScope.launch {
            settingsRepository.setUndoTransaction(transactionId, spaceId, messageId, now)
        }
    }

    fun undoTransaction(spaceId: String, transactionId: String) {
        viewModelScope.launch {
            try {
                // Check if transaction still exists before deleting
                // The repository's deleteTransaction might fail if it doesn't exist, which is fine
                savingsRepository.deleteTransaction(spaceId, transactionId)
                
                settingsRepository.setUndoTransaction(null, null, null, null)
                _uiState.update { it.copy(undoableTransaction = null) }
                
                saveAiMessage("Transaksi berhasil dibatalkan!")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Gagal membatalkan: ${e.message}") }
            }
        }
    }

    fun confirmCorrection() {
        val pending = _uiState.value.pendingCorrection ?: return
        val spaceId = _uiState.value.activeSpaceId ?: return
        
        viewModelScope.launch {
            try {
                savingsRepository.updateTransaction(spaceId, pending.newTransaction)
                _uiState.update { it.copy(pendingCorrection = null) }
                
                saveAiMessage("Berhasil diperbarui! Transaksi sekarang menjadi Rp${pending.newTransaction.amount}.")
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
                
                val currentUndo = _uiState.value.undoableTransaction
                if (currentUndo?.transactionId == lastTx.id) {
                    settingsRepository.setUndoTransaction(null, null, null, null)
                    _uiState.update { it.copy(undoableTransaction = null) }
                }

                saveAiMessage("Transaksi Rp${lastTx.amount} berhasil dibatalkan.")
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

    fun restoreMessages(messages: List<ChatMessage>) {
        viewModelScope.launch {
            aiChatRepository.restoreMessages(messages)
        }
    }

    fun getSelectedMessagesText(): String {
        val state = _uiState.value
        val selectedIds = state.selectedMessageIds
        if (selectedIds.isEmpty()) return ""

        return state.messages
            .filter { it.id in selectedIds }
            .sortedBy { it.timestamp }
            .joinToString("\n") { message ->
                val instant = Instant.fromEpochMilliseconds(message.timestamp)
                val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                val month = localDateTime.monthNumber
                val day = localDateTime.dayOfMonth
                val hour = localDateTime.hour.toString().padStart(2, '0')
                val minute = localDateTime.minute.toString().padStart(2, '0')
                
                val senderName = if (message.sender == MessageSender.AI) "Sir Quack" else state.currentUserName
                "[$month/$day, $hour:$minute] $senderName: ${message.text}"
            }
    }

    fun clearHistory() {
        viewModelScope.launch {
            aiChatRepository.clearHistory()
        }
    }
}
