package com.schedule.app.ui.screens

import com.schedule.app.util.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import com.schedule.app.ui.components.CenteredLoading
import com.schedule.app.ui.components.ScheduleErrorState
import com.schedule.app.ui.components.PairTimeRow
import com.schedule.app.ui.components.LessonStatusBadge
import com.schedule.app.ui.components.ScheduleSectionHeader
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.model.TeacherDay
import com.schedule.app.data.model.TeacherLessonEntry
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.CascadeEntranceItem
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.components.ScheduleModeToggle
import com.schedule.app.ui.components.DismissDimScrim
import com.schedule.app.ui.components.SwipeDismissState
import com.schedule.app.ui.components.ScheduleTogglePlacement
import com.schedule.app.ui.components.PickerSearchState
import com.schedule.app.ui.components.SearchHeaderZone
import com.schedule.app.ui.components.SEARCH_SHIFT
import com.schedule.app.ui.components.SearchResultsLayer
import com.schedule.app.ui.components.blockTouches
import com.schedule.app.ui.components.rememberHideKeyboard
import com.schedule.app.ui.components.rememberSearchProgress
import com.schedule.app.ui.components.rememberSwipeDismissState
import com.schedule.app.ui.components.swipeToDismiss
import com.schedule.app.ui.theme.AppRadius
import com.schedule.app.ui.theme.LocalAppColors

// Та же длительность, что и SUBSCREEN_ANIM_MS в ScheduleScreen.kt — переходы
// пикер преподавателя ↔ расписание пар должны визуально совпадать.
private const val TEACHER_SUBSCREEN_ANIM_MS = 280

// ─── Скелетон для пикера преподавателя — визуально идентичен TeacherPickerScreen ─

@Composable
@Suppress("UNUSED_PARAMETER")
private fun TeacherPickerLoading(entranceTrigger: Any) { CenteredLoading() }

// ─── TeacherScheduleScreen ─────────────────────────────────────────────────────
// Зеркало ScheduleScreen.kt — см. большой комментарий там про архитектуру
// после рефакторинга (пикер = база, экран пар = оверлей со свайп-дисмиссом).

