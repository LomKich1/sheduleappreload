package com.schedule.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.LocalAppColors
import kotlin.math.roundToInt

// ─── Плавающий пузырёк навигации ─────────────────────────────────────────────
//
//  Выделение СКОЛЬЗИТ между вкладками (iOS-стиль):
//  один Box-индикатор под элементами плавно перемещается к активному пункту
//  через animateFloatAsState + spring.  Сами элементы не имеют своего фона —
//  только иконка и текст поверх общего индикатора.
//
//  ВАЖНО про источник размеров индикатора:
//  Раньше ширина/позиция активного пункта бралась из onGloballyPositioned
//  живого Row — а этот Row сам анимируется (подпись плавно
//  сжимается/расширяется через AnimatedVisibility). В результате пружина
//  пилюли каждый кадр гналась за постоянно меняющейся целью, и в момент,
//  когда анимация текста наконец останавливалась, пилюля ещё не успевала
//  «догнать» — отсюда рывок в конце.
//
//  Исправление: ширины пунктов больше не измеряются из живого layout'а,
//  а считаются один раз заранее — через TextMeasurer (размер текста) +
//  известные размеры иконки/паддингов/отступов. Это и есть «конечное»
//  значение, к которому пилюля должна стремиться с самого начала анимации,
//  а не то, что дёргается по ходу дела.

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

    // ── Стабильные (конечные) ширины пунктов ──────────────────────────────────
    // Считаем один раз через измерение текста, а не через onGloballyPositioned
    // живого Row — см. комментарий вверху файла про причину рывка.
    val textMeasurer = rememberTextMeasurer()

    val iconSizePx           = with(density) { 18.dp.toPx() }
    val iconTextSpacingPx    = with(density) { 6.dp.toPx() }   // Arrangement.spacedBy(6.dp) внутри пункта
    val itemHorizontalPadPx  = with(density) { 12.dp.toPx() * 2 } // horizontal-паддинг пункта, с двух сторон
    val rowSpacingPx         = with(density) { 4.dp.toPx() }   // Arrangement.spacedBy(4.dp) между пунктами

    // Ширина пункта в РАЗВЁРНУТОМ состоянии (иконка + отступ + текст + паддинги).
    // Пересчитывается только если поменялся список пунктов (шрифт/тексты фиксированы).
    val itemExpandedWidthsPx = remember(items) {
        items.map { item ->
            val textWidthPx = textMeasurer.measure(
                text  = item.label,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            ).size.width.toFloat()
            iconSizePx + iconTextSpacingPx + textWidthPx + itemHorizontalPadPx
        }
    }
    // Ширина пункта в СВЁРНУТОМ состоянии (только иконка + паддинги, текста нет вообще —
    // AnimatedVisibility после exit полностью убирает Text из композиции).
    val itemCollapsedWidthPx = iconSizePx + itemHorizontalPadPx

    fun stableWidthOf(idx: Int) =
        if (idx == selectedIndex) itemExpandedWidthsPx[idx] else itemCollapsedWidthPx

    // Позиции считаем суммированием стабильных ширин — тоже без обращения
    // к живому layout'у, поэтому не зависят от текущего кадра анимации.
    val itemXsPx = remember(selectedIndex, itemExpandedWidthsPx) {
        val xs = FloatArray(items.size)
        var acc = 0f
        for (i in items.indices) {
            xs[i] = acc
            acc += stableWidthOf(i) + rowSpacingPx
        }
        xs.toList()
    }

    // ─── Настройка внутреннего отступа для выделения ────────────────────────
    // Сколько «воздуха» добавить слева и справа от контента внутри синей пилюли
    val extraPaddingDp = 12.dp 
    val extraPaddingPx = with(density) { extraPaddingDp.toPx() }

    // ── Анимация индикатора — spring с лёгким overshoot для «живости» ─────────
    // Это тот самый отскок при переключении вкладок — его специально просили
    // оставить как было. Без отскока сделана только анимация подписи ниже
    // (AnimatedVisibility) — раньше она резко исчезала/появлялась через
    // обычный if, теперь плавно сжимается, но уже без пружинного "перелёта".
    val springSpec = spring<Float>(
        stiffness    = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioMediumBouncy,
    )
    
    // Сдвигаем позицию влево на величину отступа, чтобы синий фон начинался раньше контента
    val targetXPx = (itemXsPx.getOrElse(selectedIndex) { 0f } - extraPaddingPx).coerceAtLeast(0f)
    val indicatorXPx by animateFloatAsState(
        targetValue   = targetXPx,
        animationSpec = springSpec,
        label         = "pillX",
    )
    
    // Увеличиваем ширину на x2 отступа (чтобы компенсировать левый и правый «воздух»)
    val targetWPx = stableWidthOf(selectedIndex) + (extraPaddingPx * 2)
    val indicatorWPx by animateFloatAsState(
        targetValue   = targetWPx,
        animationSpec = springSpec,
        label         = "pillW",
    )

    // Размеры теперь известны сразу (посчитаны, а не измерены после layout'а),
    // поэтому индикатор можно показывать с первого кадра — задержка не нужна.
    val hasPositions = true

    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)          // высота = высота Row-контента
            .clip(CircleShape)
            .background(c.pillBg)
            .border(1.dp, c.border, CircleShape)
            .padding(5.dp),
    ) {

        // ── Скользящий индикатор (слой ПОД элементами) ───────────────────────
        if (hasPositions) {
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
