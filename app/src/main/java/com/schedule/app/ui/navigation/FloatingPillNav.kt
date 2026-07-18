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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
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
//  Позиции измеряются через onGloballyPositioned (срабатывает после layout),
//  поэтому индикатор показывается начиная со второго кадра — мерцания нет,
//  т.к. первый кадр элементы рисуют с правильными цветами (animateColorAsState).

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

    // ── Стабильные ширины пунктов, измеренные ЗАРАНЕЕ и НЕВИДИМО ──────────────
    // Раньше indicatorWPx/indicatorXPx гнались пружиной за itemWsPx/itemXsPx,
    // которые обновлялись через onGloballyPositioned КАЖДЫЙ кадр — то есть
    // прямо во время того, как подпись (AnimatedVisibility ниже) сама
    // расширялась/сужалась. Индикатор в итоге догонял постоянно ДВИГАЮЩУЮСЯ
    // цель, а когда подпись останавливалась, у пружины индикатора ещё
    // оставалась "недокрученная" скорость — отсюда и рывок под конец.
    //
    // Вместо этого меряем ОБА состояния каждого пункта (с подписью и без) —
    // ОДИН раз, невидимо (см. MeasureOnly ниже) — и дальше у индикатора всегда
    // фиксированная, не меняющаяся на лету цель, к которой можно спокойно
    // подъехать пружиной без "недолёта".
    val collapsedWPx = remember { mutableStateListOf(0f, 0f) }
    val expandedWPx  = remember { mutableStateListOf(0f, 0f) }

    items.forEachIndexed { idx, item ->
        MeasureOnly(onMeasured = { w -> collapsedWPx[idx] = w }) {
            PillItemMeasureContent(item = item, showLabel = false)
        }
        MeasureOnly(onMeasured = { w -> expandedWPx[idx] = w }) {
            PillItemMeasureContent(item = item, showLabel = true)
        }
    }

    // ─── Настройка внутреннего отступа для выделения ────────────────────────
    // Сколько «воздуха» добавить слева и справа от контента внутри синей пилюли
    val extraPaddingDp = 12.dp 
    val extraPaddingPx = with(density) { extraPaddingDp.toPx() }
    val spacingPx = with(density) { 4.dp.toPx() } // Arrangement.spacedBy(4.dp) у Row ниже

    // ── Анимация индикатора — spring с лёгким overshoot для «живости» ─────────
    // Это тот самый отскок при переключении вкладок — его специально просили
    // оставить как было. Без отскока сделана только анимация подписи ниже
    // (AnimatedVisibility) — раньше она резко исчезала/появлялась через
    // обычный if, теперь плавно сжимается, но уже без пружинного "перелёта".
    val springSpec = spring<Float>(
        stiffness    = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioMediumBouncy,
    )

    // Ширина пункта idx В ЦЕЛЕВОМ состоянии: развёрнутая — только у выбранного
    // пункта, у остальных — свёрнутая. Именно эта комбинация будет актуальна,
    // когда анимация подписи полностью доиграет.
    fun settledWidthPx(idx: Int): Float =
        if (idx == selectedIndex) expandedWPx[idx] else collapsedWPx[idx]

    // X-позиция пункта idx в его целевом состоянии — сумма ширин всех пунктов
    // ДО него (в их целевом состоянии) плюс отступы между ними. Пунктов всего
    // два, но формула работает для любого их числа.
    val targetItemXPx = (0 until selectedIndex).sumOf { settledWidthPx(it).toDouble() }.toFloat() +
        selectedIndex * spacingPx

    // Сдвигаем позицию влево на величину отступа, чтобы синий фон начинался раньше контента
    val targetXPx = (targetItemXPx - extraPaddingPx).coerceAtLeast(0f)
    val indicatorXPx by animateFloatAsState(
        targetValue   = targetXPx,
        animationSpec = springSpec,
        label         = "pillX",
    )
    
    // Увеличиваем ширину на x2 отступа (чтобы компенсировать левый и правый «воздух»)
    val targetWPx = settledWidthPx(selectedIndex) + (extraPaddingPx * 2)
    val indicatorWPx by animateFloatAsState(
        targetValue   = targetWPx,
        animationSpec = springSpec,
        label         = "pillW",
    )

    // Показываем индикатор только когда уже есть реальные размеры (≥ 2-й кадр)
    val hasPositions = collapsedWPx.all { it > 0f } && expandedWPx.all { it > 0f }

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

// ─── MeasureOnly ─────────────────────────────────────────────────────────────
//
// Меряет естественную ширину content — но не рисует его и не занимает места
// в родителе (place() для ребёнка ни разу не вызывается, поэтому фаза
// отрисовки для него просто не запускается). Нужен, чтобы заранее и
// стабильно узнать ширину пункта нижней навигации в обоих состояниях
// (с подписью и без), не завязываясь на живую, меняющуюся во время анимации
// подписи разметку (см. collapsedWPx/expandedWPx выше).
@Composable
private fun MeasureOnly(
    onMeasured: (widthPx: Float) -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, _ ->
        val placeable = measurables.first().measure(Constraints())
        onMeasured(placeable.width.toFloat())
        layout(0, 0) {}
    }
}

// Точная копия визуальной структуры пункта (иконка + опциональная подпись,
// те же паддинги/отступы) — используется ТОЛЬКО для невидимого измерения
// внутри MeasureOnly, поэтому цвет не важен: этот Composable никогда
// реально не отрисовывается на экране.
@Composable
private fun PillItemMeasureContent(item: PillNavItem, showLabel: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        if (showLabel) {
            Text(
                text = item.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
