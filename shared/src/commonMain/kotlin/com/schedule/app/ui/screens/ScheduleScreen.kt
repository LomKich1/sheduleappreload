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
import androidx.compose.material.icons.outlined.Group
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schedule.app.data.model.LessonEntry
import com.schedule.app.data.model.ScheduleDay
import com.schedule.app.data.model.ScheduleFile
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

// Длительность анимации переключения между "под-экранами" ScheduleScreen —
// то же значение, что и NAV_ANIM_MS в AppScaffold для переходов
// Files/Bells → Schedule/Settings. Не переиспользуем константу напрямую
// (она private в другом файле) — просто дублируем число, чтобы анимации
// визуально совпадали. Используется и для пикера (Idle→Loading→Ready), и
// для въезда PairsOverlay при тапе по группе.
private const val SUBSCREEN_ANIM_MS = 280

// ─── Скелетон для пикера группы — визуально идентичен GroupPickerScreen ───────

@Composable
@Suppress("UNUSED_PARAMETER")
private fun GroupPickerLoading(entranceTrigger: Any) { CenteredLoading() }

// ─── ScheduleScreen ───────────────────────────────────────────────────────────
//
// АРХИТЕКТУРА ПОСЛЕ РЕФАКТОРИНГА (свайп-выход с экрана пар, см. чат):
// Раньше пикер и экран пар были двумя состояниями ОДНОГО ScheduleViewModel/
// AnimatedContent — из-за этого честный always-mounted свайп (как у
// Files/Bells или Ученики/Преподаватели, см. rememberSwipableProgress) был
// невозможен без костылей. Теперь:
//   • Пикер — БАЗА этого экрана, живёт всё время через GroupPickerViewModel.
//   • Экран пар — ОВЕРЛЕЙ поверх неё (PairsOverlay), который РЕАЛЬНО создаётся
//     тапом по группе (свой PairsViewModel, байты переданы hand-off'ом из
//     GroupPickerViewModel — без повторного скачивания) и РЕАЛЬНО уничтожается
//     свайпом вправо с любой точки экрана — см. SwipeDismiss.kt, комментарий
//     там объясняет, почему это не rememberSwipableProgress.
//   • Свайп асимметричен, как и должно быть: вперёд (Picker→Pairs) — только
//     тапом, назад (Pairs→Picker) — только свайпом. Это НЕ двусторонний
//     переключатель, а ближе к iOS/Telegram interactive-pop.

