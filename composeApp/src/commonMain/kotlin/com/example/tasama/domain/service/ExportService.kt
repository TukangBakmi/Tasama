package com.example.tasama.domain.service

import com.example.tasama.domain.model.Transaction

interface ExportService {
    suspend fun exportToExcel(transactions: List<Transaction>): ByteArray
    suspend fun exportToPdf(transactions: List<Transaction>): ByteArray
}
