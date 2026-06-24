package com.example.tasama.navigation

sealed class BottomNavItem(
    val title: String,
    val emoji: String
) {
    data object Dashboard : BottomNavItem(
        "Dashboard",
        "📊"
    )

    data object Savings : BottomNavItem(
        "Savings",
        "💰"
    )

    data object Chat : BottomNavItem(
        "Chat",
        "💬"
    )

    data object Partner : BottomNavItem(
        "Partner",
        "❤️"
    )

    data object Profile : BottomNavItem(
        "Profile",
        "👤"
    )
}
