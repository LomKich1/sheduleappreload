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
import androidx.compose.ui.draw.alpha
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

    // Измеренные СТАТИЧЕСКИЕ позиции (не меняются во время анимации)
    val itemXsPx = remember { mutableStateListOf(0f, 0f) }
    val itemWsPx = remember { mutableStateListOf(0f, 0f) }

    val extraPaddingDp = 12.dp 
    val extraPaddingPx = with(density) { extraPaddingDp.toPx() }

    // Анимация скольжения индикатора (X) — с приятным отскоком
    val positionSpringSpec = spring<Float>(
        stiffness    = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioMediumBouncy,
    )

    // Анимация изменения ширины (W) — мягкая, без пружинного "перелета", 
    // чтобы пилюля плавно догоняла границы расширяющегося текста
    val widthSpringSpec = spring<Float>(
        stiffness    = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    )
    
    val targetXPx = (itemXsPx.getOrElse(selectedIndex) { 0f } - extraPaddingPx).coerceAtLeast(0f)
    val indicatorXPx by animateFloatAsState(
        targetValue   = targetXPx,
        animationSpec = positionSpringSpec,
        label         = "pillX",
    )
    
    val targetWPx = itemWsPx.getOrElse(selectedIndex) { 0f } + (extraPaddingPx * 2)
    val indicatorWPx by animateFloatAsState(
        targetValue   = targetWPx,
        animationSpec = widthSpringSpec,
        label         = "pillW",
    )

    val hasPositions = itemWsPx.any { it > 0f }

    Box(
        modifier = modifier
            .height(IntrinsicSize.Min) // Высота навбара определяется его контентом
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

        // ── Навигационные элементы ───────────────────────────────────────────
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

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                        ) { onNavigate(item.route) }
                ) {
                    // ТРЮК: Статическая "невидимая" подложка для измерения размеров.
                    // Она всегда имеет РЕАЛЬНЫЙ конечный размер кнопки (вкладка с текстом, если активна,
                    // или просто иконка, если неактивна) БЕЗ динамической анимации во время переключения.
                    // Измеряя ЕЁ, мы получаем чистые финальные точки для пилюли без шума анимации!
                    Row(
                        modifier = Modifier
                            .alpha(0f) // Скрываем от глаз пользователя
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .onGloballyPositioned { coords ->
                                itemXsPx[idx] = coords.positionInParent().x
                                itemWsPx[idx] = coords.size.width.toFloat()
                            },
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        if (isActive) {
                            Text(
                                text       = item.label,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Реальный ВИДИМЫЙ контент с плавной анимацией текста
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
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
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow, 
                                    dampingRatio = Spring.DampingRatioNoBouncy
                                ),
                                expandFrom = Alignment.Start,
                            ) + fadeIn(),
                            exit = shrinkHorizontally(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow, 
                                    dampingRatio = Spring.DampingRatioNoBouncy
                                ),
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
}