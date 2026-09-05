package com.schedule.app.ui

import com.schedule.app.util.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.lerp
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.schedule.app.data.prefs.AnimPrefs
import com.schedule.app.data.prefs.TabAnimMode
import com.schedule.app.ui.components.AppHeader
import com.schedule.app.ui.components.rememberSwipableProgress
import com.schedule.app.ui.navigation.FloatingPillNav
import com.schedule.app.ui.navigation.NavigationHolder
import com.schedule.app.ui.navigation.Screen
import com.schedule.app.ui.screens.BellsScreen
import com.schedule.app.ui.screens.DebugSettingsScreen
import com.schedule.app.ui.screens.FilesScreen
import com.schedule.app.ui.screens.ScheduleHostScreen
import com.schedule.app.ui.screens.SettingsScreen
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import androidx.compose.runtime.collectAsState
import kotlin.math.pow

// ── Длительности ─────────────────────────────────────────────────────────────

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
    // Именно это убирает лаг: Files/Bells больше не уничтожаются и не
    // пересоздаются при каждом переключении, они всегда в композиции —
    // переключение это просто анимация translationX (см. BoxWithConstraints).
    var activeTab by rememberSaveable { mutableStateOf(Screen.Files.route) }

    // ── Триггеры каскадной анимации появления ────────────────────────────────
    var filesEntranceTrigger by rememberSaveable { mutableStateOf(0) }
    var bellsEntranceTrigger by rememberSaveable { mutableStateOf(0) }

    // Переключение вкладки. animateEntrance=false — для свайпа: контент и так
    // уже был виден на экране всё время перетаскивания пальцем, повторный
    // каскадный "влёт" элементов в конце будет лишним и будет читаться как
    // глюк. Каскад нужен только когда вкладка появляется "внезапно" — по
    // тапу на пилл-навигацию/кнопку назад, или после закрытия глубокого
    // экрана (см. LaunchedEffect(deepScreenOpen) ниже — не трогали).
    fun switchTab(route: String, animateEntrance: Boolean) {
        if (route == activeTab) return
        activeTab = route
        if (animateEntrance) {
            if (route == Screen.Files.route) filesEntranceTrigger++ else bellsEntranceTrigger++
        }
    }

    // Тот же каскад должен проигрываться и когда мы ЗАКРЫВАЕМ глубокий экран
    // (Schedule/Settings) и возвращаемся на вкладку.
    var wasDeepScreenOpen by rememberSaveable { mutableStateOf(deepScreenOpen) }

    LaunchedEffect(deepScreenOpen) {
        if (wasDeepScreenOpen && !deepScreenOpen) {
            if (activeTab == Screen.Files.route) filesEntranceTrigger++ else bellsEntranceTrigger++
        }
        wasDeepScreenOpen = deepScreenOpen
    }

    val showPill = !deepScreenOpen

    // Системная кнопка «назад»: если открыта вкладка Bells и нет глубокого
    // экрана сверху — возвращаем на Files, а не выходим из приложения.
    BackHandler(enabled = !deepScreenOpen && activeTab == Screen.Bells.route) {
        switchTab(Screen.Files.route, animateEntrance = true)
    }

    // Прогресс Files↔Bells поднят на уровень всей функции (а не внутрь
    // Box с вкладками) — он нужен ещё и FloatingPillNav ниже, чтобы
    // индикатор в пилюле синхронно ехал за пальцем во время свайпа, а не
    // просто дожидался финального переключения activeTab.
    var widthPx by remember { mutableStateOf(0f) }

    val animMode by AnimPrefs.mode.collectAsState()
    val tweenDurationMs by AnimPrefs.durationMs.collectAsState()
    val springDamping by AnimPrefs.springDamping.collectAsState()
    val springStiffness by AnimPrefs.springStiffness.collectAsState()
    val parallaxPower by AnimPrefs.parallaxPower.collectAsState()

    val activeIndex = if (activeTab == Screen.Files.route) 0 else 1

    val swipable = rememberSwipableProgress(
        activeIndex = activeIndex,
        onSwitch = { idx -> switchTab(if (idx == 0) Screen.Files.route else Screen.Bells.route, animateEntrance = false) },
        widthPx = widthPx,
        animMode = animMode,
        tweenDurationMs = tweenDurationMs,
        springDamping = springDamping,
        springStiffness = springStiffness,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding(),
    ) {
        // ── Единая шапка ("Расписание" ↔ "Звонки" через flip) ───────────────
        // AppHeader всегда остаётся в композиции, а NavHost рисуется поверх него.
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                activeRoute = activeTab,
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )

            // ── Вкладки: Files и Bells всегда в композиции ──────────────────
            // Раньше тут стоял BoxWithConstraints — он был нужен только ради
            // maxWidth. Теперь ширина нужна ДО входа в scope (чтобы передать
            // её в rememberSwipableProgress и повесить dragModifier на сам
            // контейнер), поэтому вместо BoxWithConstraints — обычный Box с
            // onSizeChanged, а ширина живёт в состоянии (объявлено выше).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 76.dp)
                    .clipToBounds()
                    .onSizeChanged { widthPx = it.width.toFloat() }
                    .then(swipable.dragModifier),
            ) {
                val progress = swipable.progress

                // Files — фоновый слой.
                val filesOffset = -widthPx * progress
                val filesScale =
                    if (animMode == TabAnimMode.PARALLAX) {
                        lerp(1f, 0.94f, progress)
                    } else {
                        1f
                    }

                val filesAlpha =
                    if (animMode == TabAnimMode.PARALLAX) {
                        lerp(1f, 0.55f, progress)
                    } else {
                        1f
                    }

                // Bells — передний слой.
                val bellsProgress =
                    if (animMode == TabAnimMode.PARALLAX) {
                        1f - (1f - progress).pow(parallaxPower)
                    } else {
                        progress
                    }

                val bellsOffset = widthPx * (1f - bellsProgress)

                val bellsScale =
                    if (animMode == TabAnimMode.PARALLAX) {
                        lerp(0.96f, 1f, bellsProgress)
                    } else {
                        1f
                    }

                val bellsAlpha =
                    if (animMode == TabAnimMode.PARALLAX) {
                        lerp(0.6f, 1f, bellsProgress)
                    } else {
                        1f
                    }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = filesOffset
                            scaleX = filesScale
                            scaleY = filesScale
                            alpha = filesAlpha
                        },
                ) {
                    FilesScreen(
                        onFileClick = { file ->
                            NavigationHolder.pendingFile = file
                            navController.navigate(Screen.Schedule.route)
                        },
                        entranceTrigger = filesEntranceTrigger,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = bellsOffset
                            scaleX = bellsScale
                            scaleY = bellsScale
                            alpha = bellsAlpha
                        },
                ) {
                    BellsScreen(entranceTrigger = bellsEntranceTrigger)
                }
            }
        }

        // ── Глубокие экраны: Schedule, Settings — Telegram-стиль слайда ─────
        val navDurationMs by AnimPrefs.navDurationMs.collectAsState()
        NavHost(
            navController = navController,
            startDestination = TABS_PLACEHOLDER,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(
                        navDurationMs,
                        easing = FastOutSlowInEasing,
                    ),
                ) + fadeIn(animationSpec = tween(navDurationMs - 60))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(
                        navDurationMs,
                        easing = FastOutSlowInEasing,
                    ),
                ) + fadeOut(animationSpec = tween(navDurationMs - 60))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(
                        navDurationMs,
                        easing = FastOutSlowInEasing,
                    ),
                ) + fadeIn(animationSpec = tween(navDurationMs - 60))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(
                        navDurationMs,
                        easing = FastOutSlowInEasing,
                    ),
                ) + fadeOut(animationSpec = tween(navDurationMs - 60))
            },
        ) {
            // Ничего не рисует — вкладки рисует BoxWithConstraints выше.
            composable(TABS_PLACEHOLDER) {
                Box(Modifier.fillMaxSize())
            }

            composable(Screen.Schedule.route) {
                val file = NavigationHolder.pendingFile

                if (file != null) {
                    ScheduleHostScreen(
                        file = file,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToDebug = { navController.navigate(Screen.DebugSettings.route) },
                )
            }

            composable(Screen.DebugSettings.route) {
                DebugSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (showPill) {
            FloatingPillNav(
                currentRoute = activeTab,
                onNavigate = { route -> switchTab(route, animateEntrance = true) },
                progress = swipable.progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
            )
        }
    }
}

@Preview
@Composable
private fun AppScaffoldPreview() {
    AppTheme(preset = ThemePreset.DARK) {
        AppScaffold()
    }
}
