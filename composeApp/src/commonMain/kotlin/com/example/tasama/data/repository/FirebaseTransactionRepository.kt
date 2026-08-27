package com.example.tasama.data.repository

import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.TransactionRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseTransactionRepository(
    private val authRepository: AuthRepository
) : TransactionRepository {
    private val firestore = Firebase.firestore
    private val collection = firestore.collection("transactions")

    override suspend fun cleanup() {
        println("DEBUG: Cleaning up FirebaseTransactionRepository")
        // No long-running jobs to clean up
    }

    override suspend fun getTransactions(): List<Transaction> {
        val userId = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            collection.where { "userId" equalTo userId }.get().documents.map { it.data() }
        } catch (e: Exception) {
            if (e.message?.contains("permission", ignoreCase = true) == true) {
                println("DEBUG: [TX] getTransactions: Permission denied (expected during logout)")
            }
            emptyList()
        }
    }

    override fun getTransactionsFlow(): Flow<List<Transaction>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                collection.where { "userId" equalTo uid }.snapshots.map { snapshot ->
                    snapshot.documents.map { it.data<Transaction>() }
                }.catch { e ->
                    if (e.message?.contains("permission", ignoreCase = true) == true) {
                        println("DEBUG: [TX] getTransactionsFlow: Permission denied (expected during logout)")
                    } else {
                        println("ERROR: [TX] getTransactionsFlow error: ${e.message}")
                    }
                    emit(emptyList())
                }
            }
        }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        val userId = authRepository.getCurrentUserId() ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val id = transaction.id.ifEmpty { "tx_$now" }
        val doc = collection.document(id)
        val finalTransaction = transaction.copy(id = id, userId = userId, createdAt = if (transaction.createdAt == 0L) now else transaction.createdAt)
        doc.set(finalTransaction)
    }

    override suspend fun deleteTransaction(transactionId: String) {
        collection.document(transactionId).delete()
    }
}
