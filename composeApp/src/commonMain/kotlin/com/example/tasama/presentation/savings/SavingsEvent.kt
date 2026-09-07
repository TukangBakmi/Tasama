package com.example.tasama.presentation.savings

sealed class SavingsEvent {
    data object NavigateToSavingsList : SavingsEvent()
}
