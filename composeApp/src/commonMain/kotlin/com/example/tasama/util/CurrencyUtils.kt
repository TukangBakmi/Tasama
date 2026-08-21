package com.example.tasama.util

fun Long.formatAmount(): String {
    return this.toString().reversed().chunked(3).joinToString(".").reversed()
}

fun Long.formatCurrency(currency: String): String {
    val symbol = when (currency) {
        "IDR" -> "Rp"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        else -> currency
    }
    
    val formatted = this.formatAmount()
    
    return if (currency == "IDR") "$symbol $formatted" else "$symbol$formatted"
}

fun Long.formatShortAmount(): String {
    return when {
        this >= 1_000_000_000 -> "${(this / 100_000_000) / 10.0}B"
        this >= 1_000_000 -> "${(this / 100_000) / 10.0}M"
        this >= 1_000 -> "${(this / 100) / 10.0}K"
        else -> this.toString()
    }
}
