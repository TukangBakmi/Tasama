package com.example.tasama.domain.repository

import com.example.tasama.domain.model.Transaction

interface TransactionRepository {

    suspend fun getTransactions(): List<Transaction>

    suspend fun addTransaction(
        transaction: Transaction
    )

    suspend fun deleteTransaction(
        transactionId: String
    )
}