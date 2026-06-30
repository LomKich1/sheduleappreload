package com.schedule.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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

// ── Длительности ─────────────────────────────────────────────────────────────
private const val NAV_ANIM_MS = 280   // глубокие экраны: Schedule, Settings
private const val TAB_ANIM_MS = 250   // вкладки: Files ↔ Bells

// route-заглушка — единственный startDestination NavHost. Сама ничего не
// рисует: вкладки Files/Bells больше не живут внутри NavHost (см. ниже),
// поэтому он отвечает только за Schedule/Settings поверх неё.
private const val TABS_PLACEHOLDER = "tabs_placeholder"

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppScaffold() {
    val c = LocalAppColors.current
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val deepRoute = backStack?.destination?.route ?: TABS_PLACEHOLDER
    val deepScreenOpen = deepRoute != TABS_PLACEHOLDER

    // ── Активная вкладка — состояние ВНЕ NavHost. ───────────────────────────
    //  Именно это убирает лаг: Files/Bells больше не уничтожаются и не
    //  пересоздаются при каждом переключении, они всегда в композиции —
    //  переключение это просто анимация translationX (см. BoxWithConstraints).
    var activeTab by rememberSaveable { mutableStateOf(Screen.Files.route) }

    val showPill = !deepScreenOpen

    // Системная кнопка «назад»: если открыта вкладка Bells и нет глубокого
    // экрана сверху — возвращаем на Files, а не выходим из приложения.
    // Кнопка «назад» поверх Schedule/Settings продолжает работать как раньше
    // — её обрабатывает сам NavHost (popBackStack), эта строка ему не мешает.
    BackHandler(enabled = !deepScreenOpen && activeTab == Screen.Bells.route) {
        activeTab = Screen.Files.route
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding(),
    ) {
        // ── Вкладки: Files и Bells всегда в композиции ──────────────────────
        //  Раньше это были composable() внутри NavHost — отсюда и лаг.
        //  Теперь оба экрана собраны один раз и просто сдвигаются по X.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showPill) Modifier.padding(bottom = 76.dp) else Modifier)
                .clipToBounds(), // чтобы неактивная вкладка не вылезала за край
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }

            // Files стоит в 0, Bells сдвинута на +width (ждёт справа).
            // offset двигает обе разом — ровно так же, как раньше двигались
            // tabEnter/tabExit, но без пересборки экрана.
            val targetOffset = if (activeTab == Screen.Files.route) 0f else -widthPx
            val offset by animateFloatAsState(
                targetValue   = targetOffset,
                animationSpec = tween(TAB_ANIM_MS, easing = FastOutSlowInEasing),
                label         = "tabSlide",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = offset },
            ) {
                FilesScreen(
                    onFileClick     = { file ->
                        NavigationHolder.pendingFile = file
                        navController.navigate(Screen.Schedule.route)
                    },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = offset + widthPx },
            ) {
                BellsScreen()
            }
        }

        // ── Глубокие экраны: Schedule, Settings — Telegram-стиль слайда ─────
        //  Единственный startDestination — пустая заглушка; Schedule/Settings
        //  накладываются на неё сверху и анимируются как раньше.
        NavHost(
            navController    = navController,
            startDestination = TABS_PLACEHOLDER,
            modifier         = Modifier.fillMaxSize(),

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
            // Ничего не рисует — вкладки рисует BoxWithConstraints выше.
            // Прозрачность: клики по областям без deep-экрана уходят
            // напрямую во FilesScreen/BellsScreen, лежащие ниже по z-оси.
            composable(TABS_PLACEHOLDER) {
                Box(Modifier.fillMaxSize())
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
                currentRoute = activeTab,
                onNavigate   = { route -> activeTab = route },
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
