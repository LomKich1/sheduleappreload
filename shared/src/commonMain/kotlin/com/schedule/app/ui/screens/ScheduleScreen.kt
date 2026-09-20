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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.schedule.app.ui.components.rememberSwipeDismissState
import com.schedule.app.ui.components.swipeToDismiss
import com.schedule.app.ui.theme.AppRadius
import com.schedule.app.ui.theme.LocalAppColors
import kotlin.math.roundToInt
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.rememberHazeState

// Длительность анимации переключения между "под-экранами" ScheduleScreen —
// то же значение, что и NAV_ANIM_MS в AppScaffold для переходов
// Files/Bells → Schedule/Settings. Не переиспользуем константу напрямую
// (она private в другом файле) — просто дублируем число, чтобы анимации
// визуально совпадали. Используется и для пикера (Idle→Loading→Ready), и
// для въезда PairsOverlay при тапе по группе.
private const val SUBSCREEN_ANIM_MS = 280

// ─── Скелетон для пикера группы — визуально идентичен GroupPickerScreen ───────

@Composable
private fun GroupPickerLoading(entranceTrigger: Any) {
    val c = LocalAppColors.current
    val alpha by rememberInfiniteTransition(label = "groupSkel")
        .animateFloat(
            initialValue  = 1f,
            targetValue   = 0.4f,
            animationSpec = infiniteRepeatable(
                animation  = tween(900, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "a",
        )

    // Каскад "с правого края" — тот же приём, что и у карточек BellsScreen при
    // переключении вкладок. entranceTrigger приходит снаружи, из ScheduleScreen
    // (единый счётчик переходов между под-экранами) — так вся анимация внутри
    // ScheduleScreen управляется из одного места и не зависит от того, решит
    // ли AnimatedContent пересоздать композицию этого экрана заново или нет.
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Тот же вид подсказки, что и в реальном пикере (см. GroupPickerScreen) —
        // без дублирующего жирного заголовка, только одна строка-подсказка.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Загружаем список групп…",
                color = c.textSub,
                fontSize = 11.5.sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(8) { i ->
                CascadeEntranceItem(
                    index      = i,
                    triggerKey = entranceTrigger,
                    enabled    = entranceEnabled,
                    edge       = CascadeEdge.RIGHT,
                ) {
                    val a = (alpha - i * 0.06f).coerceIn(0.3f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface.copy(alpha = a))
                            .border(1.dp, c.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c.surface2.copy(alpha = a)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier
                                .size(width = 90.dp, height = 12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.surface2.copy(alpha = a)),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(width = 8.dp, height = 12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c.surface2.copy(alpha = a)),
                        )
                    }
                }
            }
        }
    }
}

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
    // Общий HazeState ХОСТА (см. ScheduleHostScreen): оба экрана — Ученики и
    // Преподаватели — регистрируют свои списки как источники в ОДНОМ state.
    // Шапка/тумблер стоят на месте (counterTranslationX), а слои под ними
    // едут при переключении/свайпе, поэтому собственный список экрана
    // покрывает шапку лишь частично — недостающую часть блюра шапка берёт из
    // списка СОСЕДНЕГО экрана, который в этот момент как раз заезжает под неё.
    // Дефолт — на случай вызова экрана вне хоста (тогда всё как раньше).
    hazeState: HazeState = rememberHazeState(),
) {
    val c        = LocalAppColors.current
    val uiState  by vm.uiState.collectAsState()
    val progress by vm.progress.collectAsState()
    val debugTransparentBg by AppPrefs.debugTransparentOverlayBg.collectAsState()

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

    // headerBlockHeightPx — высота шапки+тумблера, измеряется живьём через
    // onGloballyPositioned (тот же приём, что уже используется чуть ниже для
    // viewportBoundsPx в GroupPickerScreen — устоявшийся в этой кодовой базе
    // паттерн). Нужна, чтобы прокинуть её как top-инсет в список: теперь
    // шапка — floating-слой ПОВЕРХ списка (см. комментарий у Box ниже), а не
    // элемент того же вертикального потока, поэтому список должен САМ знать,
    // на сколько отступить сверху, чтобы первая карточка визуально начиналась
    // там же, где раньше — и чтобы это было ЧАСТЬЮ прокрутки (contentPadding
    // внутри verticalScroll), а не статичным отступом снаружи неё — иначе
    // карточки не будут физически заезжать под шапку при скролле, и Haze
    // будет размывать пустоту (см. историю в чате про "фон темы вместо
    // блюра").
    var headerBlockHeightPx by remember { mutableStateOf(0) }
    val headerBlockHeightDp = with(LocalDensity.current) { headerBlockHeightPx.toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .background(if (debugTransparentBg) Color.Transparent else c.bg),
        ) {
            // Хосту нужен только факт "открыт ли сейчас экран пар" (см.
            // onPairsOpenChanged в комментарии к параметрам выше).
            LaunchedEffect(selection) { onPairsOpenChanged(selection != null) }

            // ── Источник Haze — ТОЛЬКО список, шапка ему СОСЕД ───────────────
            // В Haze 1.6 hazeChild, у которого есть предок-источник с тем же
            // state, рисует лишь области с zIndex СТРОГО меньше zIndex этого
            // предка (HazeEffectNode.updateEffect). При дефолтных нулях условие
            // 0 < 0 ложно — список областей пуст, блюрить нечего, и остаётся
            // один тинт ("просвечивание без размытия"). Поэтому .haze() висит
            // на обёртке вокруг списка, а шапка+тумблер (hazeChild) лежат ниже
            // в том же Box как СИБЛИНГ — канонический паттерн, тот же, что в
            // AppScaffold (debug-блюр) и IsolatedHazeDiagnosticSection.
            Box(modifier = Modifier.fillMaxSize().haze(hazeState)) {
                // ── Список — от самого верха экрана, а не "после шапки" ─────────
                // Раньше шапка+тумблер и список были соседями в одном Column —
                // список просто ехал после них, высота шапки автоматически
                // вычиталась потоком. Теперь список занимает fillMaxSize() целиком
                // от Y=0, а top-инсет (headerBlockHeightDp) живёт ВНУТРИ его
                // собственного contentPadding (см. GroupPickerScreen) — именно
                // поэтому при скролле карточки реально проезжают ПОД шапкой, а не
                // останавливаются перед ней. Шапка рисуется НИЖЕ по коду (значит
                // выше по Z-order — последний child в Box побеждает), поверх
                // списка, как floating-слой.
                AnimatedContent(
                    targetState = uiState,
                    modifier    = Modifier.fillMaxSize(),
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
                        // Idle/Loading/Error — не скроллятся, поэтому top-инсет тут
                        // обычный статичный padding СНАРУЖИ, а не contentPadding
                        // внутри скролла (в отличие от Ready/GroupPickerScreen ниже).
                        is PickerUiState.Idle -> Box(Modifier.fillMaxSize().padding(top = headerBlockHeightDp)) {
                            SchedLoading()
                        }
                        is PickerUiState.Loading -> Box(Modifier.fillMaxSize().padding(top = headerBlockHeightDp)) {
                            GroupPickerLoading(entranceTrigger = transitionSeq)
                        }
                        is PickerUiState.Ready -> GroupPickerScreen(
                            groups            = state.groups,
                            onSelect          = onSelectGroup,
                            entranceTrigger   = transitionSeq,
                            entranceEdge      = pickerRevealEdgeOverride ?: CascadeEdge.BOTTOM,
                            topContentPadding = headerBlockHeightDp,
                        )
                        is PickerUiState.Error -> Box(Modifier.fillMaxSize().padding(top = headerBlockHeightDp)) {
                            SchedError(
                                message = state.message,
                                onRetry = { vm.load(file) },
                            )
                        }
                    }
                }
            }

            // Шапка пикера + тумблер — floating-слой ПОВЕРХ списка (см.
            // комментарий выше). Обёрнуты в компенсирующий translationX (см.
            // counterTranslationX выше), поэтому остаются неподвижными на
            // экране во время свайпа/тумблера Ученики↔Преподаватели, хотя
            // физически являются частью этого же слоя пикера (и потому
            // корректно закрываются/проступают вместе с ним при свайпе
            // Picker↔Pairs — см. комментарий у ScheduleHostScreen).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerBlockHeightPx = it.size.height }
                    // Было .graphicsLayer { translationX = -counterTranslationX }
                    // — заменено на layout-фазовый offset{} (см. чат: блюр
                    // капсулы/шапки показывал только просвечивание без
                    // реального размытия — Haze на Android иногда молча
                    // уходит в "unsupported"-фоллбэк just-tint без блюра,
                    // если между источником и hazeChild есть ЧУЖОЙ
                    // graphicsLayer/RenderNode, см. issue chrisbanes/haze#117
                    // про этот фоллбэк-путь). offset{} двигает через систему
                    // layout, а не через отдельный композит-слой поверх
                    // RenderNode — визуально идентично, но не создаёт
                    // промежуточный слой, который мог сбивать Haze с толку.
                    .offset { IntOffset(x = (-counterTranslationX).roundToInt(), y = 0) },
            ) {
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
                ScheduleHeaderRow(
                    header = pickerHeader,
                    opaqueBackground = false,
                    hazeModifier = Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = c.bg,
                            blurRadius = 20.dp,
                            tint = HazeTint(c.surface.copy(alpha = 0.55f)),
                        ),
                    ),
                )
                if (pickerHeader.isLoading) {
                    LinearProgressIndicator(
                        progress   = { pickerHeader.progress },
                        modifier   = Modifier.fillMaxWidth().height(2.dp),
                        color      = c.accent,
                        trackColor = c.surface2,
                    )
                }

                Spacer(Modifier.height(10.dp))
                ScheduleModeToggle(
                    selected = mode,
                    onSelect = onModeSelect,
                    progress = modeSwipeProgress,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    opaqueBackground = false,
                    hazeModifier = Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = c.bg,
                            blurRadius = 20.dp,
                            tint = HazeTint(c.pillBg.copy(alpha = 0.5f)),
                        ),
                    ),
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        selection?.let { sel ->
            Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                PairsOverlay(
                    selection   = sel,
                    active      = active,
                    onDismissed = {
                        AppPrefs.clearGroupName()
                        selection = null
                    },
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
    onDismissed: () -> Unit,
) {
    val c  = LocalAppColors.current
    val vm: PairsViewModel = viewModel(key = "pairs-${selection.id}") { PairsViewModel() }
    val uiState  by vm.uiState.collectAsState()
    val clockMin by vm.clockMin.collectAsState()
    val debugTransparentBg by AppPrefs.debugTransparentOverlayBg.collectAsState()

    LaunchedEffect(selection.id) { vm.load(selection.bytes, selection.group, selection.file) }

    var transitionSeq by remember { mutableStateOf(0) }
    LaunchedEffect(uiState) { transitionSeq++ }

    val dismissState = rememberSwipeDismissState(onDismissed = onDismissed)

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
    // topContentPadding — высота floating-шапки+тумблера сверху (см. Box-
    // перестройку в ScheduleScreen выше). Идёт ИМЕННО в contentPadding
    // скроллящегося списка ниже, а не статичным отступом снаружи — иначе
    // карточки не будут физически заезжать под шапку при скролле, и Haze
    // будет размывать пустоту вместо них.
    topContentPadding: Dp = 0.dp,
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
        // Подсказка — по умолчанию скрыта (см. чат: шапка+капсула теперь
        // блюрные, повторять "сколько групп/что сохранится" текстом под
        // ними избыточно). Включается обратно через debug-тумблер
        // "Показывать подсказку" для отладки/сравнения.
        if (showHint) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topContentPadding)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    text = if (pinnedInFile != null)
                        "Найдено ${groups.size} групп · запомненная — вверху"
                    else
                        "Найдено ${groups.size} групп · выбор сохранится автоматически",
                    color = c.textSub,
                    fontSize = 11.5.sp,
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
                    start = 14.dp,
                    end = 14.dp,
                    bottom = 80.dp,
                    // Если подсказка показана (debug-тумблер) — она уже сама
                    // получила topContentPadding выше по коду, тут достаточно
                    // обычного 2.dp. Если подсказки нет (дефолт) — список сам
                    // отвечает за отступ под шапку, и он ОБЯЗАН быть частью
                    // contentPadding (внутри verticalScroll), а не снаружи —
                    // иначе карточки не будут заезжать под шапку при скролле.
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

@Composable
private fun GroupCard(
    name: String,
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val c           = LocalAppColors.current
    val bg          = if (isPinned) c.todayAccent.copy(alpha = 0.09f) else c.surface
    val borderColor = if (isPinned) c.todayAccent.copy(alpha = 0.32f) else c.border
    val borderWidth = if (isPinned) 2.dp else 1.dp
    val iconBg      = if (isPinned) c.todayAccent.copy(alpha = 0.18f) else c.surface2
    val iconTint    = if (isPinned) c.todayAccent else c.textSub
    val nameColor   = if (isPinned) c.todayAccent else c.text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppRadius.card)
            .background(bg)
            .border(borderWidth, borderColor, AppRadius.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Иконка-кружок (как в FileCard)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Outlined.Group,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text       = name,
            color      = nameColor,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.weight(1f),
        )

        // Раньше тут был текстовый бейдж "ЗАПОМНЕНА" для запомненной группы —
        // убрали: карточка и так узнаётся по цвету фона/иконки/текста и более
        // толстой обводке (borderWidth выше), бейдж поверх этого был избыточен.
        // Шеврон теперь одинаковый у всех карточек — все они одинаково кликабельны.
        Icon(
            imageVector        = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint               = if (isPinned) c.todayAccent else c.textSub,
            modifier           = Modifier.size(18.dp),
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
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        if (currentLesson != null && currentStatus != null) {
            item { LiveBar(lesson = currentLesson, status = currentStatus) }
        }

        item {
            Text(
                text = day.header,
                color = c.textSub,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.07.sp,
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
            )
        }

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
    val c = LocalAppColors.current
    val animProg by animateFloatAsState(
        targetValue   = status.progressPct,
        animationSpec = tween(30_000, easing = LinearEasing),
        label         = "liveProgress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("▶ ${lesson.subject}", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(status.remainText ?: "", color = c.textSub, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.surface3),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animProg)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accent),
            )
        }
    }
}

