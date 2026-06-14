package com.example.tasama.domain.service

class NoOpFileService : FileService {
    override suspend fun saveAndShareFile(fileName: String, content: ByteArray, mimeType: String) {
        // No-op for platforms not supporting file export yet
    }
}
