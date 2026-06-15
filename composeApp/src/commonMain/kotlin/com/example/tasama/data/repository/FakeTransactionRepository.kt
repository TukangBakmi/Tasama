package com.example.tasama.data.repository

import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock

class FakeTransactionRepository : TransactionRepository {

    private val transactions = mutableListOf(
        Transaction(
            id = "1",
            amount = 8000000,
            type = TransactionType.INCOME,
            category = "Salary",
            note = "Gaji Bulanan",
            createdAt = 1716440000000L
        ),
        Transaction(
            id = "2",
            amount = 25000,
            type = TransactionType.EXPENSE,
            category = "Food",
            note = "Makan Siang",
            createdAt = 1716440000000L
        )
    )

    override suspend fun getTransactions(): List<Transaction> {
        return transactions
    }

    override fun getTransactionsFlow(): Flow<List<Transaction>> {
        return flowOf(transactions)
    }

    override suspend fun addTransaction(
        transaction: Transaction
    ) {
        transactions.add(transaction)
    }

    override suspend fun deleteTransaction(
        transactionId: String
    ) {
        val transaction = transactions.find {
            it.id == transactionId
        }

        if (transaction != null) {
            transactions.remove(transaction)
        }
    }
}