@Composable
fun ScheduleScreen(
    file: ScheduleFile,
    onBack: () -> Unit,
    vm: GroupPickerViewModel = viewModel { GroupPickerViewModel() },
    // ── Параметры для хостинга внутри ScheduleHostScreen ────────────────────
    // active     — виден ли СЕЙЧАС этот экран пользователю (см. ScheduleHostScreen,
    //              где студенческий и преподавательский вид смонтированы ОБА
    //              одновременно и просто сдвигаются по X). Нужен, чтобы системный
    //              back-жест (и свайп-дисмисс PairsOverlay) не перехватывался
    //              невидимой половиной.
    // revealTrigger/revealEdge — см. комментарий у lastRevealApplied ниже.
    active: Boolean = true,
    revealTrigger: Int = 0,
    revealEdge: CascadeEdge = CascadeEdge.BOTTOM,
    // mode/onModeSelect/modeSwipeProgress — состояние тумблера "Ученики/
    // Преподаватели", поднятое в ScheduleHostScreen (общее на оба под-экрана,
    // см. комментарий там). Сам тумблер теперь рисуется ЗДЕСЬ же, в слое
    // пикера (см. ниже) — раньше рисовался единым экземпляром в хосте, но
    // тогда его нельзя было "естественно" спрятать под открытым экраном
    // пар — только вручную гейтить условием/альфой, что и обсуждали в чате.
    mode: ScheduleMode = ScheduleMode.STUDENT,
    onModeSelect: (ScheduleMode) -> Unit = {},
    modeSwipeProgress: Float = 0f,
    // onPairsOpenChanged — раньше отсюда наверх поднималась ПОЛНАЯ шапка пар
    // (onPairsHeaderInfo) плюс живой прогресс свайпа-закрытия, чтобы хост мог
    // синхронно двигать общую шапку/тумблер. Теперь шапка пар рисуется прямо
    // в PairsOverlay ниже, и хосту нужен только простой факт "открыт ли
    // сейчас экран пар" — для блокировки жеста Ученики↔Преподаватели.
    onPairsOpenChanged: (Boolean) -> Unit = {},
    // counterTranslationX — тот же translationX (studentOffset/teacherOffset),
    // что и накладывает ScheduleHostScreen на ВЕСЬ этот экран целиком во
    // время свайпа/тумблера Ученики↔Преподаватели. Шапка и тумблер ниже
    // компенсируют его обратным знаком у себя, поэтому визуально остаются
    // неподвижными на экране, пока весь остальной контент (список групп)
    // честно едет вместе со своим слоем — то есть z-order/раскрытие
    // Picker↔Pairs остаётся "настоящим" (см. комментарий у ScheduleHostScreen),
    // а неподвижность шапки/тумблера — отдельный, чисто визуальный трюк поверх
    // этого. Точная компенсация работает в DEFAULT/SPRING (там scale = 1); в
    // PARALLAX добавляется небольшой (пропорционально 1 - scale) остаточный
    // дрейф, т.к. масштаб родителя чуть подрастягивает саму компенсацию —
    // на глаз почти незаметно, но не идеально ноль.
    counterTranslationX: Float = 0f,
    // search — состояние поиска, общее на оба вида (см. PickerSearch.kt), поднято
    // в ScheduleHostScreen. Дефолт — только чтобы превью/одиночный вызов компилились.
    search: PickerSearchState = remember { PickerSearchState() },
    hazeState: HazeState = rememberHazeState(),
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

    // transitionSeq — уникальный номер каждого перехода пикера. Передаём его
    // вниз как triggerKey для каскадных анимаций карточек групп, а не
    // полагаемся на то, что AnimatedContent сочтёт два одинаковых по
    // содержимому состояния "разными".
    var transitionSeq by remember { mutableStateOf(0) }
    LaunchedEffect(uiState) { transitionSeq++ }

    // Одноразовая "подмена" направления каскада пикера — используется только
    // когда пикер раскрыт тумблером без перезагрузки (см. lastRevealApplied
    // ниже). revealTrigger — per-screen (см. историю в ScheduleHostScreen.kt).
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

    // ── Экран пар — теперь реальный оверлей, не состояние uiState ───────────
    // selection == null → оверлея нет вообще (даже не смонтирован).
    // selectionCounter — уникальный id на каждый тап, чтобы viewModel(key=...)
    // пересоздавал PairsViewModel с нуля при каждом новом выборе группы (а не
    // переиспользовал старый экземпляр с чужими данными).
    var selection by remember { mutableStateOf<PairsSelection?>(null) }
    var selectionCounter by remember { mutableStateOf(0) }

    val onSelectGroup: (String) -> Unit = onSelect@{ group ->
        val (bytes, pickedFile) = vm.handoffOrNull() ?: return@onSelect
        AppPrefs.saveGroupName(group)
        selectionCounter++
        selection = PairsSelection(id = selectionCounter, bytes = bytes, file = pickedFile, group = group)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(selection) { onPairsOpenChanged(selection != null) }

        val pickerHeader = ScheduleHeaderInfo(
            title         = "",
            placeholder   = if (uiState is PickerUiState.Loading)
                "Загружаем список групп…"
            else
                "Выберите группу",
            dateText      = file.dateLabel,
            isPairsScreen = false,
            isLoading     = uiState is PickerUiState.Loading,
            progress      = progress,
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
                    // Пикер теперь посещается СТРОГО вперёд: Idle→Loading→Ready,
                    // либо Error→Loading→Ready при повторе. Раньше тут был ещё
                    // goingBack-переход (LEFT-слайд при возврате с экрана пар),
                    // но с оверлеем возврат больше не трогает состояние пикера
                    // вообще — PairsOverlay просто закрывается, а пикер под ним
                    // как был отрисован, так и остаётся. Отдельная "обратная"
                    // ветка тут больше не нужна.
                    val isSkeletonToPicker =
                        initialState is PickerUiState.Loading && targetState is PickerUiState.Ready
                    val isInitialLoad = initialState is PickerUiState.Idle

                    if (isSkeletonToPicker || isInitialLoad) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec  = tween(SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
                        ) + fadeIn(tween(SUBSCREEN_ANIM_MS - 60))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { -it / 4 },
                                animationSpec = tween(SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
                            ) + fadeOut(tween(SUBSCREEN_ANIM_MS - 60)))
                    }
                },
                label = "pickerSubscreen",
            ) { state ->
                when (state) {
                    is PickerUiState.Idle    -> Box(Modifier.fillMaxSize().padding(top = pad.listTop)) {
                        SchedLoading()
                    }
                    is PickerUiState.Loading -> Box(Modifier.fillMaxSize().padding(top = pad.listTop)) {
                        GroupPickerLoading(entranceTrigger = transitionSeq)
                    }
                    is PickerUiState.Ready   -> GroupPickerScreen(
                        groups          = state.groups,
                        onSelect        = onSelectGroup,
                        entranceTrigger = transitionSeq,
                        entranceEdge    = pickerRevealEdgeOverride ?: CascadeEdge.BOTTOM,
                        topContentPadding    = pad.listTop,
                        bottomContentPadding = pad.listBottom,
                    )
                    is PickerUiState.Error   -> Box(Modifier.fillMaxSize().padding(top = pad.listTop)) {
                        SchedError(
                            message = state.message,
                            onRetry = { vm.load(file) },
                        )
                    }
                }
            }

            (uiState as? PickerUiState.Ready)?.let { ready ->
                SearchResultsLayer(
                    search   = search,
                    progress = searchProgress,
                    items    = ready.groups,
                    topInsetPx    = pad.resultsTopPx,
                    bottomInsetPx = pad.resultsBottomPx,
                ) { name ->
                    GroupCard(name = name, isPinned = false) {
                        hideKeyboard()
                        onSelectGroup(name)
                    }
                }
            }

        }

        selection?.let { sel ->
            // dismissState теперь создаётся ЗДЕСЬ, а не внутри PairsOverlay —
            // нужен ещё и снаружи самого оверлея, чтобы прогресс дошёл до
            // DismissDimScrim (см. её комментарий в SwipeDismiss.kt), который
            // рисуется МЕЖДУ пикером (zIndex 0f выше) и самим оверлеем.
            val dismissState = rememberSwipeDismissState(
                onDismissed = {
                    AppPrefs.clearGroupName()
                    selection = null
                },
            )
            val dismissProgress =
                (dismissState.offsetX.value / dismissState.widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

            DismissDimScrim(
                progress = dismissProgress,
                modifier = Modifier.zIndex(0.5f),
            )

            Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                PairsOverlay(
                    selection    = sel,
                    active       = active,
                    dismissState = dismissState,
                )
            }
        }
    }
}

