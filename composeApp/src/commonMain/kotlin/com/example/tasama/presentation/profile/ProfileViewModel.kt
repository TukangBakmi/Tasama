package com.example.tasama.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.domain.repository.TransactionRepository
import com.example.tasama.domain.service.ExportService
import com.example.tasama.domain.service.FileService
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeUser() {
        viewModelScope.launch {
            authRepository.userId.flatMapLatest { uid ->
                if (uid != null) {
                    authRepository.getUserFlow(uid).map { user -> uid to user }
                } else {
                    flowOf(null to null)
                }
            }.collect { (uid, user) ->
                if (uid != null) {
                    val isGuest = authRepository.isGuest()
                    var partnerName: String? = null
                    val partnerId = user?.partnerId
                    if (partnerId != null) {
                        partnerName = authRepository.getUser(partnerId)?.name
                    }

                    _uiState.update {
                        it.copy(
                            userName = user?.name ?: "Guest",
                            userEmail = user?.email ?: "Guest session",
                            userId = uid,
                            userShortId = user?.shortId ?: "",
                            profilePictureUrl = user?.avatarUrl,
                            partnerId = partnerId,
                            partnerName = partnerName,
                            isLoading = false,
                            isGuest = isGuest,
                            hasPendingRequest = user?.partnerRequestFrom != null
                        )
                    }
                } else {
                    // Reset state on logout
                    _uiState.update {
                        ProfileUiState(
                            theme = it.theme,
                            currency = it.currency
                        )
                    }
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

    // loadUserProfile is no longer needed as observeUser handles it reactively

    fun linkPartner(partnerShortId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isLinkSuccess = false, errorText = null) }
            val result = authRepository.sendPartnerRequest(uid, partnerShortId)
            _uiState.update { it.copy(isLoading = false) }
            if (result.isSuccess) {
                _uiState.update { it.copy(
                    exportMessage = "Partner request sent",
                    isLinkSuccess = true
                ) }
            } else {
                _uiState.update { it.copy(
                    errorText = result.exceptionOrNull()?.message ?: "Failed to link partner"
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
                _uiState.update { it.copy(exportMessage = "Partner unlinked") }
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to unlink partner"
                ) }
            }
        }
    }

    fun clearLinkError() {
        _uiState.update { it.copy(errorText = null) }
    }

    fun clearLinkSuccess() {
        _uiState.update { it.copy(isLinkSuccess = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateProfilePicture(url: String?) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            authRepository.updateProfilePicture(uid, url)
            _uiState.update { it.copy(isUpdating = false) }
        }
    }

    fun deleteProfilePicture() {
        updateProfilePicture(null)
    }

    fun uploadProfilePicture(file: PlatformFile) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUpdating = true) }
                val bytes = file.readBytes()
                val url = authRepository.uploadProfilePicture(uid, bytes)
                authRepository.updateProfilePicture(uid, url)
                _uiState.update { it.copy(isUpdating = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUpdating = false, error = "Failed to upload: ${e.message}") }
            }
        }
    }

    fun uploadProfilePicture(bytes: ByteArray) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUpdating = true) }
                val url = authRepository.uploadProfilePicture(uid, bytes)
                authRepository.updateProfilePicture(uid, url)
                _uiState.update { it.copy(isUpdating = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUpdating = false, error = "Failed to upload: ${e.message}") }
            }
        }
    }

    fun updateDisplayName(name: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            authRepository.updateDisplayName(uid, name)
            _uiState.update { it.copy(isUpdating = false) }
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
