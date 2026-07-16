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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
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

    // Конечные (статические) позиции и размеры элементов
    val itemXsPx = remember { mutableStateListOf(0f, 0f) }
    val itemWsPx = remember { mutableStateListOf(0f, 0f) }

    val extraPaddingDp = 12.dp 
    val extraPaddingPx = with(density) { extraPaddingDp.toPx() }

    // Пружина для ПЕРЕМЕЩЕНИЯ (X) индикатора — оставляем прыгучей
    val positionSpringSpec = spring<Float>(
        stiffness    = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioMediumBouncy,
    )

    // Пружина для ШИРИНЫ (W) индикатора — делаем мягкой, без лишнего отскока
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
            .height(IntrinsicSize.Min)
            .clip(CircleShape)
            .background(c.pillBg)
            .border(1.dp, c.border, CircleShape)
            .padding(5.dp),
    ) {

        // Скользящий индикатор под элементами
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

                // Анимируем ширину (вес/коэффициент раскрытия) текста от 0f до 1f
                val textExpansion by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    ),
                    label = "textExpansion$idx"
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                        ) { onNavigate(item.route) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .onGloballyPositioned { coords ->
                            // Записываем координаты ТОЛЬКО когда анимация завершена (textExpansion равен 1f или 0f),
                            // либо при самом первом кадре инициализации. Это полностью убирает "виляние" во время анимации!
                            if (textExpansion == 1f || textExpansion == 0f || itemWsPx[idx] == 0f) {
                                itemXsPx[idx] = coords.positionInParent().x
                                itemWsPx[idx] = coords.size.width.toFloat()
                            }
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

                    // Вместо AnimatedVisibility используем кастомный контейнер с плавной шириной
                    // Он не дергается в конце анимации и идеально рассчитывает размеры
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            // Плавно меняем ширину от 0 до максимальной ширины текста
                            .width(IntrinsicSize.Min)
                            .graphicsLayer {
                                // Плавный скейл и прозрачность для красоты
                                alpha = textExpansion
                                scaleX = textExpansion
                            }
                            .drawWithContent {
                                // Рисуем текст только в пределах его текущего раскрытия, чтобы он не "вылезал" наружу
                                if (textExpansion > 0f) {
                                    drawContent()
                                }
                            }
                    ) {
                        Text(
                            text       = item.label,
                            color      = contentColor,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines   = 1,
                        )
                    }
                }
            }
        }
    }
}