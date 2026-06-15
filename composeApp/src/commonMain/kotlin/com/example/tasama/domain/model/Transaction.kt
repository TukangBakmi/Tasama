package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val id: String = "",
    val userId: String = "",
    val amount: Long = 0,
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "",
    val note: String = "",
    val createdAt: Long = 0
)