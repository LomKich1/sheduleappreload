package com.schedule.app.ui

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.IntOffset
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

// ── Длительности ─────────────────────────────────────────────────────────────
private const val NAV_ANIM_MS = 280   // глубокие экраны: Schedule, Settings
private const val TAB_ANIM_MS = 250   // вкладки: Files ↔ Bells

// ── Порядок вкладок ───────────────────────────────────────────────────────────
//  Files = 0 (левая страница), Bells = 1 (правая страница).
//  Используется для определения направления слайда.
private val TAB_INDEX = mapOf(
    Screen.Files.route to 0,
    Screen.Bells.route to 1,
)

// true = движение вперёд (Files→Bells), false = назад (Bells→Files)
private fun isForward(from: String?, to: String?): Boolean {
    val a = TAB_INDEX[from] ?: return true
    val b = TAB_INDEX[to]   ?: return true
    return b > a
}

// ── Скоординированный слайд вкладок ──────────────────────────────────────────
//
//  Ключевое правило «без наложений»:
//  Enter и Exit используют ОДИНАКОВЫЙ easing + ОДИНАКОВУЮ длительность.
//  Тогда в любой момент t:
//    правый край уходящего  = screenW × (1 − f(t))
//    левый  край входящего  = screenW × (1 − f(t))   ← совпадают всегда
//  → экраны движутся бок о бок, как страницы журнала.

private val tabSpec = tween<IntOffset>(TAB_ANIM_MS, easing = FastOutSlowInEasing)

// forward=true  → входящий приезжает справа (Files→Bells)
// forward=false → входящий приезжает слева  (Bells→Files)
private fun tabEnter(forward: Boolean) = slideInHorizontally(
    initialOffsetX = { if (forward) it else -it },
    animationSpec  = tabSpec,
)

// forward=true  → уходящий едет влево  (Files→Bells: Files уходит влево)
// forward=false → уходящий едет вправо (Bells→Files: Bells уходит вправо)
private fun tabExit(forward: Boolean) = slideOutHorizontally(
    targetOffsetX = { if (forward) -it else it },
    animationSpec = tabSpec,
)

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppScaffold() {
    val c = LocalAppColors.current
    val context = LocalContext.current
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

            // ── Глобальные анимации — только для глубоких экранов ─────────
            //  Telegram-стиль: новый экран едет справа, старый уходит влево.
            //  Вкладки Files/Bells переопределяют эти значения (см. ниже).
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

            // ── Вкладка: Файлы (левая страница) ──────────────────────────
            composable(
                route = Screen.Files.route,

                enterTransition = {
                    val from = initialState.destination.route ?: ""
                    val to   = targetState.destination.route  ?: ""
                    // Возврат с Bells → Files едет слева
                    if (from in TAB_INDEX && to in TAB_INDEX) tabEnter(isForward(from, to))
                    else fadeIn(tween(160))
                },
                exitTransition = {
                    val from = initialState.destination.route ?: ""
                    val to   = targetState.destination.route  ?: ""
                    // Files → Bells: Files едет влево
                    // Files → Settings/Schedule: глобальный (null = NavHost default)
                    if (from in TAB_INDEX && to in TAB_INDEX) tabExit(isForward(from, to))
                    else null
                },
                popEnterTransition = {
                    val from = initialState.destination.route ?: ""
                    val to   = targetState.destination.route  ?: ""
                    // popUpTo возвращает на Files с Bells: Files едет слева
                    if (from in TAB_INDEX && to in TAB_INDEX) tabEnter(isForward(from, to))
                    else null   // глобальный popEnterTransition (слайд назад)
                },
                popExitTransition = { null },  // глобальный
              ) {
                    // Используем встроенную фабрику, чтобы ViewModel жила в бэкстеке навигации
                    val filesViewModel: FilesViewModel = viewModel {
                        FilesViewModel(context)
                    }
                    
                FilesScreen(
                    viewModel       = filesViewModel,
                    onFileClick     = { file ->
                        NavigationHolder.pendingFile = file
                        navController.navigate(Screen.Schedule.route)
                    },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }

            // ── Вкладка: Звонки (правая страница) ────────────────────────
            composable(
                route = Screen.Bells.route,

                enterTransition = {
                    val from = initialState.destination.route ?: ""
                    val to   = targetState.destination.route  ?: ""
                    // Files → Bells: Bells приезжает справа
                    if (from in TAB_INDEX && to in TAB_INDEX) tabEnter(isForward(from, to))
                    else fadeIn(tween(160))
                },
                exitTransition = {
                    val from = initialState.destination.route ?: ""
                    val to   = targetState.destination.route  ?: ""
                    // Bells → Files: Bells едет вправо
                    if (from in TAB_INDEX && to in TAB_INDEX) tabExit(isForward(from, to))
                    else null
                },
                popEnterTransition = {
                    val from = initialState.destination.route ?: ""
                    val to   = targetState.destination.route  ?: ""
                    if (from in TAB_INDEX && to in TAB_INDEX) tabEnter(isForward(from, to))
                    else fadeIn(tween(160))
                },
                popExitTransition = {
                    val from = initialState.destination.route ?: ""
                    val to   = targetState.destination.route  ?: ""
                    // popUpTo с Bells обратно на Files: Bells едет вправо
                    if (from in TAB_INDEX && to in TAB_INDEX) tabExit(isForward(from, to))
                    else null
                },
            ) {
                BellsScreen()
            }

            // ── Глубокие экраны: слайд в стиле Telegram ──────────────────
            //  Используют глобальные анимации NavHost (без переопределения)
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