@Composable
fun TeacherScheduleScreen(
    file: ScheduleFile,
    onBack: () -> Unit,
    vm: TeacherPickerViewModel = viewModel { TeacherPickerViewModel() },
    // См. аналогичные параметры и комментарий в ScheduleScreen.kt — тут то же
    // самое, зеркально, для преподавательской ветки.
    active: Boolean = true,
    revealTrigger: Int = 0,
    revealEdge: CascadeEdge = CascadeEdge.BOTTOM,
    mode: ScheduleMode = ScheduleMode.STUDENT,
    onModeSelect: (ScheduleMode) -> Unit = {},
    modeSwipeProgress: Float = 0f,
    onPairsOpenChanged: (Boolean) -> Unit = {},
    // См. counterTranslationX в ScheduleScreen.kt — тот же принцип.
    counterTranslationX: Float = 0f,
    // search — состояние поиска, общее на оба вида (см. PickerSearch.kt), поднято
    // в ScheduleHostScreen. Дефолт — только чтобы превью/одиночный вызов компилились.
    search: PickerSearchState = remember { PickerSearchState() },
    hazeState: HazeState = rememberHazeState(),
    // Широкий режим (ПК): тумблер Ученики/Преподаватели скрыт, а кнопка
    // «назад» в шапке пикера остаётся только у одной из двух колонок.
    showModeToggle: Boolean = true,
    showBackButton: Boolean = true,
) {
    val c        = LocalAppColors.current
    val uiState  by vm.uiState.collectAsState()
    val progress by vm.progress.collectAsState()
    val debugTransparentBg by AppPrefs.debugTransparentOverlayBg.collectAsState()
    val searchProgress = rememberSearchProgress(search)
    val hideKeyboard = rememberHideKeyboard()
    val searchShiftPx = with(LocalDensity.current) { SEARCH_SHIFT.toPx() }

    // Системный back закрывает поиск (если клавиатура открыта — её прячет сам
    // Android до того, как back долетит сюда). Регистрируется РАНЬШЕ
    // BackHandler'а оверлея пар, поэтому при открытых парах back сначала
    // закрывает их, а не поиск.
    BackHandler(enabled = active && search.active) { search.close() }

    LaunchedEffect(file.name) { vm.load(file) }

    var transitionSeq by remember { mutableStateOf(0) }
    LaunchedEffect(uiState) { transitionSeq++ }

    var pickerRevealEdgeOverride by remember { mutableStateOf<CascadeEdge?>(null) }
    var lastRevealApplied by remember { mutableStateOf(revealTrigger) }
    LaunchedEffect(revealTrigger) {
        if (revealTrigger != lastRevealApplied) {
            pickerRevealEdgeOverride = revealEdge
            transitionSeq++
            lastRevealApplied = revealTrigger
        }
    }
    LaunchedEffect(transitionSeq) { pickerRevealEdgeOverride = null }

    // ── Экран пар — оверлей (см. ScheduleScreen.kt, тот же принцип) ─────────
    var selection by remember { mutableStateOf<TeacherPairsSelection?>(null) }
    var selectionCounter by remember { mutableStateOf(0) }

    val onSelectTeacher: (String) -> Unit = onSelect@{ teacher ->
        val (bytes, pickedFile) = vm.handoffOrNull() ?: return@onSelect
        selectionCounter++
        selection = TeacherPairsSelection(id = selectionCounter, bytes = bytes, file = pickedFile, teacher = teacher)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(selection) { onPairsOpenChanged(selection != null) }

        val pickerHeader = ScheduleHeaderInfo(
            title         = "",
            placeholder   = if (uiState is TeacherPickerUiState.Loading)
                "Загружаем список преподавателей…"
            else
                "Выберите преподавателя",
            dateText      = file.dateLabel,
            isPairsScreen = false,
            isLoading     = uiState is TeacherPickerUiState.Loading,
            progress      = progress,
            showBack      = showBackButton,
            onBack        = onBack,
        )

        // Шапка/поле поиска/тумблер — плавающие оверлеи с Haze поверх списка
        // (список — источник блюра на весь экран). Вся раскладка, инсеты и
        // поведение тумблера TOP/BOTTOM живут в PickerScaffold.
        PickerScaffold(
            modifier            = Modifier
                .zIndex(0f)
                .background(if (debugTransparentBg) Color.Transparent else c.bg),
            hazeState           = hazeState,
            search              = search,
            searchProgress      = searchProgress,
            isVisibleHalf       = active,
            header              = pickerHeader,
            mode                = mode,
            onModeSelect        = onModeSelect,
            modeSwipeProgress   = modeSwipeProgress,
            counterTranslationX = counterTranslationX,
            showModeToggle      = showModeToggle,
        ) { pad ->
            AnimatedContent(
                targetState = uiState,
                modifier    = Modifier
                    .fillMaxSize()
                    // Обычный список при поиске гаснет и чуть уезжает вверх — то же
                    // смещение, что и у шапки (SEARCH_SHIFT).
                    .graphicsLayer {
                        val p = searchProgress.value
                        alpha = 1f - p
                        translationY = -searchShiftPx * p
                    }
                    .blockTouches(search.active),
                transitionSpec = {
                    // Пикер посещается строго вперёд — см. подробный
                    // комментарий у аналогичного места в ScheduleScreen.kt.
                    val isSkeletonToPicker =
                        initialState is TeacherPickerUiState.Loading && targetState is TeacherPickerUiState.Ready
                    val isInitialLoad = initialState is TeacherPickerUiState.Idle

                    if (isSkeletonToPicker || isInitialLoad) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec  = tween(TEACHER_SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
                        ) + fadeIn(tween(TEACHER_SUBSCREEN_ANIM_MS - 60))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { -it / 4 },
                                animationSpec = tween(TEACHER_SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
                            ) + fadeOut(tween(TEACHER_SUBSCREEN_ANIM_MS - 60)))
                    }
                },
                label = "teacherPickerSubscreen",
            ) { state ->
                when (state) {
                    is TeacherPickerUiState.Idle    -> Box(Modifier.fillMaxSize().padding(top = pad.listTop)) {
                        TeacherSchedLoading()
                    }
                    is TeacherPickerUiState.Loading -> Box(Modifier.fillMaxSize().padding(top = pad.listTop)) {
                        TeacherPickerLoading(entranceTrigger = transitionSeq)
                    }
                    is TeacherPickerUiState.Ready   -> TeacherPickerScreen(
                        teachers        = state.teachers,
                        onSelect        = onSelectTeacher,
                        entranceTrigger = transitionSeq,
                        entranceEdge    = pickerRevealEdgeOverride ?: CascadeEdge.BOTTOM,
                        topContentPadding    = pad.listTop,
                        bottomContentPadding = pad.listBottom,
                    )
                    is TeacherPickerUiState.Error   -> Box(Modifier.fillMaxSize().padding(top = pad.listTop)) {
                        TeacherSchedError(
                            message = state.message,
                            onRetry = { vm.load(file) },
                        )
                    }
                }
            }

            (uiState as? TeacherPickerUiState.Ready)?.let { ready ->
                SearchResultsLayer(
                    search   = search,
                    progress = searchProgress,
                    items    = ready.teachers,
                    topInsetPx    = pad.resultsTopPx,
                    bottomInsetPx = pad.resultsBottomPx,
                ) { name ->
                    TeacherCard(name = name) {
                        hideKeyboard()
                        onSelectTeacher(name)
                    }
                }
            }

        }

        selection?.let { sel ->
            // См. подробный комментарий у аналогичного места в ScheduleScreen.kt —
            // dismissState поднят сюда, чтобы прогресс дошёл до DismissDimScrim,
            // который рисуется МЕЖДУ пикером (zIndex 0f выше) и самим оверлеем.
            val dismissState = rememberSwipeDismissState(onDismissed = { selection = null })
            val dismissProgress =
                (dismissState.offsetX.value / dismissState.widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

            DismissDimScrim(
                progress = dismissProgress,
                modifier = Modifier.zIndex(0.5f),
            )

            Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                TeacherPairsOverlay(
                    selection    = sel,
                    active       = active,
                    dismissState = dismissState,
                )
            }
        }
    }
}