// ─── Экран пар (оверлей поверх пикера) ─────────────────────────────────────
//
// Создаётся тапом по карточке группы, уничтожается свайпом вправо с любой
// точки экрана (или программным dismiss() — кнопка "назад"/системный back) —
// см. большой комментарий у ScheduleScreen выше и SwipeDismiss.kt.

private class PairsSelection(
    val id: Int,
    val bytes: ByteArray,
    val file: ScheduleFile,
    val group: String,
)

@Composable
private fun PairsOverlay(
    selection: PairsSelection,
    active: Boolean,
    // dismissState — поднят наверх, в ScheduleScreen (см. комментарий там):
    // нужен ещё и снаружи этого оверлея, для DismissDimScrim над пикером.
    dismissState: SwipeDismissState,
) {
    val c  = LocalAppColors.current
    val vm: PairsViewModel = viewModel(key = "pairs-${selection.id}") { PairsViewModel() }
    val uiState  by vm.uiState.collectAsState()
    val clockMin by vm.clockMin.collectAsState()
    val debugTransparentBg by AppPrefs.debugTransparentOverlayBg.collectAsState()

    LaunchedEffect(selection.id) { vm.load(selection.bytes, selection.group, selection.file) }

    var transitionSeq by remember { mutableStateOf(0) }
    LaunchedEffect(uiState) { transitionSeq++ }

    // Системный back — та же анимация, что и живой свайп, не мгновенное
    // исчезновение. "&& active" — пока эта половина Student/Teacher сдвинута
    // за край в ScheduleHostScreen, она не должна перехватывать back.
    BackHandler(enabled = active) { dismissState.dismiss() }

    val pairsHeader = ScheduleHeaderInfo(
        title         = selection.group,
        placeholder   = "",
        dateText      = selection.file.dateLabel,
        isPairsScreen = true,
        isLoading     = uiState is ScheduleUiState.Loading,
        progress      = 1f,
        onBack        = { dismissState.dismiss() },
    )

    // Въезд экрана при монтировании — MutableTransitionState(false→true), а не
    // ручной Animatable-хак (см. историю правок: та версия иногда роняла экран
    // за правый край и НЕ доигрывала анимацию обратно — баг с "пустым" экраном
    // на самом деле был экраном, уехавшим за пределы видимой области).
    // Uход же остаётся на откупе живого свайпа/dismissState.dismiss() — они
    // доигрывают анимацию ДО того, как selection станет null, поэтому exit
    // здесь не нужен (ExitTransition.None).
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec  = tween(SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
        ) + fadeIn(tween(SUBSCREEN_ANIM_MS - 60)),
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
                transitionSpec = {
                    // Тут только внутренние подсостояния ОДНОГО и того же
                    // выбора группы (Loading → Success/OnPractice/Error, либо
                    // повтор после Error) — сам экран уже "въехал" один раз
                    // при монтировании (см. visibleState выше), простого
                    // fade достаточно.
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "pairsSubstate",
            ) { state ->
                when (state) {
                    is ScheduleUiState.Loading    -> SchedLoading()
                    is ScheduleUiState.Success    -> SchedContent(
                        day             = state.day,
                        clockMin        = clockMin,
                        entranceTrigger = transitionSeq,
                    )
                    is ScheduleUiState.OnPractice -> SchedOnPractice(headerText = state.headerText)
                    is ScheduleUiState.Error      -> SchedError(message = state.message, onRetry = vm::retry)
                }
            }
        }
    }
}

