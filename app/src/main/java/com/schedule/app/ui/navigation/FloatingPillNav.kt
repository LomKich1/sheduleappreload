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
import androidx.compose.ui.draw.alpha
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
import kotlin.math.abs
import kotlin.math.roundToInt

// ─── Плавающий пузырёк навигации (переписан с нуля) ──────────────────────────
//
//  СТАРЫЙ БАГ: подпись показывалась/скрывалась через AnimatedVisibility
//  (expandHorizontally/shrinkHorizontally) — это отдельная, встроенная в
//  Compose система анимации размера. А ширина/позиция индикатора считались
//  отдельно (через onGloballyPositioned или через разовый Layout-замер).
//  Эти две системы не были синхронизированы кадр-в-кадр, и в конце анимации
//  переключения вкладок происходил рассинхрон — контейнер на миг становился
//  шире, чем нужно, а потом за один кадр резко "схлопывался" до правильного
//  размера.
//
//  НОВЫЙ ПОДХОД: никакого AnimatedVisibility. Есть ОДНО анимируемое число `t`
//  (плавно едет к индексу выбранной вкладки, без отскока). Из этого t
//  одновременно вычисляются: ширина каждой вкладки, прозрачность её подписи и
//  X-позиции вкладок. Раз всё выведено из одного и того же числа — контейнер
//  физически не может "разъехаться" сам с собой, следовательно и лишней ширины
//  взяться неоткуда.
//
//  Синий скользящий индикатор — это отдельный декоративный слой ПОД
//  элементами. У него своя, более "живая" пружина с отскоком (specifically
//  попросили оставить), но её цель считается по ФИНАЛЬНЫМ (уже устоявшимся)
//  размерам вкладок, а не по текущим анимирующимся — поэтому отскок индикатора
//  никак не влияет на размер самого контейнера.

