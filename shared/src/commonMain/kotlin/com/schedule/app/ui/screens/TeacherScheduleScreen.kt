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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.model.TeacherDay
import com.schedule.app.data.model.TeacherLessonEntry
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.CascadeEntranceItem
import com.schedule.app.ui.components.rememberScrollCascadeState
import com.schedule.app.ui.theme.LocalAppColors

// Та же длительность, что и SUBSCREEN_ANIM_MS в ScheduleScreen.kt — переходы
// пикер преподавателя ↔ расписание пар должны визуально совпадать с
// переходами пикер группы ↔ расписание пар у студенческой ветки.
private const val TEACHER_SUBSCREEN_ANIM_MS = 280

// ─── Скелетон для пикера преподавателя — визуально идентичен TeacherPickerScreen ─

@Composable
private fun TeacherPickerLoading(entranceTrigger: Any) {
    val c = LocalAppColors.current
    val alpha by rememberInfiniteTransition(label = "teacherSkel")
        .animateFloat(
            initialValue  = 1f,
            targetValue   = 0.4f,
            animationSpec = infiniteRepeatable(
                animation  = tween(900, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "a",
        )

    // Тот же каскад "с правого края", что и в GroupPickerLoading/BellsScreen —
    // подробности см. в комментарии там. entranceTrigger приходит снаружи (единый
    // счётчик переходов из TeacherScheduleScreen), а не создаётся локально —
    // иначе каждая перекомпозиция скелетона считалась бы новым запуском каскада.
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Выберите преподавателя",
                color = c.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Загружаем список преподавателей…",
                color = c.textSub,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
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
                                .size(width = 140.dp, height = 12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.surface2.copy(alpha = a)),
                        )
                    }
                }
            }
        }
    }
}

// ─── TeacherScheduleScreen ─────────────────────────────────────────────────────

@Composable
fun TeacherScheduleScreen(
    file: ScheduleFile,
    onBack: () -> Unit,
    vm: TeacherScheduleViewModel = viewModel { TeacherScheduleViewModel() },
    // См. аналогичные параметры и комментарий в ScheduleScreen.kt — тут то же
    // самое, зеркально, для преподавательской ветки.
    active: Boolean = true,
    revealTrigger: Int = 0,
    revealEdge: CascadeEdge = CascadeEdge.BOTTOM,
    // onHeaderInfo — см. подробный комментарий у аналогичного параметра в
    // ScheduleScreen.kt: этот экран больше не рисует шапку/тумблер/прогресс-
    // бар сам, а поднимает их состояние наверх в ScheduleHostScreen.
    onHeaderInfo: (ScheduleHeaderInfo) -> Unit = {},
) {
    val c            = LocalAppColors.current
    val uiState      by vm.uiState.collectAsState()
    val progress     by vm.progress.collectAsState()
    val teacherName  by vm.teacherName.collectAsState()
    val clockMin     by vm.clockMin.collectAsState()

    LaunchedEffect(file.name) { vm.load(file) }

    // ── Направление и "номер" перехода между под-экранами — зеркало ScheduleScreen ──
    // goingBack — true, когда возвращаемся к пикеру преподавателя (стрелка назад
    // или карандаш с экрана расписания), false — когда идём вперёд.
    var goingBack by remember { mutableStateOf(false) }

    // transitionSeq — уникальный номер каждого перехода, передаётся вниз как
    // triggerKey для каскадных анимаций карточек.
    var transitionSeq by remember { mutableStateOf(0) }
    LaunchedEffect(uiState) { transitionSeq++ }

    val isPairsScreen = uiState is TeacherUiState.Success

    // Общее действие для стрелки "назад" и карандаша "сменить преподавателя" —
    // оба ведут к пикеру, а не сразу выкидывают на главный экран.
    val backToPicker: () -> Unit = {
        goingBack = true
        vm.clearTeacher()
    }

    // Системный жест "назад" перехватываем только пока показано расписание —
    // см. подробный комментарий у аналогичного BackHandler в ScheduleScreen.kt.
    // "&& active" — та же причина, что и там: невидимая половина
    // ScheduleHostScreen не должна перехватывать системный back.
    BackHandler(enabled = isPairsScreen && active) { backToPicker() }

    // Одноразовая подмена направления каскада пикера преподавателя — см.
    // подробный комментарий у pickerRevealEdgeOverride в ScheduleScreen.kt.
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

    // Как и в ScheduleScreen: пока показывается пикер/загрузка — в шапке
    // не должно мелькать прошлое имя преподавателя из предыдущего файла.
    val headerTeacherName = when (uiState) {
        is TeacherUiState.Success -> teacherName
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
                    title          = headerTeacherName,
                    placeholder    = "Выберите преподавателя",
                    dateText       = file.dateLabel,
                    isPairsScreen  = isPairsScreen,
                    isLoading      = uiState is TeacherUiState.Loading,
                    progress       = progress,
                    filledFontSize = 20.sp,
                    // Со экрана пар стрелка ведёт к пикеру преподавателя; с
                    // любого другого под-экрана — как раньше, наружу из
                    // TeacherScheduleScreen.
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

                // Скелетон загрузки и реальный пикер преподавателя визуально
                // идентичны по расположению — мгновенная подмена без анимации,
                // как и у GroupPickerLoading → GroupPicker в ScheduleScreen.
                val isSkeletonToPicker =
                    from is TeacherUiState.Loading && from.stage == LoadingStage.FILE &&
                    to is TeacherUiState.TeacherPicker

                // Idle → Loading — самый первый внутренний переход сразу после
                // того, как NavHost уже задвинул весь экран слайдом (см. тот же
                // комментарий в ScheduleScreen.kt) — без этого байпаса анимация
                // "двоится" в первые ~280мс после открытия файла.
                val isInitialLoad = from is TeacherUiState.Idle

                if (isSkeletonToPicker || isInitialLoad) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else if (goingBack) {
                    (slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec  = tween(TEACHER_SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(TEACHER_SUBSCREEN_ANIM_MS - 60))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(TEACHER_SUBSCREEN_ANIM_MS, easing = FastOutSlowInEasing),
                        ) + fadeOut(tween(TEACHER_SUBSCREEN_ANIM_MS - 60)))
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
            label = "teacherSubscreen",
        ) { state ->
            when (state) {
                is TeacherUiState.Idle -> TeacherSchedLoading()

                is TeacherUiState.Loading -> when (state.stage) {
                    LoadingStage.FILE     -> TeacherPickerLoading(entranceTrigger = transitionSeq)
                    LoadingStage.SCHEDULE -> TeacherSchedLoading()
                }

                is TeacherUiState.TeacherPicker -> TeacherPickerScreen(
                    teachers        = state.teachers,
                    onSelect        = { teacher -> goingBack = false; vm.selectTeacher(teacher, file.name) },
                    entranceTrigger = transitionSeq,
                    // Вперёд — карточки поднимаются снизу с fade (BOTTOM), назад —
                    // едут слева (LEFT), revealEdge — раскрыт тумблером без
                    // перезагрузки. См. аналогичную логику в GroupPickerScreen.
                    entranceEdge    = pickerRevealEdgeOverride
                        ?: if (goingBack) CascadeEdge.LEFT else CascadeEdge.BOTTOM,
                )

                is TeacherUiState.Success -> TeacherSchedContent(
                    day             = state.day,
                    clockMin        = clockMin,
                    entranceTrigger = transitionSeq,
                )

                is TeacherUiState.Error -> TeacherSchedError(
                    message = state.message,
                    onRetry = { goingBack = false; vm.load(file) },
                )
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
) {
    val c = LocalAppColors.current
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

    if (teachers.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TeacherPickerHint(count = 0)
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "В этом файле не удалось распознать ни одного преподавателя",
                    color = c.textSub,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TeacherPickerHint(count = teachers.size)

        // Как и в GroupPickerScreen/FilesList: короткий список (1-3 преподавателя)
        // центрируем по вертикали вместо прилипания к верху.
        val isShort = teachers.size <= 3

        val listState = rememberLazyListState()
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
            itemsIndexed(teachers, key = { _, t -> "t_$t" }) { idx, teacher ->
                val mount = scrollCascade.resolve("t_$teacher", idx, entranceEdge)
                CascadeEntranceItem(
                    index      = mount.index,
                    triggerKey = entranceTrigger,
                    enabled    = entranceEnabled,
                    edge       = mount.edge,
                ) {
                    TeacherCard(name = teacher) { onSelect(teacher) }
                }
            }
        }
    }
}

@Composable
private fun TeacherPickerHint(count: Int) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        // Заголовок "Выберите преподавателя" уже есть в шапке экрана
        // (TeacherHeader) — дублировать его тут не нужно, как и в
        // GroupPickerScreen (там тоже только строка с количеством).
        Text(
            text = "Найдено $count преподавателей за этот день",
            color = c.textSub,
            fontSize = 11.5.sp,
        )
    }
}