// ─── PairCard ─────────────────────────────────────────────────────────────────

@Composable
private fun PairCard(lesson: LessonEntry, status: LessonStatus) {
    val c = LocalAppColors.current

    val leftColor = when {
        status.isNow    -> c.todayAccent
        status.isNext   -> Color(0xFF50C878)
        lesson.isWindow -> c.surface3
        else            -> c.accent
    }
    val bgColor = when {
        status.isNow -> c.todayAccent.copy(alpha = 0.10f)
        else         -> c.surface2
    }
    val borderColor = when {
        status.isNow  -> c.todayAccent.copy(alpha = 0.30f)
        status.isNext -> Color(0xFF50C878).copy(alpha = 0.30f)
        else          -> c.surface3
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 9.dp)
            .then(if (lesson.isWindow) Modifier.alpha(0.6f) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppRadius.card)
                .background(bgColor)
                .border(1.5.dp, borderColor, AppRadius.card),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(leftColor),
            )

            if (lesson.isWindow) {
                // Компактная карточка «Окно» — по правке дизайнера: раньше она
                // рисовалась как настоящая пара (тот же layout, просто alpha+курсив)
                // и занимала ту же высоту, хотя показывать там, по сути, нечего.
                // Теперь — один ряд: номер пары, «Окно», и время, если оно известно.
                //
                // Важно: именно if/else, а не if{...; return@Row} — ранний выход
                // из composable-лямбды внутри LazyColumn+AnimatedContent ловил
                // ArrayIndexOutOfBoundsException в ComposerImpl.endGroup (рассинхрон
                // slot table). Симметричные ветки одного if/else компилятор Compose
                // обрабатывает штатно.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = lesson.num,
                        color = c.textSub,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.05.sp,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = "Окно",
                        color = c.textSub,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.weight(1f),
                    )
                    if (lesson.timeStart.isNotEmpty()) {
                        Text(
                            text = "${lesson.timeStart}–${lesson.timeEnd}",
                            color = c.textSub,
                            fontSize = 11.sp,
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = lesson.num,
                            color = c.textSub,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.05.sp,
                            modifier = Modifier.width(24.dp).padding(top = 2.dp),
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            if (status.isNow || status.isNext) {
                                val badgeColor = if (status.isNow) c.todayAccent else Color(0xFF50C878)
                                val badgeText  = if (status.isNow) "▶ СЕЙЧАС" else "СЛЕДУЮЩАЯ"
                                Box(
                                    modifier = Modifier
                                        .clip(AppRadius.capsule)
                                        .background(badgeColor)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(badgeText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(5.dp))
                            }

                            Text(
                                text = lesson.subject,
                                color = c.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Normal,
                                lineHeight = 19.sp,
                            )
                        }

                        if (status.remainText != null) {
                            Text(
                                text = status.remainText,
                                color = c.textSub,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, end = 14.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (lesson.timeStart.isNotEmpty()) {
                            TimeRow(time = "${lesson.timeStart}–${lesson.timeEnd}", tag = "ПАРА", muted = false)
                        }
                        if (lesson.breakStart != null && lesson.breakEnd != null) {
                            TimeRow(time = "${lesson.breakStart}–${lesson.breakEnd}", tag = "ПЕРЕМ", muted = true)
                        }
                    }

                    val detailParts = listOfNotNull(lesson.teacher, lesson.room)
                    if (detailParts.isNotEmpty()) {
                        Text(
                            text = detailParts.joinToString(" · "),
                            color = c.textSub,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp, end = 14.dp, bottom = 12.dp),
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(time: String, tag: String, muted: Boolean) {
    val c = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = time,
            color = if (muted) c.textSub else c.text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(c.surface3)
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(text = tag, color = c.textSub, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.07.sp)
        }
    }
}

// ─── Скелетон загрузки ────────────────────────────────────────────────────────

@Composable
private fun SchedLoading() {
    val c = LocalAppColors.current
    val alpha by rememberInfiniteTransition(label = "skel")
        .animateFloat(
            initialValue  = 1f,
            targetValue   = 0.4f,
            animationSpec = infiniteRepeatable(
                animation  = tween(900, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "a",
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        repeat(5) { i ->
            val a = (alpha - i * 0.08f).coerceIn(0.3f, 1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface.copy(alpha = a))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 24.dp, height = 40.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2.copy(alpha = a)))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width = 130.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2.copy(alpha = a)))
                    Box(Modifier.size(width = 80.dp, height = 10.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2.copy(alpha = a)))
                }
            }
        }
    }
}

// ─── На практике ──────────────────────────────────────────────────────────────

@Composable
private fun SchedOnPractice(headerText: String) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(c.todayAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Text("🎓", fontSize = 28.sp) }
        Spacer(Modifier.height(18.dp))
        Text("Группа на практике", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "На эту дату ($headerText) у вашей группы нет занятий — она проходит производственную практику.",
            color = c.textSub,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

// ─── Ошибка ───────────────────────────────────────────────────────────────────

@Composable
private fun SchedError(message: String, onRetry: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(c.surface2),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.WifiOff, null, tint = c.textSub, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Не удалось загрузить расписание", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = c.textSub, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .clip(AppRadius.capsule)
                .background(c.surface2)
                .border(1.dp, c.border, AppRadius.capsule)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = c.accent, modifier = Modifier.size(18.dp))
            Text("Повторить", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
