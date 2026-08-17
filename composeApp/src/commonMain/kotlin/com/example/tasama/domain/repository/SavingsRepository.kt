package com.example.tasama.domain.repository

import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.SavingsTransaction
import kotlinx.coroutines.flow.Flow

interface SavingsRepository {
    fun getSavingsSpaces(): Flow<List<SavingsSpace>>
    fun getSavingsSpace(id: String): Flow<SavingsSpace?>
    fun getTransactions(spaceId: String): Flow<List<SavingsTransaction>>
    suspend fun createSavingsSpace(space: SavingsSpace): String
    suspend fun updateSavingsSpace(space: SavingsSpace)
    suspend fun deleteSavingsSpace(id: String)
    suspend fun addTransaction(spaceId: String, transaction: SavingsTransaction)
    suspend fun updateTransaction(spaceId: String, transaction: SavingsTransaction)
    suspend fun deleteTransaction(spaceId: String, transactionId: String)
    suspend fun inviteMember(spaceId: String, email: String)
}
