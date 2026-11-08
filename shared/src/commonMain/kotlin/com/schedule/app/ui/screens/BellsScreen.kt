package com.schedule.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
// Примечание: animateColorAsState/spring сохранены для BellDayTabs (анимация при тапе по табу).
// В BellCard используются статические цвета — они не меняются в рантайме,
// а animateColorAsState там создавал 24 лишних Animatable при каждом входе на экран.
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.CascadeEntranceItem
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import kotlinx.coroutines.delay
import com.schedule.app.util.currentLocalDateTime
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.schedule.app.ui.components.StaggeredSwapItem
import com.schedule.app.ui.theme.AppRadius

// ══════════════════════════════════════════════════════════════════════════════
//  BellsScreen — живое расписание звонков
//
//  Три варианта расписания (ПН / ВТ–ПТ / СБ), переключаемые табами.
//  Автоматически открывается нужный день. Тик каждые 30 с обновляет
//  статус текущей/следующей пары без пересоздания экрана.
// ══════════════════════════════════════════════════════════════════════════════

// ─── Типы расписания ─────────────────────────────────────────────────────────

enum class BellDayType(val label: String) {
    MON("Пн"),
    TUE_SAT("Вт–Сб"),
}

// ─── Модель одной пары ────────────────────────────────────────────────────────
//
//  start1 / end1  — первая полупара (или единственная)
//  start2 / end2  — вторая полупара после перемены (null на субботе и парах IV–VI)

private data class BellPeriod(
    val roman: String,
    val start1: String,
    val end1: String,
    val start2: String? = null,
    val end2: String? = null,
) {
    val totalStart: String get() = start1
    val totalEnd: String   get() = end2 ?: end1
    val hasBreak: Boolean  get() = start2 != null
    val durationMin: Int   get() = toMin(totalEnd) - toMin(totalStart)
    val breakMin: Int?     get() = if (start2 != null) toMin(start2) - toMin(end1) else null
}

// ─── Данные звонков ───────────────────────────────────────────────────────────
// Идентичны BELLS_MON / BELLS_TUE в DocParser.kt

private val BELLS_MON = listOf(
    BellPeriod("I",   "09:00", "09:45", "09:50", "10:35"),
    BellPeriod("II",  "10:45", "11:30", "11:35", "12:20"),
    BellPeriod("III", "12:40", "13:25", "13:30", "14:15"),
    BellPeriod("IV",  "14:25", "15:10", "15:15", "16:00"),
    BellPeriod("V",   "16:10", "16:55", "17:00", "17:45"),
    BellPeriod("VI",  "17:55", "18:40", "18:45", "19:30"),
)
private val BELLS_TUE = listOf(
    BellPeriod("I",   "08:30", "09:15", "09:20", "10:05"),
    BellPeriod("II",  "10:15", "11:00", "11:05", "11:50"),
    BellPeriod("III", "12:10", "12:55", "13:00", "13:45"),
    BellPeriod("IV",  "13:55", "14:40", "14:45", "15:30"),
    BellPeriod("V",   "15:40", "16:25", "16:30", "17:15"),
    BellPeriod("VI",  "17:25", "18:10", "18:15", "19:00"),
)

private fun bellsFor(type: BellDayType) = when (type) {
    BellDayType.MON     -> BELLS_MON
    BellDayType.TUE_SAT -> BELLS_TUE
}

private fun dayTypeFor(dayOfWeek: Int): BellDayType = when (dayOfWeek) {
    1    -> BellDayType.MON // ISO-8601 Monday
    else -> BellDayType.TUE_SAT
}