// ─── Экран пар преподавателя (оверлей поверх пикера) ───────────────────────
// Зеркало PairsOverlay в ScheduleScreen.kt — см. комментарии там.

private class TeacherPairsSelection(
    val id: Int,
    val bytes: ByteArray,
    val file: ScheduleFile,
    val teacher: String,
)

@Composable
private fun TeacherPairsOverlay(
    selection: TeacherPairsSelection,
    active: Boolean,
    // dismissState — поднят наверх, в TeacherScheduleScreen (см. комментарий там).
    dismissState: SwipeDismissState,
) {
    val c  = LocalAppColors.current
    val vm: TeacherPairsViewModel = viewModel(key = "teacher-pairs-${selection.id}") { TeacherPairsViewModel() }
    val uiState  by vm.uiState.collectAsState()
    val clockMin by vm.clockMin.collectAsState()
    val debugTransparentBg by AppPrefs.debugTransparentOverlayBg.collectAsState()

    LaunchedEffect(selection.id) { vm.load(selection.bytes, selection.teacher, selection.file) }

    var transitionSeq by remember { mutableStateOf(0) }
    LaunchedEffect(uiState) { transitionSeq++ }

    BackHandler(enabled = active) { dismissState.dismiss() }

    val pairsHeader = ScheduleHeaderInfo(
        title         = selection.teacher,
        placeholder   = "",
        dateText      = selection.file.dateLabel,
        isPairsScreen = true,
        isLoading     = uiState is TeacherScheduleUiState.Loading,
        progress      = 1f,
        onBack        = { dismissState.dismiss() },
    )

    // См. подробный комментарий у аналогичного места в ScheduleScreen.kt —
    // MutableTransitionState вместо ручного Animatable-хака, который иногда
    // ронял экран за правый край без анимации обратно.
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec  = tween(TEACHER_SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
        ) + fadeIn(tween(TEACHER_SUBSCREEN_ANIM_MS - 60)),
        exit = ExitTransition.None,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeToDismiss(dismissState, enabled = active)
                .background(if (debugTransparentBg) Color.Transparent else c.bg),
        ) {
            ScheduleHeaderRow(header = pairsHeader)
            if (pairsHeader.isLoading) {
                LinearProgressIndicator(
                    progress   = { pairsHeader.progress },
                    modifier   = Modifier.fillMaxWidth().height(2.dp),
                    color      = c.accent,
                    trackColor = c.surface2,
                )
            }

            AnimatedContent(
                targetState    = uiState,
                modifier       = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label          = "teacherPairsSubstate",
            ) { state ->
                when (state) {
                    is TeacherScheduleUiState.Loading -> TeacherSchedLoading()
                    is TeacherScheduleUiState.Success -> TeacherSchedContent(
                        day             = state.day,
                        clockMin        = clockMin,
                        entranceTrigger = transitionSeq,
                    )
                    is TeacherScheduleUiState.Error   -> TeacherSchedError(message = state.message, onRetry = vm::retry)
                }
            }
        }
    }
}

