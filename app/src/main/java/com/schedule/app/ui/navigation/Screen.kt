package com.schedule.app.ui.navigation

sealed class Screen(val route: String) {
    object Files    : Screen("files")
    object Bells    : Screen("bells")
    object Schedule : Screen("schedule")
    object Settings : Screen("settings")
}
