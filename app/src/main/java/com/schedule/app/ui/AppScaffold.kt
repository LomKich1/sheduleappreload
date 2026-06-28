package com.schedule.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.schedule.app.ui.navigation.FloatingPillNav
import com.schedule.app.ui.navigation.NavigationHolder
import com.schedule.app.ui.navigation.Screen
import com.schedule.app.ui.screens.BellsScreen
import com.schedule.app.ui.screens.FilesScreen
import com.schedule.app.ui.screens.ScheduleScreen
import com.schedule.app.ui.screens.SettingsScreen
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset

// Длительность анимации перехода (мс)
private const val NAV_ANIM_MS = 280

@Composable
fun AppScaffold() {
    val c = LocalAppColors.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Screen.Files.route

    val showPill = currentRoute in listOf(Screen.Files.route, Screen.Bells.route)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding(),
    ) {
        NavHost(
            navController    = navController,
            startDestination = Screen.Files.route,
            modifier         = Modifier
                .fillMaxSize()
                .then(if (showPill) Modifier.padding(bottom = 76.dp) else Modifier),

            // ── Telegram-стиль: сдвиг по горизонтали + затухание ─────────
            // Вперёд: новый экран едет справа, старый уходит влево
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec  = tween(NAV_ANIM_MS, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(NAV_ANIM_MS - 60))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(NAV_ANIM_MS, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(NAV_ANIM_MS - 60))
            },
            // Назад: текущий едет вправо, предыдущий возвращается справа налево
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec  = tween(NAV_ANIM_MS, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(NAV_ANIM_MS - 60))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(NAV_ANIM_MS, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(NAV_ANIM_MS - 60))
            },
        ) {
            composable(Screen.Files.route) {
                FilesScreen(
                    onFileClick     = { file ->
                        NavigationHolder.pendingFile = file
                        navController.navigate(Screen.Schedule.route)
                    },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }

            composable(Screen.Bells.route) {
                BellsScreen()
            }

            composable(Screen.Schedule.route) {
                val file = NavigationHolder.pendingFile
                if (file != null) {
                    ScheduleScreen(
                        file   = file,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }

        if (showPill) {
            FloatingPillNav(
                currentRoute = currentRoute,
                onNavigate   = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
            )
        }
    }
}

@Preview(name = "Главный экран", showSystemUi = true)
@Composable
private fun AppScaffoldPreview() {
    AppTheme(preset = ThemePreset.DARK) { AppScaffold() }
}