// ─── Пикер преподавателя ───────────────────────────────────────────────────────

@Composable
private fun TeacherPickerScreen(
    teachers: List<String>,
    onSelect: (String) -> Unit,
    entranceTrigger: Any,
    entranceEdge: CascadeEdge,
    // См. комментарий у аналогичных параметров GroupPickerScreen.
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
) {
    val c = LocalAppColors.current
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()
    val showHint by AppPrefs.debugShowPickerHint.collectAsState()

    if (teachers.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = topContentPadding)) {
            TeacherPickerHint(count = 0)
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "В этом файле не удалось распознать ни одного преподавателя",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TeacherPickerHint(count = teachers.size, topPadding = topContentPadding)

        // Как и в GroupPickerScreen/FilesList: короткий список (1-3 преподавателя)
        // центрируем по вертикали вместо прилипания к верху.
        val isShort = teachers.size <= 3

        // См. подробный комментарий у аналогичной правки в GroupPickerScreen
        // (ScheduleScreen.kt) — та же замена LazyColumn → Column+verticalScroll
        // по той же причине (список конечный, до ~52 преподавателей по
        // ростеру колледжа, виртуализация была дороже, чем просто держать всё
        // в памяти), и по той же причине ScrollCascadeState тут больше не
        // нужен. viewportBoundsPx — тоже см. там же и в CascadeEntrance.kt:
        // гейтит старт анимации входа реальной видимостью карточки при
        // скролле, а не фактом загрузки списка.
        var viewportBoundsPx by remember { mutableStateOf<Rect?>(null) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewportBoundsPx = it.boundsInWindow() }
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding,
                    top = if (showHint) 2.dp else topContentPadding + 2.dp,
                ),
            verticalArrangement = if (isShort)
                Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            else
                Arrangement.spacedBy(8.dp),
        ) {
            teachers.forEachIndexed { idx, teacher ->
                CascadeEntranceItem(
                    index      = idx,
                    triggerKey = entranceTrigger,
                    enabled    = entranceEnabled,
                    edge       = entranceEdge,
                    viewportBoundsPx = { viewportBoundsPx },
                ) {
                    TeacherCard(name = teacher) { onSelect(teacher) }
                }
            }
        }
    }
}

