package com.example.tasama.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
    private val exportService: ExportService,
    private val fileService: FileService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeUser()
        observeSettings()
    }

    private fun observeUser() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                if (uid != null) {
                    loadUserProfile(uid)
                } else {
                    // Reset state on logout
                    _uiState.update { ProfileUiState(
                        theme = it.theme,
                        currency = it.currency
                    ) }
                }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(
                    theme = settings.theme,
                    currency = settings.currency
                ) }
            }
        }
    }

    private fun loadUserProfile(uid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = authRepository.getUser(uid)
            val isGuest = authRepository.isGuest()
            var partnerName: String? = null
            if (user?.partnerId != null) {
                partnerName = authRepository.getUser(user.partnerId)?.name
            }
            
            _uiState.update { it.copy(
                userName = user?.name ?: "Guest", 
                userEmail = user?.email ?: "Guest session",
                userId = uid,
                userShortId = user?.shortId ?: "",
                profilePictureUrl = user?.avatarUrl,
                partnerId = user?.partnerId,
                partnerName = partnerName,
                isLoading = false,
                isGuest = isGuest
            ) }
        }
    }

    fun linkPartner(partnerShortId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.linkPartner(uid, partnerShortId)
            if (result.isSuccess) {
                loadUserProfile(uid)
                _uiState.update { it.copy(exportMessage = "Partner linked successfully") }
            } else {
                _uiState.update { it.copy(
                    isLoading = false, 
                    error = result.exceptionOrNull()?.message ?: "Failed to link partner"
                ) }
            }
        }
    }

    fun unlinkPartner() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.unlinkPartner(uid)
            if (result.isSuccess) {
                loadUserProfile(uid)
                _uiState.update { it.copy(exportMessage = "Partner unlinked") }
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to unlink partner"
                ) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateProfilePicture(url: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            authRepository.updateProfilePicture(uid, url)
            _uiState.update { it.copy(profilePictureUrl = url, isUpdating = false) }
        }
    }

    fun updateDisplayName(name: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            authRepository.updateDisplayName(uid, name)
            _uiState.update { it.copy(userName = name, isUpdating = false) }
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
            try {
                val transactions = transactionRepository.getTransactions()
                val bytes = exportService.exportToPdf(transactions)
                
                fileService.saveAndShareFile(
                    fileName = "tasama_export_pdf.pdf",
                    content = bytes,
                    mimeType = "application/pdf"
                )
                _uiState.update { it.copy(isExporting = false, exportMessage = "PDF exported successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, exportMessage = "Error exporting PDF: ${e.message}") }
            }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    fun onIdCopied() {
        _uiState.update { it.copy(exportMessage = "ID copied to clipboard") }
    }

    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.updateTheme(theme)
        }
    }

    fun updateCurrency(currency: String) {
        viewModelScope.launch {
            settingsRepository.updateCurrency(currency)
        }
    }
}