// ─── Пикер группы ─────────────────────────────────────────────────────────────

@Composable
private fun GroupPickerScreen(
    groups: List<String>,
    onSelect: (String) -> Unit,
    entranceTrigger: Any,
    entranceEdge: CascadeEdge,
    // topContentPadding — высота плавающей шапки/тумблера (список идёт на весь
    // экран под ней, чтобы Haze было что блюрить); bottomContentPadding —
    // резерв снизу (тумблер при BOTTOM + навбар). Оба считает PickerScaffold.
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
) {
    val c              = LocalAppColors.current
    val rememberOn     by AppPrefs.rememberGroup.collectAsState()
    val pinnedGroup    by AppPrefs.pinnedGroup.collectAsState()
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()
    val showHint       by AppPrefs.debugShowPickerHint.collectAsState()

    // Подсвечиваем только если rememberGroup ON + группа реально есть в этом файле
    val pinnedInFile = if (rememberOn && pinnedGroup.isNotBlank() && pinnedGroup in groups)
        pinnedGroup else null

    // Единый список без разрывов по высоте: запомненная группа просто идёт первой,
    // отличаясь только цветом/обводкой (см. GroupCard) — раньше тут были ещё
    // секционные подписи "ЗАПОМНЕННАЯ"/"ВСЕ ГРУППЫ" со своими отступами, которые
    // визуально рвали список на куски без особой пользы.
    val orderedGroups = if (pinnedInFile != null)
        listOf(pinnedInFile) + groups.filter { it != pinnedInFile }
    else groups

    Column(modifier = Modifier.fillMaxSize()) {
        // Подсказка — без дублирующего заголовка "Выберите вашу группу": он и так
        // виден в шапке экрана прямо над этим блоком. Оставили только то, что
        // реально несёт новую информацию — сколько групп и что выбор сохранится.
        if (showHint) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topContentPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (pinnedInFile != null)
                        "Найдено ${groups.size} групп · запомненная — вверху"
                    else
                        "Найдено ${groups.size} групп · выбор сохранится автоматически",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Как и в FilesList: короткий список групп (1-3) центрируем по вертикали
        // вместо прилипания к верху с пустым "хвостом".
        val isShort = orderedGroups.size <= 3

        // Раньше тут стоял LazyColumn — виртуализация, при которой карточки,
        // ушедшие за пределы экрана, полностью уничтожаются и пересоздаются
        // при возврате в вьюпорт. Список групп конечен и небольшой (обычно
        // до полусотни строк в самом крупном файле), поэтому цена
        // виртуализации (постоянная композиция/декомпозиция карточек при
        // каждом скролле) оказалась дороже, чем просто держать все карточки
        // в памяти сразу — LazyColumn и вызывал ощутимые подтормаживания при
        // прокрутке, о которых просили разобраться. Обычный Column с
        // verticalScroll строит вообще ВСЕ карточки один раз при первом
        // появлении экрана и просто скроллит уже готовое дерево — скроллинг
        // становится чисто графической операцией без перекомпозиции.
        //
        // Вместе с этим ScrollCascadeState (см. CascadeEntrance.kt) тут
        // больше не нужен и убран: он существовал ИМЕННО для того, чтобы
        // отличать "первое появление карточки" от "пересоздания при
        // скролле" — раз пересоздания при скролле больше физически не
        // происходит, разделять эти случаи незачем.
        //
        // viewportBoundsPx — компенсирует ДРУГОЙ побочный эффект того же
        // перехода: раз все карточки строятся сразу, без него анимация
        // входа проигрывалась бы у всех разом при открытии экрана, а не по
        // факту попадания в кадр при скролле (см. подробный комментарий в
        // CascadeEntrance.kt). Ловим bounds именно этого Column — он же и
        // есть видимая область: fillMaxSize() выше заставляет его занимать
        // ровно столько, сколько выделено родителем, а verticalScroll сам
        // клипует содержимое по этим границам.
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
                    // Подсказка включена — она сама стоит под шапкой, списку
                    // хватает 2.dp; выключена — списку нужен весь верхний отступ.
                    top = if (showHint) 2.dp else topContentPadding + 2.dp,
                ),
            verticalArrangement = if (isShort)
                Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            else
                Arrangement.spacedBy(8.dp),
        ) {
            orderedGroups.forEachIndexed { idx, group ->
                CascadeEntranceItem(
                    index      = idx,
                    triggerKey = entranceTrigger,
                    enabled    = entranceEnabled,
                    edge       = entranceEdge,
                    viewportBoundsPx = { viewportBoundsPx },
                ) {
                    GroupCard(name = group, isPinned = group == pinnedInFile) { onSelect(group) }
                }
            }
        }
    }
}

