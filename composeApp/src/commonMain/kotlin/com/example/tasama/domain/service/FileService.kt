package com.example.tasama.domain.service

interface FileService {
    suspend fun saveAndShareFile(fileName: String, content: ByteArray, mimeType: String)
}
