package com.example.tasama.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.AuthRepository
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    init {
        viewModelScope.launch {
            authRepository.signInAnonymously()
        }
    }
}
