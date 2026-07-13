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
import com.schedule.app.ui.theme.LocalAppColors

// ─── Скелетон для пикера преподавателя — визуально идентичен TeacherPickerScreen ─

@Composable
private fun TeacherPickerLoading() {
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
    // подробности см. в комментарии там.
    val entranceKey     = remember { Any() }
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
                    triggerKey = entranceKey,
                    enabled    = entranceEnabled,
                    edge       = CascadeEdge.RIGHT,
                ) {
                    val a = (alpha - i * 0.06f).coerceIn(0.3f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface.copy(alpha = a))
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
    vm: TeacherScheduleViewModel = viewModel(),
) {
    val c            = LocalAppColors.current
    val uiState      by vm.uiState.collectAsState()
    val progress     by vm.progress.collectAsState()
    val teacherName  by vm.teacherName.collectAsState()
    val clockMin     by vm.clockMin.collectAsState()

    LaunchedEffect(file.name) { vm.load(file) }

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
        TeacherHeader(
            teacherName     = headerTeacherName,
            dateText        = file.dateLabel,
            onBack          = onBack,
            onChangeTeacher = if (headerTeacherName.isNotBlank()) { { vm.clearTeacher() } } else null,
        )

        if (uiState is TeacherUiState.Loading) {
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.fillMaxWidth().height(2.dp),
                color      = c.accent,
                trackColor = c.surface2,
            )
        }

        when (val state = uiState) {
            is TeacherUiState.Idle -> TeacherSchedLoading()

            is TeacherUiState.Loading -> when (state.stage) {
                LoadingStage.FILE     -> TeacherPickerLoading()
                LoadingStage.SCHEDULE -> TeacherSchedLoading()
            }

            is TeacherUiState.TeacherPicker -> TeacherPickerScreen(
                teachers = state.teachers,
                onSelect = { teacher -> vm.selectTeacher(teacher, file.name) },
            )

            is TeacherUiState.Success -> TeacherSchedContent(day = state.day, clockMin = clockMin)

            is TeacherUiState.Error -> TeacherSchedError(
                message = state.message,
                onRetry = { vm.load(file) },
            )
        }
    }
}

// ─── Шапка ────────────────────────────────────────────────────────────────────

@Composable
private fun TeacherHeader(
    teacherName: String,
    dateText: String,
    onBack: () -> Unit,
    onChangeTeacher: (() -> Unit)? = null,
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (teacherName.isBlank()) "Выберите преподавателя" else teacherName,
                    color = if (teacherName.isBlank()) c.textSub else c.accent,
                    fontSize = if (teacherName.isBlank()) 16.sp else 20.sp,
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

            if (onChangeTeacher != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.surface2)
                        .clickable(onClick = onChangeTeacher),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Сменить преподавателя",
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

// ─── Пикер преподавателя ───────────────────────────────────────────────────────

@Composable
private fun TeacherPickerScreen(
    teachers: List<String>,
    onSelect: (String) -> Unit,
) {
    val c = LocalAppColors.current

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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                bottom = 80.dp, top = 2.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(teachers, key = { "t_$it" }) { teacher ->
                TeacherCard(name = teacher) { onSelect(teacher) }
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
        Text(
            text = "Выберите преподавателя",
            color = c.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Найдено $count преподавателей за этот день",
            color = c.textSub,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 3.dp),
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
private fun TeacherSchedContent(day: TeacherDay, clockMin: Int) {
    val c = LocalAppColors.current

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

        items(day.lessons.size) { idx ->
            TeacherPairCard(
                lesson = day.lessons[idx],
                status = liveStatuses.getOrNull(idx) ?: TeacherLessonStatus(),
            )
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
