package com.example.tasama.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.TransactionRepository
import com.example.tasama.domain.service.ExportService
import com.example.tasama.domain.service.FileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val exportService: ExportService,
    private val fileService: FileService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            val user = authRepository.getUser(uid)
            _uiState.update { it.copy(
                userName = user?.name ?: "User", 
                userEmail = user?.email ?: "", 
                userId = uid,
                userShortId = user?.shortId ?: "",
                profilePictureUrl = user?.avatarUrl
            ) }
        }
    }

    fun updateProfilePicture(url: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.updateProfilePicture(uid, url)
            _uiState.update { it.copy(profilePictureUrl = url) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

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
            
            _uiState.update { it.copy(isExporting = false, exportMessage = "Excel exported successfully") }
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

            _uiState.update { it.copy(isExporting = false, exportMessage = "PDF exported successfully") }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    fun onIdCopied() {
        _uiState.update { it.copy(exportMessage = "ID copied to clipboard") }
    }
}
