package com.example.tasama.presentation.savings

sealed class SavingsEvent {
    data object NavigateToSavingsList : SavingsEvent()
    data class ShowFeedback(val message: String) : SavingsEvent()
}
