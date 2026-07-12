package com.schedule.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
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
import com.schedule.app.ui.theme.LocalAppColors

// ─── Скелетон для пикера группы — визуально идентичен GroupPickerScreen ───────

@Composable
private fun GroupPickerLoading() {
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Тот же заголовок, что и в реальном пикере — просто без счётчика
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Выберите вашу группу",
                color = c.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Загружаем список групп…",
                color = c.textSub,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Та же секция "ВСЕ ГРУППЫ", что и в реальном списке (GroupPickerScreen).
            // Раньше её тут не было — карточки скелетона стояли примерно на 18dp
            // выше настоящих, и при появлении списка всё заметно "прыгало" вниз.
            GroupSectionLabel("ВСЕ ГРУППЫ")

            repeat(8) { i ->
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

// ─── ScheduleScreen ───────────────────────────────────────────────────────────

@Composable
fun ScheduleScreen(
    file: ScheduleFile,
    onBack: () -> Unit,
    vm: ScheduleViewModel = viewModel(),
) {
    val c         = LocalAppColors.current
    val uiState   by vm.uiState.collectAsState()
    val progress  by vm.progress.collectAsState()
    val groupName by AppPrefs.groupName.collectAsState()
    val clockMin  by vm.clockMin.collectAsState()

    LaunchedEffect(file.name) { vm.load(file) }

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
        SchedHeader(
            groupName     = headerGroupName,
            dateText      = file.dateLabel,
            onBack        = onBack,
            // Карандаш виден только когда группа уже выбрана и расписание показано
            onChangeGroup = if (headerGroupName.isNotBlank()) { { vm.clearGroup() } } else null,
        )

        if (uiState is ScheduleUiState.Loading) {
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.fillMaxWidth().height(2.dp),
                color      = c.accent,
                trackColor = c.surface2,
            )
        }

        when (val state = uiState) {
            is ScheduleUiState.Idle -> SchedLoading()

            is ScheduleUiState.Loading -> when (state.stage) {
                LoadingStage.FILE     -> GroupPickerLoading()
                LoadingStage.SCHEDULE -> SchedLoading()
            }

            is ScheduleUiState.GroupPicker -> GroupPickerScreen(
                groups   = state.groups,
                onSelect = { group -> vm.selectGroup(group, file.name) },
            )

            is ScheduleUiState.Success     -> SchedContent(day = state.day, clockMin = clockMin)

            is ScheduleUiState.OnPractice  -> SchedOnPractice(headerText = state.headerText)

            is ScheduleUiState.Error       -> SchedError(
                message = state.message,
                onRetry = { vm.load(file) },
            )
        }
    }
}

// ─── Шапка ────────────────────────────────────────────────────────────────────

@Composable
private fun SchedHeader(
    groupName: String,
    dateText: String,
    onBack: () -> Unit,
    onChangeGroup: (() -> Unit)? = null,
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Кнопка назад
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c.surface2)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Назад",
                    tint = c.accent,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Название группы или плейсхолдер
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (groupName.isBlank()) "Выберите группу" else groupName,
                    color = if (groupName.isBlank()) c.textSub else c.accent,
                    fontSize = if (groupName.isBlank()) 16.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                )
                Text(
                    text = dateText,
                    color = c.textSub,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Кнопка «Сменить группу» — карандаш, только если группа задана
            if (onChangeGroup != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.surface2)
                        .clickable(onClick = onChangeGroup),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Сменить группу",
                        tint = c.textSub,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.border),
        )
    }
}

// ─── Пикер группы ─────────────────────────────────────────────────────────────

@Composable
private fun GroupPickerScreen(
    groups: List<String>,
    onSelect: (String) -> Unit,
) {
    val c           = LocalAppColors.current
    val rememberOn  by AppPrefs.rememberGroup.collectAsState()
    val pinnedGroup by AppPrefs.pinnedGroup.collectAsState()

    // Подсвечиваем только если rememberGroup ON + группа реально есть в этом файле
    val pinnedInFile = if (rememberOn && pinnedGroup.isNotBlank() && pinnedGroup in groups)
        pinnedGroup else null
    val otherGroups = if (pinnedInFile != null) groups.filter { it != pinnedInFile } else groups

    Column(modifier = Modifier.fillMaxSize()) {
        // Подсказка
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Выберите вашу группу",
                color = c.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (pinnedInFile != null)
                    "Найдено ${groups.size} групп · запомненная — вверху"
                else
                    "Найдено ${groups.size} групп · выбор сохранится автоматически",
                color = c.textSub,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                bottom = 80.dp, top = 2.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (pinnedInFile != null) {
                item(key = "lbl_pinned") {
                    GroupSectionLabel("ЗАПОМНЕННАЯ")
                }
                item(key = "pinned_$pinnedInFile") {
                    GroupCard(name = pinnedInFile, isPinned = true) { onSelect(pinnedInFile) }
                }
                item(key = "spacer_sep") {
                    Spacer(Modifier.height(6.dp))
                }
                item(key = "lbl_all") {
                    GroupSectionLabel("ВСЕ ГРУППЫ")
                }
                items(otherGroups, key = { "g_$it" }) { group ->
                    GroupCard(name = group, isPinned = false) { onSelect(group) }
                }
            } else {
                item(key = "lbl_all") {
                    GroupSectionLabel("ВСЕ ГРУППЫ")
                }
                items(groups, key = { "g_$it" }) { group ->
                    GroupCard(name = group, isPinned = false) { onSelect(group) }
                }
            }
        }
    }
}

// ─── Секционный заголовок в пикере ────────────────────────────────────────────

@Composable
private fun GroupSectionLabel(text: String) {
    val c = LocalAppColors.current
    Text(
        text           = text,
        color          = c.textSub,
        fontSize       = 10.sp,
        fontWeight     = FontWeight.Bold,
        letterSpacing  = 0.08.sp,
        modifier       = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
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
    val iconBg      = if (isPinned) c.todayAccent.copy(alpha = 0.18f) else c.surface2
    val iconTint    = if (isPinned) c.todayAccent else c.textSub
    val nameColor   = if (isPinned) c.todayAccent else c.text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
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

        if (isPinned) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.todayAccent)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text       = "ЗАПОМНЕНА",
                    color      = c.bg,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Icon(
                imageVector        = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint               = c.textSub,
                modifier           = Modifier.size(18.dp),
            )
        }
    }
}

// ─── Расписание дня ───────────────────────────────────────────────────────────

@Composable
private fun SchedContent(day: ScheduleDay, clockMin: Int) {
    val c = LocalAppColors.current

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

        items(day.lessons, key = { it.num }) { lesson ->
            val idx    = day.lessons.indexOf(lesson)
            val status = liveStatuses.getOrNull(idx) ?: LessonStatus()
            PairCard(lesson = lesson, status = status)
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
                            color = if (lesson.isWindow) c.textSub else c.text,
                            fontSize = 14.sp,
                            fontWeight = if (lesson.isWindow) FontWeight.Normal else FontWeight.Bold,
                            fontStyle = if (lesson.isWindow) FontStyle.Italic else FontStyle.Normal,
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
