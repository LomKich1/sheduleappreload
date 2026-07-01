package com.schedule.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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

    // ── Измеренные позиции элементов (обновляются после каждого layout) ───────
    // Хранятся в пикселях — тот же масштаб, что у offset { IntOffset(...) }.
    val itemXsPx = remember { mutableStateListOf(0f, 0f) }
    val itemWsPx = remember { mutableStateListOf(0f, 0f) }

    // ── Анимация индикатора — spring с лёгким overshoot для «живости» ─────────
    val springSpec = spring<Float>(
        stiffness    = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioMediumBouncy,   // небольшой отскок
    )
    val indicatorXPx by animateFloatAsState(
        targetValue   = itemXsPx.getOrElse(selectedIndex) { 0f },
        animationSpec = springSpec,
        label         = "pillX",
    )
    val indicatorWPx by animateFloatAsState(
        targetValue   = itemWsPx.getOrElse(selectedIndex) { 0f },
        animationSpec = springSpec,
        label         = "pillW",
    )

    // Показываем индикатор только когда уже есть реальные размеры (≥ 2-й кадр)
    val hasPositions = itemWsPx.any { it > 0f }

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
                            horizontal = if (isActive) 40.dp else 12.dp,
                            vertical   = 9.dp,
                        )
                        .onGloballyPositioned { coords ->
                            // positionInParent() — координаты в родительском Row.
                            // Row напрямую в Box без смещений → == координаты в Box.
                            itemXsPx[idx] = coords.positionInParent().x
                            itemWsPx[idx] = coords.size.width.toFloat()
                        },
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector        = item.icon,
                        contentDescription = item.label,
                        tint               = contentColor,
                        modifier           = Modifier.size(18.dp),
                    )
                    if (isActive) {
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
