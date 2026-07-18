package com.schedule.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.FlipTransitionText
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.components.ScheduleModeToggle
import com.schedule.app.ui.theme.LocalAppColors

// ─── ScheduleHeaderInfo ─────────────────────────────────────────────────────
//
// Раньше каждый под-экран (ScheduleScreen/TeacherScheduleScreen) сам рисовал
// у себя в шапке заголовок, дату, кнопку назад и полосу загрузки (см. старые
// SchedHeader/TeacherHeader) — из-за этого при переключении тумблером
// съезжала ВСЯ шапка целиком вместе с содержимым, что и выглядело странно.
// Теперь каждый под-экран только ВЫЧИСЛЯЕТ эти данные и поднимает их сюда
// через onHeaderInfo, а рисует шапку ОДИН раз сам ScheduleHostScreen — при
// переключении режима она не пересоздаётся и не двигается, меняется только
// сам текст заголовка (см. FlipTransitionText в теле ScheduleHostScreen).
data class ScheduleHeaderInfo(
    val title: String = "",
    val placeholder: String = "",
    val dateText: String = "",
    val isPairsScreen: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val filledFontSize: TextUnit = 22.sp,
    val onBack: () -> Unit = {},
)

// ─── ScheduleHostScreen ─────────────────────────────────────────────────────
//
// Экран, открывающийся по тапу на файл. Оба вида (ScheduleScreen и
// TeacherScheduleScreen) монтируются СРАЗУ и ОБА — у каждого свой ViewModel
// и своё независимое состояние (выбранная группа / преподаватель не
// сбрасываются друг от друга при переключении).
//
// Раньше переключение тумблером сдвигало оба вида по X (offset-слайд, как у
// Files/Bells в AppScaffold) — визуально это читалось как "переход на другой
// экран", хотя по смыслу это просто переключатель РЕЖИМА одного и того же
// экрана. Поэтому слайд убран: неактивный вид теперь просто становится
// невидимым (alpha 0) и перестаёт ловить тапы, БЕЗ какой-либо анимации
// самого переключения. "Едут" только элементы ВНУТРИ активного вида —
// карточки пикера группы/преподавателя всё так же анимированно "влетают"
// (см. revealTrigger/revealEdge, эта часть не менялась).
//
// Шапка, тумблер и полоса загрузки теперь тоже не дублируются на два экрана —
// единственный экземпляр каждого живёт здесь и берёт данные из headerInfo
// активного в данный момент режима.
@Composable
fun ScheduleHostScreen(file: ScheduleFile, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val defaultMode by AppPrefs.defaultScheduleMode.collectAsState()

    // Стартовый режим берём из настроек ровно один раз при открытии ЭТОГО
    // файла (rememberSaveable(file.name) пересоздаст состояние для другого
    // файла) — дальнейшие переключения тумблером внутри одного открытия не
    // должны "прыгать" обратно на дефолт при рекомпозициях.
    var mode by rememberSaveable(file.name) { mutableStateOf(defaultMode) }

    // Каскадная анимация карточек пикера при переключении тумблером — тот же
    // приём, что и раньше, просто по-прежнему управляет карточками
    // группы/преподавателя внутри активного вида.
    var toggleTrigger by remember { mutableStateOf(0) }
    var toggleEdge by remember { mutableStateOf(CascadeEdge.LEFT) }

    val onModeSelect: (ScheduleMode) -> Unit = { newMode ->
        if (newMode != mode) {
            toggleEdge = if (newMode == ScheduleMode.TEACHER) CascadeEdge.RIGHT else CascadeEdge.LEFT
            toggleTrigger++
        }
        mode = newMode
    }

    // Состояние шапки для каждого из двух видов — оба смонтированы всегда и
    // независимо сообщают о себе, а рисуется только то, что относится к
    // активному в данный момент mode.
    var studentHeader by remember { mutableStateOf(ScheduleHeaderInfo(placeholder = "Выберите группу")) }
    var teacherHeader by remember { mutableStateOf(ScheduleHeaderInfo(placeholder = "Выберите преподавателя")) }
    val activeHeader = if (mode == ScheduleMode.STUDENT) studentHeader else teacherHeader

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {

        // ── Единая фиксированная шапка ──────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth().background(c.surface)) {
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
                        .clickable(onClick = activeHeader.onBack),
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
                    val displayTitle = activeHeader.title.ifBlank { activeHeader.placeholder }
                    FlipTransitionText(
                        text     = displayTitle,
                        color    = if (activeHeader.title.isBlank()) c.textSub else c.accent,
                        fontSize = if (activeHeader.title.isBlank()) 16.sp else activeHeader.filledFontSize,
                    )
                    Text(
                        text = activeHeader.dateText,
                        color = c.textSub,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.border),
            )
        }

        // ── Полоса загрузки — теперь НАД тумблером (раньше была под ним) ────
        if (activeHeader.isLoading) {
            LinearProgressIndicator(
                progress   = { activeHeader.progress },
                modifier   = Modifier.fillMaxWidth().height(2.dp),
                color      = c.accent,
                trackColor = c.surface2,
            )
        }

        // ── Тумблер "Ученики/Преподаватели" — один фиксированный экземпляр,
        // виден только пока в активном режиме не показано само расписание пар.
        if (!activeHeader.isPairsScreen) {
            Spacer(Modifier.height(10.dp))
            ScheduleModeToggle(
                selected = mode,
                onSelect = onModeSelect,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(4.dp))
        }

        // ── Содержимое: оба вида смонтированы всегда, неактивный просто
        // невидим и не ловит тапы — БЕЗ анимации переключения между ними.
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            val studentActive = mode == ScheduleMode.STUDENT

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (studentActive) 1f else 0f }
                    .blockTouchesIfInactive(!studentActive),
            ) {
                ScheduleScreen(
                    file          = file,
                    onBack        = onBack,
                    active        = studentActive,
                    revealTrigger = toggleTrigger,
                    revealEdge    = toggleEdge,
                    onHeaderInfo  = { studentHeader = it },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (!studentActive) 1f else 0f }
                    .blockTouchesIfInactive(studentActive),
            ) {
                TeacherScheduleScreen(
                    file          = file,
                    onBack        = onBack,
                    active        = !studentActive,
                    revealTrigger = toggleTrigger,
                    revealEdge    = toggleEdge,
                    onHeaderInfo  = { teacherHeader = it },
                )
            }
        }
    }
}

// Блокирует все тач-события для невидимого (неактивного) вида — иначе он,
// будучи смонтированным и занимающим весь экран под активным, мог бы первым
// перехватывать часть тапов, несмотря на alpha = 0.
private fun Modifier.blockTouchesIfInactive(inactive: Boolean): Modifier =
    if (inactive) {
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else this
