package com.example.tasama.domain.repository

import com.example.tasama.domain.model.*
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
    
    // Member Management
    suspend fun inviteMember(spaceId: String, inviteeId: String)
    suspend fun cancelInvitation(invitationId: String)
    suspend fun acceptInvitation(invitationId: String)
    suspend fun declineInvitation(invitationId: String)
    fun getPendingInvitations(spaceId: String): Flow<List<SavingsInvitation>>
    fun getMyInvitations(): Flow<List<SavingsInvitation>>
    
    suspend fun removeMember(spaceId: String, userId: String)
    suspend fun leaveSpace(spaceId: String)
    suspend fun transferOwnership(spaceId: String, newOwnerId: String)
    
    // Activity / History
    fun getActivityHistory(spaceId: String): Flow<List<SavingsActivity>>
    suspend fun archiveSpace(spaceId: String)
}
