package com.example.tasama.domain.service

import com.example.tasama.domain.model.Transaction

class NoOpExportService : ExportService {
    override suspend fun exportToExcel(transactions: List<Transaction>): ByteArray = byteArrayOf()
    override suspend fun exportToPdf(transactions: List<Transaction>): ByteArray = byteArrayOf()
}
