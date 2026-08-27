package com.example.tasama.domain.repository

interface SessionCleanupRepository {
    suspend fun cleanup()
}
