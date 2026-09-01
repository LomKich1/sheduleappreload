package com.schedule.app.ui

import com.schedule.app.util.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.schedule.app.ui.navigation.FloatingPillNav
import com.schedule.app.ui.navigation.NavigationHolder
import com.schedule.app.ui.navigation.Screen
import com.schedule.app.ui.screens.BellsScreen
import com.schedule.app.ui.screens.FilesScreen
import com.schedule.app.ui.screens.ScheduleHostScreen
import com.schedule.app.ui.screens.SettingsScreen
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import androidx.compose.runtime.collectAsState
import kotlin.math.pow

// ── Длительности ─────────────────────────────────────────────────────────────
private const val NAV_ANIM_MS = 340   // глубокие экраны: Schedule, Settings

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

    // ── Триггеры каскадной анимации появления ────────────────────────────────
    //  Каждый увеличивается ровно в момент, когда соответствующая вкладка
    //  становится активной (включая самый первый показ) — CascadeEntranceItem
    //  внутри FilesScreen/BellsScreen смотрит на изменение этого числа и
    //  заново проигрывает анимацию своих элементов.
    var filesEntranceTrigger by rememberSaveable { mutableStateOf(0) }
    var bellsEntranceTrigger by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(activeTab) {
        if (activeTab == Screen.Files.route) filesEntranceTrigger++ else bellsEntranceTrigger++
    }

    // Тот же каскад должен проигрываться и когда мы ЗАКРЫВАЕМ глубокий экран
    // (Schedule/Settings) и возвращаемся на вкладку — например, после того как
    // вышли из расписания пар через пикер группы обратно на главный экран.
    // activeTab при этом не меняется (мы всё это время оставались на вкладке
    // Files), поэтому LaunchedEffect(activeTab) выше тут не сработает — нужен
    // отдельный триггер именно на "deepScreenOpen: true → false".
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
        // ── Единая шапка ("Расписание" ↔ "Звонки" через flip) ───────────────
        //  Раньше каждая вкладка рисовала свою собственную шапку — из-за
        //  этого при переключении не было анимации самого заголовка, он
        //  просто резко подменялся. Теперь шапка одна на обе вкладки.
        //
        //  ВАЖНО: раньше она пряталась через `if (!deepScreenOpen)` на время
        //  Schedule/Settings — из-за этого при открытии глубокого экрана
        //  AppHeader мгновенно пропадал ИЗ КОМПОЗИЦИИ, Column ужимался, и
        //  BoxWithConstraints с вкладками дёргался вверх на кадр раньше, чем
        //  NavHost успевал наехать сверху и всё перекрыть — заметный сдвиг
        //  интерфейса. AppHeader теперь всегда в композиции: NavHost рисуется
        //  поверх этого Column в том же родительском Box и его экраны
        //  (Schedule/Settings) непрозрачны — шапку всё равно не видно, пока
        //  что-то открыто, но сама Column больше не меняет размер.
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                activeRoute     = activeTab,
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )

            // ── Вкладки: Files и Bells всегда в композиции ──────────────────
            //  Раньше это были composable() внутри NavHost — отсюда и лаг.
            //  Теперь оба экрана собраны один раз и просто сдвигаются по X.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (showPill) Modifier.padding(bottom = 76.dp) else Modifier)
                    .clipToBounds(), // чтобы неактивная вкладка не вылезала за край
            ) {
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }

                // Files стоит в 0, Bells сдвинута на +width (ждёт справа).
                // Какой конкретно расчёт офсета/scale/alpha использовать —
                // зависит от режима из AnimPrefs (debug-настройка). Обычный
                // пользователь релиза всегда на DEFAULT — это ровно то
                // поведение, что было в проекте до всех экспериментов.
                val animMode        by AnimPrefs.mode.collectAsState()
                val tweenDurationMs by AnimPrefs.durationMs.collectAsState()
                val springDamping   by AnimPrefs.springDamping.collectAsState()
                val springStiffness by AnimPrefs.springStiffness.collectAsState()
                val parallaxPower   by AnimPrefs.parallaxPower.collectAsState()

                val targetProgress = if (activeTab == Screen.Files.route) 0f else 1f

                val progress: Float = when (animMode) {
                    TabAnimMode.DEFAULT -> {
                        // Оригинальное поведение: простой tween, без пружины.
                        val p by animateFloatAsState(
                            targetValue   = targetProgress,
                            animationSpec = tween(tweenDurationMs, easing = FastOutSlowInEasing),
                            label         = "tabProgressDefault",
                        )
                        p
                    }
                    TabAnimMode.SPRING, TabAnimMode.PARALLAX -> {
                        val p by animateFloatAsState(
                            targetValue   = targetProgress,
                            animationSpec = spring(dampingRatio = springDamping, stiffness = springStiffness),
                            label         = "tabProgressSpring",
                        )
                        p
                    }
                }

                // Files — фоновый слой.
                // DEFAULT/SPRING: жёсткий 1:1 офсет, без scale/alpha/параллакса —
                //                 ровно как раньше, только источник прогресса разный.
                // PARALLAX: та же дистанция, лёгкий scale+alpha добавляют глубину.
                val filesOffset = -widthPx * progress
                val filesScale  = if (animMode == TabAnimMode.PARALLAX) lerp(1f, 0.94f, progress) else 1f
                val filesAlpha  = if (animMode == TabAnimMode.PARALLAX) lerp(1f, 0.55f, progress) else 1f

                // Bells — передний слой.
                // PARALLAX: своя кривая для offset/scale/alpha — та же дистанция
                //           и те же концевые точки, но догоняет резче к концу
                //           (parallaxPower настраивается в дебаг-панели).
                // DEFAULT/SPRING: жёсткий 1:1 офсет вместе с Files, без глубины.
                val bellsProgress = if (animMode == TabAnimMode.PARALLAX) {
                    1f - (1f - progress).pow(parallaxPower)
                } else {
                    progress
                }
                val bellsOffset = widthPx * (1f - bellsProgress)
                val bellsScale  = if (animMode == TabAnimMode.PARALLAX) lerp(0.96f, 1f, bellsProgress) else 1f
                val bellsAlpha  = if (animMode == TabAnimMode.PARALLAX) lerp(0.6f, 1f, bellsProgress) else 1f

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
                        onFileClick     = { file ->
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
                    ScheduleHostScreen(
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

@Preview
@Composable
private fun AppScaffoldPreview() {
    AppTheme(preset = ThemePreset.DARK) { AppScaffold() }
}

