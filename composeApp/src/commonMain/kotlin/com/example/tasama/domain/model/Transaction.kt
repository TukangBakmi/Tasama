package com.example.tasama.domain.model

data class Transaction(
    val id: String,
    val amount: Long,
    val type: TransactionType,
    val category: String,
    val note: String,
    val createdAt: Long
)