private data class PillNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun FloatingPillNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val density = LocalDensity.current

    val items = remember {
        listOf(
            PillNavItem(Screen.Files.route, Icons.Outlined.CalendarMonth, "Расписание"),
            PillNavItem(Screen.Bells.route, Icons.Outlined.NotificationsNone, "Звонки"),
        )
    }

    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val spacingDp = 4.dp
    val spacingPx = with(density) { spacingDp.toPx() }
    val extraPaddingDp = 4.dp
    val extraPaddingPx = with(density) { extraPaddingDp.toPx() }
    val itemPaddingH = 12.dp
    val itemPaddingV = 9.dp
    val iconLabelGap = 6.dp

    // ── 1. Разовый замер ширин "компакт" (только иконка) и "полная" (иконка + подпись) ──
    // Меряем невидимым Layout один раз (числа стабильны, пока не поменялся текст/шрифт).
    var compactWidths by remember { mutableStateOf(List(items.size) { 0f }) }
    var fullWidths by remember { mutableStateOf(List(items.size) { 0f }) }

    Layout(
        content = {
            items.forEach { item ->
                Row(
                    modifier = Modifier.padding(horizontal = itemPaddingH, vertical = itemPaddingV),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(iconLabelGap),
                ) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            items.forEach { item ->
                Row(
                    modifier = Modifier.padding(horizontal = itemPaddingH, vertical = itemPaddingV),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(iconLabelGap),
                ) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
    ) { measurables, _ ->
        val unbounded = Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(unbounded) }
        val n = items.size
        val newCompact = placeables.take(n).map { it.width.toFloat() }
        val newFull = placeables.drop(n).map { it.width.toFloat() }
        if (compactWidths != newCompact) compactWidths = newCompact
        if (fullWidths != newFull) fullWidths = newFull
        layout(0, 0) {}
    }

    val hasSizes = fullWidths.isNotEmpty() && fullWidths.all { it > 0f } && compactWidths.all { it > 0f }

    // ── 2. Единый источник правды: t едет к индексу выбранной вкладки, без отскока ──
    val labelSpring = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy,
    )
    val t by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = labelSpring,
        label = "pillReveal",
    )

    // reveal(idx) = насколько близко t к этому индексу: 1 — вкладка полностью раскрыта,
    // 0 — полностью схлопнута. Для соседних индексов сумма долей всегда согласована,
    // потому что оба выведены из одного и того же t.
    fun revealOf(idx: Int): Float = (1f - abs(t - idx)).coerceIn(0f, 1f)

    val itemWidthsPx = items.indices.map { idx ->
        val compact = compactWidths.getOrElse(idx) { 0f }
        val full = fullWidths.getOrElse(idx) { 0f }
        compact + (full - compact) * revealOf(idx)
    }

    val itemXsPx = run {
        val xs = mutableListOf<Float>()
        var acc = 0f
        itemWidthsPx.forEach { w ->
            xs.add(acc)
            acc += w + spacingPx
        }
        xs
    }

    // ── 3. Скользящий индикатор — отдельная пружина с отскоком ──────────────────
    // Цель считаем по ФИНАЛЬНЫМ (устоявшимся) ширинам выбранной вкладки, а не по
    // itemWidthsPx (которые ещё анимируются) — индикатор не участвует в расчёте
    // размера контейнера, только рисуется поверх него.
    val indicatorSpring = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioMediumBouncy,
    )

    val restX = run {
        var acc = 0f
        for (idx in 0 until selectedIndex) {
            acc += compactWidths.getOrElse(idx) { 0f } + spacingPx
        }
        acc
    }
    val restWidth = fullWidths.getOrElse(selectedIndex) { 0f }

    val targetIndicatorXPx = (restX - extraPaddingPx).coerceAtLeast(0f)
    val targetIndicatorWPx = restWidth + extraPaddingPx * 2

    val indicatorXPx by animateFloatAsState(targetIndicatorXPx, indicatorSpring, label = "pillX")
    val indicatorWPx by animateFloatAsState(targetIndicatorWPx, indicatorSpring, label = "pillW")

    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(CircleShape)
            .background(c.pillBg)
            .border(1.dp, c.border, CircleShape)
            .padding(5.dp),
    ) {
        // ── Скользящий индикатор (слой ПОД элементами) ──────────────────────
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

        // ── Навигационные элементы ────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacingDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { idx, item ->
                val isActive = currentRoute == item.route
                val reveal = revealOf(idx)

                val contentColor by animateColorAsState(
                    targetValue = if (isActive) c.pillActiveText else c.pillInactiveText,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "pillContent$idx",
                )

                if (hasSizes) {
                    // Контролируемая ширина: контейнер жёстко зафиксирован в
                    // itemWidthsPx (та самая единая анимация), а внутренний Row
                    // всегда меряется на полный размер (иконка + подпись) и
                    // просто обрезается снаружи модификатором .clip(). Подпись
                    // не убирается из разметки — только становится прозрачной
                    // и попадает под обрезку. Никакой второй системы анимации
                    // размера здесь нет — только clip по уже готовому числу.
                    val widthDp = with(density) { itemWidthsPx[idx].toDp() }
                    Box(
                        modifier = Modifier
                            .width(widthDp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onNavigate(item.route) },
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                                .padding(horizontal = itemPaddingH, vertical = itemPaddingV),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(iconLabelGap),
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = item.label,
                                color = contentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.alpha(reveal),
                            )
                        }
                    }
                } else {
                    // Самый первый кадр, пока невидимый замер ещё не отработал —
                    // рисуем без анимации, просто иконку (+ подпись, если активна),
                    // чтобы не было пустоты. Как только придут реальные размеры,
                    // переключаемся на управляемую ветку выше.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onNavigate(item.route) }
                            .padding(horizontal = itemPaddingH, vertical = itemPaddingV),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(iconLabelGap),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        if (isActive) {
                            Text(
                                text = item.label,
                                color = contentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
