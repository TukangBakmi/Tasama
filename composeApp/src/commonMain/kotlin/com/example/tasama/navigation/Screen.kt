package com.example.tasama.navigation

sealed class Screen(
    val route: String
) {

    data object Dashboard :
        Screen("dashboard")

    data object TransactionList :
        Screen("transaction_list")

    data object AddTransaction :
        Screen("add_transaction")

    data object Login :
        Screen("login")

    data object Savings :
        Screen("savings")

    data object AIChat :
        Screen("ai_chat")

    data object Chat :
        Screen("chat")

}