// ─── Карточка группы (стиль FileCard: иконка + название + бейдж/шеврон) ──────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupCard(
    name: String,
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val scheme    = MaterialTheme.colorScheme
    val container = if (isPinned) scheme.primaryContainer else scheme.surfaceContainer
    val nameColor = if (isPinned) scheme.onPrimaryContainer else scheme.onSurface
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
                    color = if (isPinned) scheme.primary else scheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = Icons.Outlined.Group,
                            contentDescription = null,
                            tint               = if (isPinned) scheme.onPrimary else scheme.onSecondaryContainer,
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
                    tint               = if (isPinned) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ─── Расписание дня ───────────────────────────────────────────────────────────

@Composable
private fun SchedContent(day: ScheduleDay, clockMin: Int, entranceTrigger: Any) {
    val c = LocalAppColors.current
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

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
            LessonStatus(isNow = isNow, isNext = isNext, progressPct = pct, remainText = remain)
        }
    }

    val currentStatus = liveStatuses.firstOrNull { it.isNow }
    val currentLesson = if (currentStatus != null)
        day.lessons[liveStatuses.indexOf(currentStatus)] else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        if (currentLesson != null && currentStatus != null) {
            item { LiveBar(lesson = currentLesson, status = currentStatus) }
        }

        item { ScheduleSectionHeader(day.header) }

        // На "экран пар" попадают только двигаясь вперёд (выбрали группу) — назад
        // сюда не возвращаются, поэтому направление каскада всегда с правого края.
        itemsIndexed(day.lessons, key = { _, lesson -> lesson.num }) { idx, lesson ->
            val status = liveStatuses.getOrNull(idx) ?: LessonStatus()
            CascadeEntranceItem(
                index      = idx,
                triggerKey = entranceTrigger,
                enabled    = entranceEnabled,
                edge       = CascadeEdge.RIGHT,
            ) {
                PairCard(lesson = lesson, status = status)
            }
        }
    }
}

