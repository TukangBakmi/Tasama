package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    INCOME,
    EXPENSE
}