// ─── Карточка преподавателя (стиль GroupCard/FileCard) ────────────────────────

@Composable
private fun TeacherCard(
    name: String,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Outlined.Person,
                contentDescription = null,
                tint               = c.textSub,
                modifier           = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text       = name,
            color      = c.text,
            fontSize   = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.weight(1f),
        )

        Icon(
            imageVector        = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint               = c.textSub,
            modifier           = Modifier.size(18.dp),
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
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
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
    val c = LocalAppColors.current

    val leftColor = when {
        status.isNow  -> c.todayAccent
        status.isNext -> Color(0xFF50C878)
        else          -> c.accent
    }
    val bgColor = if (status.isNow) c.todayAccent.copy(alpha = 0.10f) else c.surface2
    val borderColor = when {
        status.isNow  -> c.todayAccent.copy(alpha = 0.30f)
        status.isNext -> Color(0xFF50C878).copy(alpha = 0.30f)
        else          -> c.surface3
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 9.dp),
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

                        // Группа — главная информация в расписании преподавателя
                        Text(
                            text = lesson.group,
                            color = c.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.03.sp,
                        )
                        Text(
                            text = lesson.subject,
                            color = c.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(top = 1.dp),
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
                        TeacherTimeRow(time = "${lesson.timeStart}–${lesson.timeEnd}", tag = "ПАРА", muted = false)
                    }
                    if (lesson.breakStart != null && lesson.breakEnd != null) {
                        TeacherTimeRow(time = "${lesson.breakStart}–${lesson.breakEnd}", tag = "ПЕРЕМ", muted = true)
                    }
                }

                if (lesson.room != null) {
                    Text(
                        text = lesson.room,
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

@Composable
private fun TeacherTimeRow(time: String, tag: String, muted: Boolean) {
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
                .background(c.surface3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tag,
                color = c.textSub,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.07.sp,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

// ─── Скелетон загрузки ────────────────────────────────────────────────────────

@Composable
private fun TeacherSchedLoading() {
    val c = LocalAppColors.current
    val alpha by rememberInfiniteTransition(label = "teacherContentSkel")
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
                    Box(Modifier.size(width = 100.dp, height = 11.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2.copy(alpha = a)))
                    Box(Modifier.size(width = 130.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2.copy(alpha = a)))
                }
            }
        }
    }
}

// ─── Ошибка ───────────────────────────────────────────────────────────────────

@Composable
private fun TeacherSchedError(message: String, onRetry: () -> Unit) {
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