private fun toMin(t: String): Int {
    val parts = t.split(':')
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
           (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

// ─── BellsScreen ─────────────────────────────────────────────────────────────

@Composable
fun BellsScreen(entranceTrigger: Int = 0) {
    val c = LocalAppColors.current

    // Живой тик каждые 30 с — обновляет текущее время и статус пары
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            tick++
        }
    }

    val now       = remember(tick) { currentLocalDateTime() }
    val nowMin    = now.hour * 60 + now.minute
    val todayType = dayTypeFor(now.dayOfWeek.ordinal + 1)
    val timeStr   = remember(tick) {
        "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
    }

    var selectedType by remember { mutableStateOf(todayType) }
    // При изменении реального дня (перевалило за полночь) обновляем todayType,
    // но не сбрасываем выбор пользователя
    LaunchedEffect(todayType) { /* подписка нужна только для пересчёта */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        // Шапка ("Расписание звонков") переехала на уровень AppScaffold в
        // единый AppHeader (заголовок теперь просто "Звонки", с
        // flip-переходом при переключении вкладок). Живое время раньше было
        // подписью под тем заголовком — теперь просто отдельная строка тут,
        // тем же приёмом, что и TodayDateLine на главном экране.
        LiveTimeLine(timeStr = timeStr)

        Spacer(Modifier.height(14.dp))

        // ── Переключатель день/тип ─────────────────────────────────────────
        BellDayTabs(
            selected  = selectedType,
            todayType = todayType,
            onSelect  = { selectedType = it },
        )

        Spacer(Modifier.height(12.dp))

        // ── Метка секции ──────────────────────────────────────────────────
        Text(
            text = "Пары и перемены",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // ── Список пар ────────────────────────────────────────────────────
        val periods    = bellsFor(selectedType)
        val isLiveDay  = selectedType == todayType
        val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(periods, key = { index, _ -> index }) { index, _ ->
                CascadeEntranceItem(
                    index      = index,
                    triggerKey = entranceTrigger,
                    enabled    = entranceEnabled,
                    edge       = CascadeEdge.RIGHT,
                ) {
                    StaggeredSwapItem(
                        index       = index,
                        targetState = selectedType,
                        enabled     = entranceEnabled,
                    ) { dayType ->
                        val period = bellsFor(dayType)[index]
                        val isLive = dayType == todayType
                        val isNow  = isLive && nowMin in toMin(period.totalStart)..toMin(period.totalEnd)
                        val isNext = isLive && !isNow && toMin(period.totalStart) - nowMin in 1..30
                        BellCard(period = period, isNow = isNow, isNext = isNext)
                    }
                }
            }

            // Небольшой нижний отступ под последней карточкой
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// ─── Живое время ──────────────────────────────────────────────────────────────
// Раньше было подписью под заголовком BellsHeader — теперь заголовка тут
// больше нет (он один общий, в AppHeader), а время осталось отдельной
// строкой, тем же стилем, что и TodayDateLine на главном экране.

@Composable
private fun LiveTimeLine(timeStr: String) {
    Text(
        text = "сейчас $timeStr",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

// ─── Переключатель расписания (Material 3 SegmentedButton) ───────────────────
// Сегодняшний день помечается точкой "•" в подписи, даже когда он не выбран.

@Composable
private fun BellDayTabs(
    selected: BellDayType,
    todayType: BellDayType,
    onSelect: (BellDayType) -> Unit,
) {
    val types = BellDayType.values()
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
            ) {
                Text(if (type == todayType) "${type.label} •" else type.label)
            }
        }
    }
}

// ─── Карточка пары ───────────────────────────────────────────────────────────

@Composable
private fun BellCard(
    period: BellPeriod,
    isNow: Boolean,
    isNext: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    // Статические цвета — без animateColorAsState: isNow/isNext меняются раз
    // в 30 с (тик), анимация тут не нужна и только тормозит первую композицию.
    //  сейчас    → primaryContainer
    //  следующая → tertiaryContainer
    //  остальное → surfaceContainer
    val containerColor = when {
        isNow  -> scheme.primaryContainer
        isNext -> scheme.tertiaryContainer
        else   -> scheme.surfaceContainer
    }
    val contentColor = when {
        isNow  -> scheme.onPrimaryContainer
        isNext -> scheme.onTertiaryContainer
        else   -> scheme.onSurface
    }
    val badgeBg = if (isNow) scheme.primary else scheme.tertiary
    val badgeFg = if (isNow) scheme.onPrimary else scheme.onTertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Номер пары
            Surface(
                shape = CircleShape,
                color = if (isNow || isNext) badgeBg else scheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = period.roman,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isNow || isNext) badgeFg else scheme.onSecondaryContainer,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${period.totalStart} — ${period.totalEnd}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (isNow || isNext) {
                        Surface(shape = CircleShape, color = badgeBg) {
                            Text(
                                text = if (isNow) "Сейчас" else "Следующая",
                                style = MaterialTheme.typography.labelMedium,
                                color = badgeFg,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                if (period.hasBreak) {
                    Text(
                        text = buildString {
                            append("${period.start1}–${period.end1}")
                            append("  ·  перем. ${period.breakMin} мин")
                            append("  ·  ${period.start2}–${period.end2}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                    Text(
                        text = "итого ${period.durationMin} мин",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f),
                    )
                } else {
                    Text(
                        text = "${period.durationMin} мин",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewBellsDark() = AppTheme(ThemePreset.DARK) { BellsScreen() }

@Preview
@Composable
private fun PreviewBellsAmoled() = AppTheme(ThemePreset.AMOLED) { BellsScreen() }
