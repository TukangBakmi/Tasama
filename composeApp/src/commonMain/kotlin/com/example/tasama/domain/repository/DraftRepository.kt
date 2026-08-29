package com.example.tasama.domain.repository

import kotlinx.coroutines.flow.Flow

interface DraftRepository {
    fun getDraft(channelId: String): Flow<String?>
    suspend fun saveDraft(channelId: String, draft: String)
    suspend fun clearDraft(channelId: String)
    fun getAllDrafts(): Flow<Map<String, String>>
}
