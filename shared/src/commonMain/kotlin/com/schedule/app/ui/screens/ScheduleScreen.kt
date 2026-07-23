package com.schedule.app.ui.screens

import com.schedule.app.util.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schedule.app.data.model.LessonEntry
import com.schedule.app.data.model.ScheduleDay
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.CascadeEntranceItem
import com.schedule.app.ui.components.rememberScrollCascadeState
import com.schedule.app.ui.theme.LocalAppColors

// Длительность анимации переключения между "под-экранами" ScheduleScreen
// (пикер группы ↔ расписание пар) — то же значение, что и NAV_ANIM_MS в
// AppScaffold для переходов Files/Bells → Schedule/Settings. Не переиспользуем
// константу напрямую (она private в другом файле) — просто дублируем число,
// чтобы анимации визуально совпадали.
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

@Composable
fun ScheduleScreen(
    file: ScheduleFile,
    onBack: () -> Unit,
    vm: ScheduleViewModel = viewModel { ScheduleViewModel() },
    // ── Параметры для хостинга внутри ScheduleHostScreen ────────────────────
    // active     — виден ли СЕЙЧАС этот экран пользователю (см. ScheduleHostScreen,
    //              где студенческий и преподавательский вид смонтированы ОБА
    //              одновременно и просто сдвигаются по X). Нужен, чтобы системный
    //              back-жест не перехватывался невидимой половиной.
    // revealTrigger/revealEdge — см. комментарий у lastRevealApplied ниже.
    active: Boolean = true,
    revealTrigger: Int = 0,
    revealEdge: CascadeEdge = CascadeEdge.BOTTOM,
    // onHeaderInfo — вместо того чтобы рисовать шапку/тумблер/прогресс-бар у
    // себя (как раньше делал SchedHeader), этот экран теперь только
    // ВЫЧИСЛЯЕТ их актуальное состояние и поднимает наверх, в
    // ScheduleHostScreen, который рисует единую фиксированную шапку на оба
    // режима сразу. См. ScheduleHeaderInfo в ScheduleHostScreen.kt.
    onHeaderInfo: (ScheduleHeaderInfo) -> Unit = {},
) {
    val c         = LocalAppColors.current
    val uiState   by vm.uiState.collectAsState()
    val progress  by vm.progress.collectAsState()
    val groupName by AppPrefs.groupName.collectAsState()
    val clockMin  by vm.clockMin.collectAsState()

    LaunchedEffect(file.name) { vm.load(file) }

    // ── Направление и "номер" перехода между под-экранами ──────────────────────
    // Внутри ScheduleScreen на самом деле несколько логических экранов —
    // загрузка / пикер группы / расписание пар / ошибка — и переключение между
    // ними должно выглядеть так же, как переходы между Files/Bells/Schedule в
    // AppScaffold: слайд + фейд, а не мгновенная подмена.
    //
    // goingBack — направление: true, когда мы ВОЗВРАЩАЕМСЯ к пикеру группы
    // (кнопка назад/карандаш с экрана расписания), false — когда идём вперёд
    // (первая загрузка, выбор группы, повтор после ошибки).
    var goingBack by remember { mutableStateOf(false) }

    // transitionSeq — уникальный номер каждого перехода. Передаём его вниз как
    // triggerKey для каскадных анимаций карточек (групп/пар), а не полагаемся
    // на то, что AnimatedContent сочтёт два одинаковых по содержимому состояния
    // "разными" (два GroupPicker(sameGroups) подряд технически equals()).
    var transitionSeq by remember { mutableStateOf(0) }
    LaunchedEffect(uiState) { transitionSeq++ }

    // "Экран пар" в терминах задачи — это то, что видно ПОСЛЕ выбора группы:
    // само расписание или плашка "на практике".
    val isPairsScreen = uiState is ScheduleUiState.Success || uiState is ScheduleUiState.OnPractice

    // Общее действие для стрелки "назад" и карандаша "сменить группу" — оба
    // должны вести к пикеру, а не сразу выкидывать пользователя на главный экран.
    val backToPicker: () -> Unit = {
        goingBack = true
        vm.clearGroup()
    }

    // Одноразовая "подмена" направления каскада пикера — используется только
    // когда пикер раскрыт тумблером без перезагрузки (см. lastRevealApplied
    // ниже); во всех остальных случаях действует обычная goingBack-логика
    // (LEFT назад / BOTTOM вперёд, см. GroupPickerScreen(...) ниже).
    var pickerRevealEdgeOverride by remember { mutableStateOf<CascadeEdge?>(null) }

    // Системный жест "назад" перехватываем ТОЛЬКО пока показано расписание —
    // NavHost в AppScaffold обрабатывает системный back сам, минуя параметр
    // onBack (тот срабатывает лишь по тапу на стрелку в шапке), поэтому без
    // этого BackHandler'а жест увёл бы сразу на главный экран, а не к пикеру.
    // "&& active" — пока этот экран сдвинут за край в ScheduleHostScreen
    // (виден другой режим), он не должен перехватывать системный back.
    BackHandler(enabled = isPairsScreen && active) { backToPicker() }

    // ── Каскад при "раскрытии" этого режима тумблером ───────────────────────
    // uiState тут не меняется (данные уже загружены и никуда не делись —
    // в этом и была идея держать оба экрана смонтированными), поэтому обычный
    // LaunchedEffect(uiState) выше не сработает — реагируем на revealTrigger
    // отдельно и вручную "проигрываем" карточки пикера ещё раз.
    var lastRevealApplied by remember { mutableStateOf(revealTrigger) }
    LaunchedEffect(revealTrigger) {
        if (revealTrigger != lastRevealApplied) {
            pickerRevealEdgeOverride = revealEdge
            transitionSeq++
            lastRevealApplied = revealTrigger
        }
    }
    // Override — ровно на один "проигрыш": как только transitionSeq применился
    // (в т.ч. и по этому самому revealTrigger), сбрасываем его, чтобы следующий
    // обычный переход снова считался по goingBack, а не залипал на revealEdge.
    LaunchedEffect(transitionSeq) { pickerRevealEdgeOverride = null }

    // Пока показывается пикер (или идёт загрузка) — заголовок не должен
    // показывать старое сохранённое имя группы, это сбивает с толку.
    // Карандаш «сменить группу» тоже имеет смысл только когда группа
    // реально подтверждена и расписание уже показано.
    val headerGroupName = when (uiState) {
        is ScheduleUiState.Success, is ScheduleUiState.OnPractice -> groupName
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        // Шапка/тумблер/полоса загрузки теперь рисуются один раз в
        // ScheduleHostScreen на оба режима сразу (см. ScheduleHeaderInfo) —
        // здесь только сообщаем актуальное состояние наверх.
        SideEffect {
            onHeaderInfo(
                ScheduleHeaderInfo(
                    title          = headerGroupName,
                    placeholder    = "Выберите группу",
                    dateText       = file.dateLabel,
                    isPairsScreen  = isPairsScreen,
                    isLoading      = uiState is ScheduleUiState.Loading,
                    progress       = progress,
                    filledFontSize = 22.sp,
                    // Со экрана пар стрелка ведёт к пикеру группы; с любого
                    // другого под-экрана (пикер, загрузка, ошибка) — как
                    // раньше, наружу из ScheduleScreen.
                    onBack         = if (isPairsScreen) backToPicker else onBack,
                ),
            )
        }

        AnimatedContent(
            targetState = uiState,
            modifier    = Modifier.weight(1f),
            transitionSpec = {
                val from = initialState
                val to   = targetState

                // Скелетон загрузки и реальный список групп теперь идентичны по
                // расположению (см. правки GroupPickerLoading выше) — слайд/фейд
                // между ними смотрится как лишний "дёрг" ради самого себя, поэтому
                // здесь просто мгновенная подмена контента без анимации.
                val isSkeletonToPicker =
                    from is ScheduleUiState.Loading && from.stage == LoadingStage.FILE &&
                    to is ScheduleUiState.GroupPicker

                // Idle → Loading — это самый первый внутренний переход сразу после
                // того, как NavHost только что задвинул весь ScheduleScreen целиком
                // слайдом справа (см. enterTransition в AppScaffold). Если тут ещё
                // раз слайдить содержимое, анимация "двоится" — накладывается сама
                // на себя в первые ~280мс. Idle ничего осмысленного не показывает,
                // так что для этого перехода анимация просто не нужна.
                val isInitialLoad = from is ScheduleUiState.Idle

                if (isSkeletonToPicker || isInitialLoad) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else if (goingBack) {
                    // Те же слайды, что и в AppScaffold: назад — новый экран
                    // въезжает с ЛЕВОГО края, старый уезжает вправо (см.
                    // NAV_ANIM_MS/popEnterTransition в AppScaffold.kt).
                    (slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec  = tween(SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(SUBSCREEN_ANIM_MS - 60))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
                        ) + fadeOut(tween(SUBSCREEN_ANIM_MS - 60)))
                } else {
                    // Вперёд — новый экран въезжает с ПРАВОГО края, старый чуть
                    // уезжает влево (см. enterTransition в AppScaffold.kt).
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
            label = "scheduleSubscreen",
        ) { state ->
            when (state) {
                is ScheduleUiState.Idle -> SchedLoading()

                is ScheduleUiState.Loading -> when (state.stage) {
                    LoadingStage.FILE     -> GroupPickerLoading(entranceTrigger = transitionSeq)
                    LoadingStage.SCHEDULE -> SchedLoading()
                }

                is ScheduleUiState.GroupPicker -> GroupPickerScreen(
                    groups          = state.groups,
                    onSelect        = { group -> goingBack = false; vm.selectGroup(group, file.name) },
                    entranceTrigger = transitionSeq,
                    // BOTTOM — контент только что загрузился, LEFT — вернулись
                    // с расписания пар, revealEdge — пикер "раскрыт" тумблером
                    // в ScheduleHostScreen без перезагрузки (см. pickerRevealEdgeOverride).
                    entranceEdge    = pickerRevealEdgeOverride
                        ?: if (goingBack) CascadeEdge.LEFT else CascadeEdge.BOTTOM,
                )

                is ScheduleUiState.Success -> SchedContent(
                    day             = state.day,
                    clockMin        = clockMin,
                    entranceTrigger = transitionSeq,
                )

                is ScheduleUiState.OnPractice -> SchedOnPractice(headerText = state.headerText)

                is ScheduleUiState.Error -> SchedError(
                    message = state.message,
                    onRetry = { goingBack = false; vm.load(file) },
                )
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
) {
    val c              = LocalAppColors.current
    val rememberOn     by AppPrefs.rememberGroup.collectAsState()
    val pinnedGroup    by AppPrefs.pinnedGroup.collectAsState()
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

        // Как и в FilesList: короткий список групп (1-3) центрируем по вертикали
        // вместо прилипания к верху с пустым "хвостом".
        val isShort = orderedGroups.size <= 3

        val listState = rememberLazyListState()
        // Раньше при прокрутке элементы, ушедшие за пределы экрана и
        // вернувшиеся обратно, "случайно" заново проигрывали анимацию входа
        // на экран (включая LEFT после возврата с расписания пары — что
        // выглядело нелогично). Теперь это разделено осознанно —
        // см. комментарий у ScrollCascadeState.
        val scrollCascade = rememberScrollCascadeState(listState, entranceTrigger)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                bottom = 80.dp, top = 2.dp,
            ),
            verticalArrangement = if (isShort)
                Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            else
                Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(orderedGroups, key = { _, g -> "g_$g" }) { idx, group ->
                val mount = scrollCascade.resolve("g_$group", idx, entranceEdge)
                CascadeEntranceItem(
                    index      = mount.index,
                    triggerKey = entranceTrigger,
                    enabled    = entranceEnabled,
                    edge       = mount.edge,
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
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
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
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor)
                .border(1.5.dp, borderColor, RoundedCornerShape(14.dp)),
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
                                        .clip(RoundedCornerShape(20.dp))
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
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
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