@Composable
private fun TeacherPickerHint(count: Int, topPadding: Dp = 0.dp) {
    val c = LocalAppColors.current
    val showHint by AppPrefs.debugShowPickerHint.collectAsState()
    if (!showHint) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Заголовок "Выберите преподавателя" уже есть в шапке экрана
        // (TeacherHeader) — дублировать его тут не нужно, как и в
        // GroupPickerScreen (там тоже только строка с количеством).
        Text(
            text = "Найдено $count преподавателей за этот день",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Карточка преподавателя (стиль GroupCard/FileCard) ────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherCard(
    name: String,
    onClick: () -> Unit,
) {
    val scheme    = MaterialTheme.colorScheme
    val container = scheme.surfaceContainer
    val nameColor = scheme.onSurface
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor  = nameColor,
            ),
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = scheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = Icons.Outlined.Person,
                            contentDescription = null,
                            tint               = scheme.onSecondaryContainer,
                            modifier           = Modifier.size(22.dp),
                        )
                    }
                }
            },
            headlineContent = { Text(name, style = MaterialTheme.typography.titleMedium) },
            trailingContent = {
                Icon(
                    imageVector        = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint               = scheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ─── Расписание дня преподавателя ──────────────────────────────────────────────

@Composable
private fun TeacherSchedContent(day: TeacherDay, clockMin: Int, entranceTrigger: Any) {
    val c = LocalAppColors.current
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

    // У пар преподавателя может повторяться `num` (несколько групп в одну и
    // ту же пару, например при разбивке на подгруппы) — поэтому список
    // адресуем по индексу, а не по num, как в студенческом SchedContent.
    val liveStatuses = remember(day, clockMin) {
        day.lessons.map { lesson ->
            val hasTime = lesson.startMin >= 0
            val isNow   = day.isToday && hasTime && clockMin in lesson.startMin..lesson.endMin
            val isNext  = day.isToday && hasTime && !isNow &&
                          (lesson.startMin - clockMin) in 1..30
            val pct = if (isNow) {
                val total = (lesson.endMin - lesson.startMin).coerceAtLeast(1)
                ((clockMin - lesson.startMin).toFloat() / total).coerceIn(0f, 1f)
            } else 0f
            val remain = if (isNow) {
                val diff = lesson.endMin - clockMin
                if (diff <= 0) "заканч." else "$diff мин"
            } else null
            TeacherLessonStatus(isNow = isNow, isNext = isNext, progressPct = pct, remainText = remain)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        item { ScheduleSectionHeader(day.header) }

        // На "экран пар" попадают только двигаясь вперёд (выбрали преподавателя) —
        // назад сюда не возвращаются, поэтому направление каскада всегда с правого края.
        itemsIndexed(day.lessons, key = { idx, _ -> idx }) { idx, lesson ->
            CascadeEntranceItem(
                index      = idx,
                triggerKey = entranceTrigger,
                enabled    = entranceEnabled,
                edge       = CascadeEdge.RIGHT,
            ) {
                TeacherPairCard(
                    lesson = lesson,
                    status = liveStatuses.getOrNull(idx) ?: TeacherLessonStatus(),
                )
            }
        }
    }
}

private data class TeacherLessonStatus(
    val isNow: Boolean      = false,
    val isNext: Boolean     = false,
    val progressPct: Float  = 0f,
    val remainText: String? = null,
)

// ─── TeacherPairCard ────────────────────────────────────────────────────────────
// Отличие от студенческой PairCard: нет isWindow (в списке преподавателя
// только реальные пары — "Окна" сюда не попадают, см. DocParser.parseForTeacher),
// а главная строка — ГРУППА, предмет идёт под ней уточнением.

@Composable
private fun TeacherPairCard(lesson: TeacherLessonEntry, status: TeacherLessonStatus) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = when {
        status.isNow  -> scheme.primaryContainer
        status.isNext -> scheme.tertiaryContainer
        else          -> scheme.surfaceContainer
    }
    val contentColor = when {
        status.isNow  -> scheme.onPrimaryContainer
        status.isNext -> scheme.onTertiaryContainer
        else          -> scheme.onSurface
    }
    val subColor = if (status.isNow || status.isNext) contentColor.copy(alpha = 0.75f) else scheme.onSurfaceVariant

    val isWindow = false

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .then(if (isWindow) Modifier.alpha(0.6f) else Modifier),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        run {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = lesson.num,
                        style = MaterialTheme.typography.labelLarge,
                        color = subColor,
                        modifier = Modifier.width(28.dp).padding(top = 2.dp),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (status.isNow || status.isNext) {
                            LessonStatusBadge(isNow = status.isNow)
                        }
                        // Группа — главная информация в расписании преподавателя
                        Text(
                            text = lesson.group,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (status.isNow || status.isNext) contentColor else scheme.primary,
                        )
                        Text(
                            text = lesson.subject,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                        )
                    }
                    if (status.remainText != null) {
                        Text(
                            text = status.remainText,
                            style = MaterialTheme.typography.labelMedium,
                            color = subColor,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(start = 44.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (lesson.timeStart.isNotEmpty()) {
                        PairTimeRow(time = "${lesson.timeStart}–${lesson.timeEnd}", tag = "ПАРА", muted = false, contentColor = contentColor)
                    }
                    if (lesson.breakStart != null && lesson.breakEnd != null) {
                        PairTimeRow(time = "${lesson.breakStart}–${lesson.breakEnd}", tag = "ПЕРЕМ", muted = true, contentColor = contentColor)
                    }
                    if (lesson.room != null) {
                        Text(
                            text = lesson.room,
                            style = MaterialTheme.typography.bodyMedium,
                            color = subColor,
                        )
                    }
                }
            }
        }
    }
}

// ─── Скелетон загрузки ────────────────────────────────────────────────────────

@Composable
private fun TeacherSchedLoading() { CenteredLoading() }

// ─── Ошибка ───────────────────────────────────────────────────────────────────

@Composable
private fun TeacherSchedError(message: String, onRetry: () -> Unit) {
    ScheduleErrorState(
        title = "Не удалось загрузить расписание",
        message = message,
        onRetry = onRetry,
    )
}
