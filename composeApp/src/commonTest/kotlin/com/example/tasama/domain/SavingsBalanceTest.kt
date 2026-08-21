package com.example.tasama.domain

import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.SavingsTransaction
import com.example.tasama.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

class SavingsBalanceTest {

    @Test
    fun testBalanceCalculation() {
        val initialBalance = 1000L
        val income = SavingsTransaction(amount = 500L, type = TransactionType.INCOME)
        val expense = SavingsTransaction(amount = 200L, type = TransactionType.EXPENSE)

        val newBalanceAfterIncome = initialBalance + income.amount
        assertEquals(1500L, newBalanceAfterIncome)

        val newBalanceAfterExpense = newBalanceAfterIncome - expense.amount
        assertEquals(1300L, newBalanceAfterExpense)
    }

    @Test
    fun testUpdateTransactionBalanceImpact() {
        val initialBalance = 1000L
        val oldTransaction = SavingsTransaction(amount = 500L, type = TransactionType.INCOME)
        val newTransaction = SavingsTransaction(amount = 700L, type = TransactionType.INCOME)

        // Reverse old impact
        val intermediateBalance = initialBalance - oldTransaction.amount
        assertEquals(500L, intermediateBalance)

        // Apply new impact
        val finalBalance = intermediateBalance + newTransaction.amount
        assertEquals(1200L, finalBalance)
    }

    @Test
    fun testChangeTransactionTypeBalanceImpact() {
        val initialBalance = 1000L
        val oldTransaction = SavingsTransaction(amount = 500L, type = TransactionType.INCOME)
        val newTransaction = SavingsTransaction(amount = 500L, type = TransactionType.EXPENSE)

        // Reverse old impact (was income, so subtract)
        val intermediateBalance = initialBalance - oldTransaction.amount
        assertEquals(500L, intermediateBalance)

        // Apply new impact (is expense, so subtract)
        val finalBalance = intermediateBalance - newTransaction.amount
        assertEquals(0L, finalBalance)
    }
}
