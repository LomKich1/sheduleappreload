package com.schedule.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

// ─── Нижняя навигация Material 3 (NavigationBar) ──────────────────────────────
//  Заменила самописную плавающую "пилюлю" FloatingPillNav.
//  Инсеты (жестовая полоса) раздаёт родитель — см. AppScaffold.

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val destinations = listOf(
    NavDestination(Screen.Files.route, "Расписание", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    NavDestination(Screen.Bells.route, "Звонки", Icons.Outlined.NotificationsNone, Icons.Filled.Notifications),
)

@Composable
fun AppNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        windowInsets = WindowInsets(0),
    ) {
        destinations.forEach { dest ->
            val selected = dest.route == currentRoute
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(dest.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(dest.label) },
            )
        }
    }
}
