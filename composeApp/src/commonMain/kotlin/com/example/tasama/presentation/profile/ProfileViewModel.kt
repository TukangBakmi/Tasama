package com.example.tasama.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.TransactionRepository
import com.example.tasama.domain.service.ExportService
import com.example.tasama.domain.service.FileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val transactionRepository: TransactionRepository,
    private val exportService: ExportService,
    private val fileService: FileService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    fun exportToExcel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val transactions = transactionRepository.getTransactions()
            val bytes = exportService.exportToExcel(transactions)
            
            fileService.saveAndShareFile(
                fileName = "tasama_export_excel.xlsx",
                content = bytes,
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    fun exportToPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val transactions = transactionRepository.getTransactions()
            val bytes = exportService.exportToPdf(transactions)
            
            fileService.saveAndShareFile(
                fileName = "tasama_export_pdf.pdf",
                content = bytes,
                mimeType = "application/pdf"
            )

            _uiState.update { it.copy(isExporting = false) }
        }
    }
}
