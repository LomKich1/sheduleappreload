package com.schedule.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.LocalAppColors

// ─── Режим главного экрана: "я ученик" / "я преподаватель" ──────────────────
// Пока это чисто визуальное переключение (см. FilesScreen) — какой конкретно
// экран открывается по тапу на файл в зависимости от режима, подключим
// отдельным шагом.

enum class ScheduleMode {
    STUDENT,
    TEACHER,
}

// ─── ScheduleModeToggle ───────────────────────────────────────────────────────
//
// Визуально — как FloatingPillNav (капсула, скользящий индикатор, spring-
// анимация), НО есть два принципиальных отличия под эту задачу:
//  1. Без иконок — только текст.
//  2. Текст виден ВСЕГДА у обеих вкладок (в FloatingPillNav неактивная вкладка
//     прячет подпись и остаётся только иконкой — здесь так нельзя, иконок нет).
// Поэтому вместо измерения ширины каждого элемента через onGloballyPositioned
// (как в FloatingPillNav, где ширины разные из-за скрывающегося текста) тут
// используется более простое деление 50/50 через BoxWithConstraints — ширины
// сегментов одинаковые и заранее известны.

@Composable
fun ScheduleModeToggle(
    selected: ScheduleMode,
    onSelect: (ScheduleMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(c.pillBg)
            .border(1.dp, c.border, RoundedCornerShape(23.dp))
            .padding(4.dp),
    ) {
        val halfWidth = maxWidth / 2

        val indicatorOffset by animateDpAsState(
            targetValue   = if (selected == ScheduleMode.STUDENT) 0.dp else halfWidth,
            animationSpec = spring(
                stiffness    = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            ),
            label = "modeIndicator",
        )

        // Скользящий индикатор — слой ПОД текстом, как синяя пилюля в FloatingPillNav.
        // offset(), а НЕ padding(): у пружины dampingRatio = MediumBouncy есть
        // небольшой перелёт за целевую точку, из-за которого indicatorOffset может
        // на мгновение уйти чуть ниже 0.dp. padding() на отрицательное значение
        // падает с IllegalArgumentException, offset() — спокойно его допускает.
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(halfWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(19.dp))
                .background(c.pillActive),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            ModeSegment(
                text     = "Ученики",
                isActive = selected == ScheduleMode.STUDENT,
                modifier = Modifier.weight(1f),
                onClick  = { onSelect(ScheduleMode.STUDENT) },
            )
            ModeSegment(
                text     = "Преподаватели",
                isActive = selected == ScheduleMode.TEACHER,
                modifier = Modifier.weight(1f),
                onClick  = { onSelect(ScheduleMode.TEACHER) },
            )
        }
    }
}

@Composable
private fun ModeSegment(
    text: String,
    isActive: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val textColor by animateColorAsState(
        targetValue   = if (isActive) c.pillActiveText else c.pillInactiveText,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "modeSegmentText",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(19.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null, // у индикатора своя анимация — своя вспышка тут лишняя
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = text,
            color      = textColor,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
