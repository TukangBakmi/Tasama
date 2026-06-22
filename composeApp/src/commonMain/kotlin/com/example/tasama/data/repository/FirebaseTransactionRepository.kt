package com.example.tasama.data.repository

import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.TransactionRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock as KtClock

class FirebaseTransactionRepository(
    private val authRepository: AuthRepository
) : TransactionRepository {
    private val firestore = Firebase.firestore
    private val collection = firestore.collection("transactions")

    override suspend fun getTransactions(): List<Transaction> {
        val userId = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            collection.where { "userId" equalTo userId }.get().documents.map { it.data() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTransactionsFlow(): Flow<List<Transaction>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                collection.where { "userId" equalTo uid }.snapshots.map { snapshot ->
                    snapshot.documents.map { it.data() }
                }
            }
        }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        val userId = authRepository.getCurrentUserId() ?: return
        val now = KtClock.System.now().toEpochMilliseconds()
        val id = if (transaction.id.isEmpty()) "tx_$now" else transaction.id
        val doc = collection.document(id)
        val finalTransaction = transaction.copy(id = id, userId = userId, createdAt = if (transaction.createdAt == 0L) now else transaction.createdAt)
        doc.set(finalTransaction)
    }

    override suspend fun deleteTransaction(transactionId: String) {
        collection.document(transactionId).delete()
    }
}
