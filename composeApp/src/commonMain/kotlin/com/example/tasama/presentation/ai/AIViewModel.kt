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
import com.example.tasama.util.formatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.tasama.data.remote.GroqException
import com.example.tasama.presentation.components.TransientFeedback
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

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
    private var spacesJob: Job? = null

    init {
        observeUserSession()
        observeSettings()
        observeSavingsSpaces()
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
        
        if (remaining > 0L) {
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
        spacesJob?.cancel()
        spacesJob = viewModelScope.launch {
            combine(
                savingsRepository.getSavingsSpaces(),
                settingsRepository.settings
            ) { spaces, settings ->
                val lastSelectedId = settings.lastAiSelectedSpaceId
                val resolvedActiveSpaceId = when {
                    spaces.isEmpty() -> null
                    lastSelectedId != null && spaces.any { it.id == lastSelectedId } -> lastSelectedId
                    else -> {
                        // If lastSelectedId is stale, clean it up
                        if (lastSelectedId != null) {
                            viewModelScope.launch {
                                settingsRepository.updateLastAiSelectedSpaceId(null)
                            }
                        }
                        spaces.firstOrNull()?.id
                    }
                }
                Pair(spaces, resolvedActiveSpaceId)
            }.collect { (spaces, resolvedActiveSpaceId) ->
                _uiState.update { state ->
                    if (resolvedActiveSpaceId != state.activeSpaceId && resolvedActiveSpaceId != null) {
                        observeLastTransaction(resolvedActiveSpaceId)
                    }

                    state.copy(
                        savingsSpaces = spaces,
                        activeSpaceId = resolvedActiveSpaceId
                    )
                }
            }
        }
    }

    fun setActiveSpace(spaceId: String) {
        viewModelScope.launch {
            settingsRepository.updateLastAiSelectedSpaceId(spaceId)
        }
        _uiState.update { it.copy(activeSpaceId = spaceId) }
        observeLastTransaction(spaceId)
    }

    private fun observeLastTransaction(spaceId: String) {
        lastTxJob?.cancel()
        lastTxJob = viewModelScope.launch {
            savingsRepository.getTransactions(spaceId).collect { transactions ->
                val last = transactions.sortedByDescending { it.timestamp }.firstOrNull()
                _uiState.update { it.copy(lastTransaction = last) }
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
                processAIResponse(trimmedText, userMessage.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to send message") }
            }
        }
    }

    private fun processAIResponse(userText: String, userMessageId: String) {
        val activeSpace = _uiState.value.savingsSpaces.find { it.id == _uiState.value.activeSpaceId }
        val spaceName = activeSpace?.name ?: "Personal"
        val membersList = activeSpace?.members?.joinToString(", ") { it.name } ?: "Anda"
        val allSpaces = _uiState.value.savingsSpaces.joinToString(", ") { it.name }

        viewModelScope.launch {
            val prompt = """
                Anda adalah Sir Quack, asisten keuangan pribadi yang cerdas dan ramah dalam aplikasi Tasama. 
                Anda saat ini berada di dalam Savings Space: "$spaceName".
                Anggota yang ada di space ini: $membersList.

                Daftar semua Savings Space yang tersedia untuk pengguna: $allSpaces.

                Tugas Anda adalah:
                1. Membantu pengguna mencatat transaksi keuangan (nabung/pengeluaran).
                2. Menjawab pertanyaan umum atau sekadar mengobrol santai (casual chat).
                3. Selalu bersikap ramah dan menggunakan persona bebek yang cerdas (Sir Quack).

                DETEKSI SAVINGS SPACE:
                - Perhatikan apakah pengguna menyebutkan nama Savings Space lain dari daftar di atas dalam pesan mereka.
                - Jika pengguna menyebutkan nama space (misal: "di Japan", "untuk Nikah"), identifikasi space tersebut.
                - Jika tidak ada space yang disebutkan, gunakan space saat ini ("$spaceName").

                ATURAN TRANSAKSI:
                - Jika pengguna ingin menabung (income): Panggil tool `CREATE_TRANSACTION` dengan type INCOME.
                - Jika pengguna ingin mencatat pengeluaran (expense): Panggil tool `CREATE_TRANSACTION` dengan type EXPENSE.
                - Jika pengguna ingin membatalkan transaksi terakhir: Panggil tool `DELETE_TRANSACTION`.
                - Jika pengguna ingin memperbaiki kesalahan data transaksi terakhir (seperti nominal salah): Panggil tool `UPDATE_TRANSACTION`.

                KONVERSI NOMINAL:
                - "k" = x1.000, "jt" = x1.000.000. (Contoh: "50k" -> 50000, "1.5jt" -> 1500000)

                PENTING: Gunakan JSON mode. Kembalikan data dalam format JSON yang valid.
                
                OUTPUT FORMAT (JSON):
                {
                  "action": "CREATE_TRANSACTION" | "UPDATE_TRANSACTION" | "DELETE_TRANSACTION" | "NONE",
                  "target_space_name": "string (Nama space yang dideteksi, atau null jika tidak ada)",
                  "transactions": [ 
                    { "type": "INCOME"|"EXPENSE", "amount": number, "category": "string", "note": "string" }
                  ],
                  "correction_data": {
                     "amount": number (optional),
                     "note": "string (optional)"
                  },
                  "reply": "Respon ramah Anda ke pengguna (Sir Quack persona)"
                }

                INFO TRANSAKSI TERAKHIR (Gunakan jika action adalah UPDATE_TRANSACTION atau DELETE_TRANSACTION):
                ${_uiState.value.lastTransaction?.let { "ID: ${it.id}, Amount: ${it.amount}, Note: ${it.note}" } ?: "Tidak ada transaksi terbaru"}
                
                INPUT USER: "$userText"
            """.trimIndent()

            var retryCount = 0
            val maxRetries = 2
            var lastException: Exception? = null

            while (retryCount <= maxRetries) {
                try {
                    val responseText = groqService.generateContent(prompt, jsonMode = true)
                    handleAIResponse(responseText, userMessageId)
                    return@launch
                } catch (e: GroqException) {
                    lastException = e
                    println("SirQuack: GroqException (Attempt ${retryCount + 1}) - ${e.message}")
                    
                    if (e is GroqException.RateLimit || e is GroqException.Server || e is GroqException.Network) {
                        retryCount++
                        if (retryCount <= maxRetries) {
                            delay(1000L * retryCount) // Simple backoff
                            continue
                        }
                    }
                    break
                } catch (e: Exception) {
                    lastException = e
                    println("SirQuack: Unexpected Error - ${e.message}")
                    break
                }
            }

            val errorMessage = when (lastException) {
                is GroqException.RateLimit -> "Sir Quack sedang sangat sibuk. Coba lagi sebentar lagi ya, kwek! (Rate Limit)"
                is GroqException.Timeout -> "Koneksi ke Sir Quack terputus. Pastikan internetmu lancar, kwek!"
                is GroqException.Auth -> "Kwek! Sir Quack sepertinya kehilangan kunci aksesnya. (Masalah Autentikasi)"
                is GroqException.Network -> "Sir Quack tidak bisa menjangkau kolam data. Periksa koneksi internetmu, kwek!"
                is GroqException.Server -> "Sir Quack sedang kurang enak badan di server. Coba lagi nanti ya!"
                else -> "Ups, ada gangguan teknis saat menghubungi Sir Quack. Coba lagi ya, kwek!"
            }
            saveAiMessage(errorMessage)
            _uiState.update { it.copy(isTyping = false) }
        }
    }

    private suspend fun handleAIResponse(response: String, userMessageId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        val activeSpaceId = _uiState.value.activeSpaceId
        val userId = authRepository.getCurrentUserId() ?: ""
        val lastTx = _uiState.value.lastTransaction

        try {
            val jsonElement = Json.parseToJsonElement(response).jsonObject
            val action = jsonElement["action"]?.jsonPrimitive?.content ?: "NONE"
            val aiReplyRaw = jsonElement["reply"]?.jsonPrimitive?.content ?: "Kwek! Ada yang bisa Sir Quack bantu?"
            val targetSpaceName = jsonElement["target_space_name"]?.let {
                if (it is JsonNull) null else it.jsonPrimitive.content
            }
            
            val aiReply = if (targetSpaceName != null && targetSpaceName != "null") {
                "$aiReplyRaw (Space: $targetSpaceName)"
            } else {
                aiReplyRaw
            }

            when (action) {
                "DELETE_TRANSACTION" -> {
                    if (activeSpaceId != null && lastTx != null) {
                        try {
                            savingsRepository.deleteTransaction(activeSpaceId, lastTx.id)
                            saveAiMessage(aiReply)
                            _uiState.update { state ->
                                if (state.undoableTransaction?.transactionId == lastTx.id) {
                                    state.copy(undoableTransaction = null, isTyping = false)
                                } else state.copy(isTyping = false)
                            }
                            return
                        } catch (e: Exception) {
                            println("SirQuack: Delete Failed - ${e.message}")
                            saveAiMessage("Maaf kwek, Sir Quack gagal menghapus transaksinya. Coba hapus manual ya.")
                        }
                    } else {
                        saveAiMessage("Kwek? Sir Quack tidak menemukan transaksi terakhir untuk dibatalkan.")
                    }
                }
                "UPDATE_TRANSACTION" -> {
                    if (activeSpaceId != null && lastTx != null) {
                        val correctionData = jsonElement["correction_data"]?.jsonObject
                        val newAmount: Long = correctionData?.get("amount")?.jsonPrimitive?.content?.let { 
                            it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong()
                        } ?: lastTx.amount
                        val newNote = correctionData?.get("note")?.jsonPrimitive?.content ?: lastTx.note
                        
                        val newTx = lastTx.copy(amount = newAmount, note = newNote)
                        
                        _uiState.update { it.copy(
                            pendingCorrection = PendingCorrection(lastTx, newTx, aiReply),
                            isTyping = false
                        ) }
                        
                        saveAiMessage(aiReply)
                        return 
                    } else {
                        saveAiMessage("Tidak ada transaksi yang bisa diperbaiki saat ini, kwek!")
                    }
                }
                "CREATE_TRANSACTION" -> {
                    val targetSpace = if (targetSpaceName != null && targetSpaceName != "null") {
                        _uiState.value.savingsSpaces.find { 
                            it.name.equals(targetSpaceName, ignoreCase = true) 
                        }
                    } else null

                    // If user mentioned a space that doesn't exist
                    if (targetSpaceName != null && targetSpaceName != "null" && targetSpace == null) {
                        saveAiMessage("Kwek! Sir Quack tidak bisa menemukan Savings Space bernama \"$targetSpaceName\". Pastikan namanya benar ya!")
                        _uiState.update { it.copy(isTyping = false) }
                        return
                    }

                    val finalSpaceId = targetSpace?.id ?: activeSpaceId
                    val finalSpaceName = targetSpace?.name ?: (_uiState.value.savingsSpaces.find { it.id == activeSpaceId }?.name ?: "Unknown")

                    if (finalSpaceId != null) {
                        val transactionsArray = jsonElement["transactions"]?.jsonArray
                        val txsToCreate = mutableListOf<SavingsTransaction>()
                        
                        transactionsArray?.forEachIndexed { index, item ->
                            try {
                                val data = item.jsonObject
                                val typeStr = data["type"]?.jsonPrimitive?.content ?: "EXPENSE"
                                val type = if (typeStr == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                                val amount: Long = data["amount"]?.jsonPrimitive?.content?.let { 
                                    it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong()
                                } ?: 0L
                                val category = data["category"]?.jsonPrimitive?.content ?: "General"
                                val note = data["note"]?.jsonPrimitive?.content ?: ""

                                val txId = "ai_${userMessageId}_$index"
                                
                                txsToCreate.add(
                                    SavingsTransaction(
                                        id = txId,
                                        spaceId = finalSpaceId,
                                        userId = userId,
                                        userName = category,
                                        amount = amount,
                                        type = type,
                                        note = note,
                                        timestamp = now
                                    )
                                )
                            } catch (e: Exception) {
                                println("SirQuack: Transaction Parsing Failed - ${e.message}")
                            }
                        }
                        
                        if (txsToCreate.isNotEmpty()) {
                            // CASE: Explicit space mentioned AND it's different from active space
                            if (targetSpace != null && targetSpace.id != activeSpaceId) {
                                val confirmationText = "Kwek! Kamu sedang di space ${_uiState.value.savingsSpaces.find { it.id == activeSpaceId }?.name}, tapi transaksi ini sepertinya untuk $finalSpaceName. Catat ke $finalSpaceName?"
                                _uiState.update { it.copy(
                                    pendingSpaceTransaction = PendingSpaceTransaction(
                                        spaceId = finalSpaceId,
                                        spaceName = finalSpaceName,
                                        transactions = txsToCreate,
                                        confirmationText = confirmationText
                                    ),
                                    isTyping = false
                                ) }
                                saveAiMessage(confirmationText)
                                return
                            }

                            // CASE: Same space or no explicit space mentioned -> Execute immediately
                            val successfulTxs = mutableListOf<String>()
                            txsToCreate.forEach { tx ->
                                try {
                                    savingsRepository.addTransaction(finalSpaceId, tx)
                                    successfulTxs.add(tx.id)
                                } catch (e: Exception) {
                                    println("SirQuack: Transaction Save Failed - ${e.message}")
                                }
                            }

                            if (successfulTxs.isNotEmpty()) {
                                val aiMsgId = saveAiMessage(aiReply)
                                startUndoTimer(successfulTxs.last(), finalSpaceId, aiMsgId)
                                _uiState.update { it.copy(isTyping = false) }
                                return
                            }
                        }
                        
                        saveAiMessage("Sir Quack gagal mencatat transaksi. Pastikan format nominalnya benar ya, kwek!")
                    }
                }
                else -> {
                    saveAiMessage(aiReply)
                }
            }
        } catch (e: Exception) {
            println("SirQuack: Response Parsing Failed - ${e.message}")
            saveAiMessage(response.takeIf { it.isNotBlank() } ?: "Kwek! Sir Quack sedikit bingung. Bisa diulangi?")
        }

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
                
                val formattedAmount = pending.newTransaction.amount.formatCurrency(pending.newTransaction.currency)
                saveAiMessage("Berhasil diperbarui! Transaksi sekarang menjadi $formattedAmount.")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update transaction") }
            }
        }
    }

    fun confirmSpaceTransaction() {
        val pending = _uiState.value.pendingSpaceTransaction ?: return
        val userId = authRepository.getCurrentUserId() ?: ""
        
        viewModelScope.launch {
            try {
                pending.transactions.forEach { tx ->
                    savingsRepository.addTransaction(pending.spaceId, tx.copy(userId = userId))
                }
                
                // Switch active space
                setActiveSpace(pending.spaceId)
                
                _uiState.update { it.copy(pendingSpaceTransaction = null) }
                
                saveAiMessage("Kwek! Transaksi berhasil dicatat ke ${pending.spaceName}. Sekarang Sir Quack juga sudah pindah ke space ini!")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Gagal mencatat transaksi: ${e.message}") }
            }
        }
    }

    fun cancelSpaceTransaction() {
        _uiState.update { it.copy(pendingSpaceTransaction = null) }
        viewModelScope.launch {
            saveAiMessage("Oke kwek! Transaksi dibatalkan.")
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

    fun deleteSelectedMessages(onFeedback: (TransientFeedback) -> Unit) {
        val selectedIds = _uiState.value.selectedMessageIds.toList()
        if (selectedIds.isEmpty()) return
        val messagesToDelete = _uiState.value.messages.filter { it.id in selectedIds }

        viewModelScope.launch {
            try {
                aiChatRepository.deleteMessages(selectedIds)
                val count = selectedIds.size
                val text = if (count == 1) "Message deleted for me" else "$count messages deleted for me"
                onFeedback(TransientFeedback.UndoDelete(text, null, selectedIds, messagesToDelete))
                _uiState.update { it.copy(selectedMessageIds = emptySet()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete messages") }
            }
        }
    }

    fun restoreMessages(messages: List<ChatMessage>) {
        viewModelScope.launch {
            try {
                aiChatRepository.restoreMessages(messages)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to restore messages") }
            }
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
                val month = localDateTime.month.number
                val day = localDateTime.day
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