private data class LessonStatus(
    val isNow: Boolean      = false,
    val isNext: Boolean     = false,
    val progressPct: Float  = 0f,
    val remainText: String? = null,
)

// ─── Live progress bar ────────────────────────────────────────────────────────

@Composable
private fun LiveBar(lesson: LessonEntry, status: LessonStatus) {
    val scheme = MaterialTheme.colorScheme
    val animProg by animateFloatAsState(
        targetValue   = status.progressPct,
        animationSpec = tween(30_000, easing = LinearEasing),
        label         = "liveProgress",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = scheme.primaryContainer,
            contentColor   = scheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "▶ ${lesson.subject}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(status.remainText ?: "", style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress   = { animProg },
                modifier   = Modifier.fillMaxWidth().height(4.dp),
                color      = scheme.primary,
                trackColor = scheme.surfaceContainerHighest.copy(alpha = 0.6f),
            )
        }
    }
}

// ─── PairCard ─────────────────────────────────────────────────────────────────

@Composable
private fun PairCard(lesson: LessonEntry, status: LessonStatus) {
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

    val isWindow = lesson.isWindow

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .then(if (isWindow) Modifier.alpha(0.6f) else Modifier),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        if (isWindow) {
            // Компактная карточка «Окно»: один ряд — номер, «Окно» и время (если известно).
            // Важно: именно if/else, а не ранний return из composable-лямбды — ранний
            // выход внутри LazyColumn+AnimatedContent ронял slot table Compose.
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = lesson.num,
                    style = MaterialTheme.typography.labelLarge,
                    color = subColor,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = "Окно",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = subColor,
                    modifier = Modifier.weight(1f),
                )
                if (lesson.timeStart.isNotEmpty()) {
                    Text(
                        text = "${lesson.timeStart}–${lesson.timeEnd}",
                        style = MaterialTheme.typography.labelMedium,
                        color = subColor,
                    )
                }
            }
        } else {
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
                    val detailParts = listOfNotNull(lesson.teacher, lesson.room)
                    if (detailParts.isNotEmpty()) {
                        Text(
                            text = detailParts.joinToString(" · "),
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
private fun SchedLoading() { CenteredLoading() }

// ─── На практике ──────────────────────────────────────────────────────────────

@Composable
private fun SchedOnPractice(headerText: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = scheme.tertiaryContainer, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("🎓", fontSize = 28.sp) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Группа на практике",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "На эту дату ($headerText) у вашей группы нет занятий — она проходит производственную практику.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

// ─── Ошибка ───────────────────────────────────────────────────────────────────

@Composable
private fun SchedError(message: String, onRetry: () -> Unit) {
    ScheduleErrorState(
        title = "Не удалось загрузить расписание",
        message = message,
        onRetry = onRetry,
    )
}
