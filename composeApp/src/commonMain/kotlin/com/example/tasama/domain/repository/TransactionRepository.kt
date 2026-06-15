package com.example.tasama.domain.repository

import com.example.tasama.domain.model.Transaction

import kotlinx.coroutines.flow.Flow

interface TransactionRepository {

    suspend fun getTransactions(): List<Transaction>

    fun getTransactionsFlow(): Flow<List<Transaction>>

    suspend fun addTransaction(
        transaction: Transaction
    )

    suspend fun deleteTransaction(
        transactionId: String
    )
}