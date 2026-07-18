package com.schedule.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.LocalAppColors
import kotlin.math.roundToInt

// ─── Плавающий пузырёк навигации ─────────────────────────────────────────────
//
//  Выделение СКОЛЬЗИТ между вкладками (iOS-стиль): один Box-индикатор под
//  элементами перемещается к активному пункту. Сами элементы не имеют своего
//  фона — только иконка и текст поверх общего индикатора.
//
//  ВАЖНО (переделано): раньше ширина/позиция вкладок мерялись "живьём" через
//  onGloballyPositioned прямо на анимирующемся Row — из-за этого позиция
//  соседнего элемента зависела от ТЕКУЩЕЙ (ещё анимирующейся) ширины другого
//  элемента, и после того как всё визуально уже "осело", могла прилететь ещё
//  одна отложенная правка на несколько px → рывок.
//
//  Теперь ширины вкладок в двух состояниях — "компакт" (только иконка) и
//  "полная" (иконка + подпись) — меряются ОДИН РАЗ заранее, невидимым Layout
//  ниже, и никогда не зависят от того, что в данный момент анимирует соседняя
//  вкладка. Позиция/ширина индикатора считаются из этих готовых чисел.
//
//  Пружина индикатора пока НЕ включена обратно (снап мгновенный) — это
//  временно, для изоляции причины рывка. Возвращать её будем отдельным шагом.

private data class PillNavItem(
    val route: String,
    val icon:  ImageVector,
    val label: String,
)

@Composable
fun FloatingPillNav(
    currentRoute: String,
    onNavigate:   (String) -> Unit,
    modifier:     Modifier = Modifier,
) {
    val c       = LocalAppColors.current
    val density = LocalDensity.current

    val items = remember {
        listOf(
            PillNavItem(Screen.Files.route, Icons.Outlined.CalendarMonth,   "Расписание"),
            PillNavItem(Screen.Bells.route, Icons.Outlined.NotificationsNone, "Звонки"),
        )
    }

    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    // ─── Настройка внутреннего отступа для выделения ────────────────────────
    val extraPaddingDp = 12.dp
    val extraPaddingPx = with(density) { extraPaddingDp.toPx() }
    val spacingDp       = 4.dp
    val spacingPx        = with(density) { spacingDp.toPx() }

    // ── Стабильные ширины вкладок (px), измеренные один раз ниже ─────────────
    // compactWidths — ширина "иконка без подписи", fullWidths — "иконка + подпись".
    // Эти числа НЕ меняются во время анимации — только когда меняется набор items.
    var compactWidths by remember { mutableStateOf(List(items.size) { 0f }) }
    var fullWidths    by remember { mutableStateOf(List(items.size) { 0f }) }

    // ── Невидимый измеритель ──────────────────────────────────────────────
    // Рисует те же Row (иконка / иконка+текст), что и реальные вкладки ниже,
    // но с нулевым итоговым размером — на экране не видно, нужен только для
    // получения стабильной intrinsic-ширины через layout(0, 0).
    Layout(
        content = {
            items.forEach { item ->
                Row(
                    modifier               = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            items.forEach { item ->
                Row(
                    modifier               = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
    ) { measurables, _ ->
        // Настоящие безграничные constraints — НЕ наследуем maxWidth от родителя,
        // иначе измерение зависит от того, что в моменте творится с шириной внешнего
        // Box (а она сама может плавать), и результат становится нестабильным.
        val unbounded  = Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(unbounded) }
        val n          = items.size
        val newCompact = placeables.take(n).map { it.width.toFloat() }
        val newFull    = placeables.drop(n).map { it.width.toFloat() }
        if (compactWidths != newCompact) compactWidths = newCompact
        if (fullWidths    != newFull)    fullWidths    = newFull
        layout(0, 0) {} // ничего не рисуем — только измерение
    }

    val hasSizes = fullWidths.isNotEmpty() && fullWidths.all { it > 0f } && compactWidths.all { it > 0f }

    // ── Ширина каждой вкладки ПРЯМО СЕЙЧАС: полная у активной, компакт у остальных ──
    val itemWidthsPx = items.indices.map { idx ->
        if (idx == selectedIndex) fullWidths.getOrElse(idx) { 0f } else compactWidths.getOrElse(idx) { 0f }
    }

    // ── X-позиции вкладок считаем сами (не из live layout) — просто накопительная сумма ──
    val itemXsPx = remember(itemWidthsPx) {
        val xs = mutableListOf<Float>()
        var acc = 0f
        itemWidthsPx.forEach { w ->
            xs.add(acc)
            acc += w + spacingPx
        }
        xs
    }

    // Сдвигаем позицию влево на величину отступа, чтобы синий фон начинался раньше контента
    val targetXPx = (itemXsPx.getOrElse(selectedIndex) { 0f } - extraPaddingPx).coerceAtLeast(0f)
    // Увеличиваем ширину на x2 отступа (чтобы компенсировать левый и правый «воздух»)
    val targetWPx = itemWidthsPx.getOrElse(selectedIndex) { 0f } + (extraPaddingPx * 2)

    // ВРЕМЕННО без пружины — мгновенный снап на target (следующий шаг — вернуть анимацию)
    val indicatorXPx = targetXPx
    val indicatorWPx = targetWPx

    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)          // высота = высота Row-контента
            .clip(CircleShape)
            .background(c.pillBg)
            .border(1.dp, c.border, CircleShape)
            .padding(5.dp),
    ) {

        // ── Скользящий индикатор (слой ПОД элементами) ───────────────────────
        if (hasSizes) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorXPx.roundToInt(), 0) }
                    .fillMaxHeight()
                    .width(with(density) { indicatorWPx.toDp() })
                    .clip(CircleShape)
                    .background(c.pillActive),
            )
        }

        // ── Навигационные элементы (без своего фона) ─────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacingDp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { idx, item ->
                val isActive = currentRoute == item.route

                val contentColor by animateColorAsState(
                    targetValue   = if (isActive) c.pillActiveText else c.pillInactiveText,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label         = "pillContent$idx",
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null, // у индикатора своя анимация — серая вспышка тут лишняя
                        ) { onNavigate(item.route) }
                        .padding(
                            horizontal = 12.dp,
                            vertical   = 9.dp,
                        ),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector        = item.icon,
                        contentDescription = item.label,
                        tint               = contentColor,
                        modifier           = Modifier.size(18.dp),
                    )
                    AnimatedVisibility(
                        visible = isActive,
                        enter = expandHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            expandFrom = Alignment.Start,
                        ) + fadeIn(),
                        exit = shrinkHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            shrinkTowards = Alignment.Start,
                        ) + fadeOut(),
                    ) {
                        Text(
                            text       = item.label,
                            color      = contentColor,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
