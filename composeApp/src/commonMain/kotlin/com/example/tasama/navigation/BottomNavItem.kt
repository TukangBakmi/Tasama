package com.example.tasama.navigation

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val emoji: String
) {
    data object Dashboard : BottomNavItem(
        "dashboard",
        "Dashboard",
        "📊"
    )

    data object Savings : BottomNavItem(
        "savings",
        "Savings",
        "💰"
    )

    data object AIAdvisor : BottomNavItem(
        "ai_advisor",
        "AI Advisor",
        "✨"
    )

    data object Chat : BottomNavItem(
        "chat",
        "Chat",
        "💬"
    )

    data object Profile : BottomNavItem(
        "profile",
        "Profile",
        "👤"
    )
}
