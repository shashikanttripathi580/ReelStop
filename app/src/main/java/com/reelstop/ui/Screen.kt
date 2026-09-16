package com.reelstop.ui

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")
    data object Privacy : Screen("privacy")
}
