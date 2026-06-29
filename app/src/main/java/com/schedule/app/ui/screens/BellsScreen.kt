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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import kotlinx.coroutines.delay
import java.util.Calendar

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
    TUE_FRI("Вт–Пт"),
    SAT("Сб"),
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
// Идентичны BELLS_MON / BELLS_TUE / BELLS_SAT в DocParser.kt

private val BELLS_MON = listOf(
    BellPeriod("I",   "09:00", "09:45", "09:50", "10:35"),
    BellPeriod("II",  "10:45", "11:30", "11:35", "12:20"),
    BellPeriod("III", "12:50", "13:35", "13:40", "14:25"),
    BellPeriod("IV",  "14:35", "15:35"),
    BellPeriod("V",   "15:45", "16:45"),
    BellPeriod("VI",  "16:55", "17:55"),
)
private val BELLS_TUE = listOf(
    BellPeriod("I",   "08:30", "09:15", "09:20", "10:05"),
    BellPeriod("II",  "10:15", "11:00", "11:05", "11:50"),
    BellPeriod("III", "12:20", "13:05", "13:10", "13:55"),
    BellPeriod("IV",  "14:05", "15:05"),
    BellPeriod("V",   "15:15", "16:15"),
    BellPeriod("VI",  "16:25", "17:25"),
)
private val BELLS_SAT = listOf(
    BellPeriod("I",   "08:30", "09:30"),
    BellPeriod("II",  "09:40", "10:40"),
    BellPeriod("III", "10:50", "11:50"),
    BellPeriod("IV",  "12:00", "13:00"),
    BellPeriod("V",   "13:10", "14:10"),
    BellPeriod("VI",  "14:20", "15:20"),
)

private fun bellsFor(type: BellDayType) = when (type) {
    BellDayType.MON     -> BELLS_MON
    BellDayType.TUE_FRI -> BELLS_TUE
    BellDayType.SAT     -> BELLS_SAT
}

private fun dayTypeFor(dayOfWeek: Int): BellDayType = when (dayOfWeek) {
    Calendar.MONDAY   -> BellDayType.MON
    Calendar.SATURDAY -> BellDayType.SAT
    else              -> BellDayType.TUE_FRI
}

private fun toMin(t: String): Int {
    val parts = t.split(':')
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
           (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

// ─── BellsScreen ─────────────────────────────────────────────────────────────

@Composable
fun BellsScreen() {
    val c = LocalAppColors.current

    // Живой тик каждые 30 с — обновляет текущее время и статус пары
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            tick++
        }
    }

    val cal       = remember(tick) { Calendar.getInstance() }
    val nowMin    = remember(tick) { cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) }
    val todayType = remember(tick) { dayTypeFor(cal.get(Calendar.DAY_OF_WEEK)) }
    val timeStr   = remember(tick) {
        "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
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
        // ── Шапка ─────────────────────────────────────────────────────────
        BellsHeader(timeStr = timeStr)

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
            text = "ПАРЫ И ПЕРЕМЕНЫ",
            color = c.textSub,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )

        // ── Список пар ────────────────────────────────────────────────────
        val periods    = bellsFor(selectedType)
        val isLiveDay  = selectedType == todayType

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(periods, key = { it.roman }) { period ->
                val isNow  = isLiveDay &&
                             nowMin in toMin(period.totalStart)..toMin(period.totalEnd)
                val isNext = isLiveDay && !isNow &&
                             toMin(period.totalStart) - nowMin in 1..30
                BellCard(period = period, isNow = isNow, isNext = isNext)
            }

            // Нижний отступ — чтобы последняя карточка не лезла под FloatingPillNav
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// ─── Шапка ───────────────────────────────────────────────────────────────────

@Composable
private fun BellsHeader(timeStr: String) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Расписание звонков",
                    color = c.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "сейчас $timeStr",
                    color = c.textSub,
                    fontSize = 12.sp,
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
}

// ─── Переключатель расписания ─────────────────────────────────────────────────
// Активный сегодняшний день подсвечивается todayAccent вместо обычного accent.
// Точка под ярлыком показывает «это сегодня» даже когда таб не выбран.

@Composable
private fun BellDayTabs(
    selected: BellDayType,
    todayType: BellDayType,
    onSelect: (BellDayType) -> Unit,
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .padding(4.dp),
    ) {
        BellDayType.values().forEach { type ->
            val isSelected = type == selected
            val isToday    = type == todayType

            val bg by animateColorAsState(
                targetValue = when {
                    isSelected && isToday -> c.todayAccent.copy(alpha = 0.20f)
                    isSelected            -> c.surface3
                    else                  -> Color.Transparent
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "bellTabBg",
            )
            val txt by animateColorAsState(
                targetValue = when {
                    isSelected && isToday -> c.todayAccent
                    isSelected            -> c.accent
                    else                  -> c.textSub
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "bellTabTxt",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelect(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = type.label,
                        color = txt,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    // Точка под ярлыком — «сегодня»
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) c.todayAccent
                                    else c.textSub.copy(alpha = 0.5f)
                                ),
                        )
                    }
                }
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
    val c = LocalAppColors.current

    // Статические цвета — без animateColorAsState.
    // isNow/isNext меняются раз в 30 с (тик), мгновенная смена цвета незаметна.
    // Убрав 4 Animatable × 6 карточек = 24 объекта, снижаем стоимость первой
    // компоновки BellsScreen и убираем лаг при переключении вкладок.
    val barColor    = when {
        isNow  -> c.todayAccent
        isNext -> Color(0xFF50C878)
        else   -> c.accent.copy(alpha = 0.55f)
    }
    val cardBg      = if (isNow) c.todayAccent.copy(alpha = 0.10f) else c.surface
    val borderColor = when {
        isNow  -> c.todayAccent.copy(alpha = 0.30f)
        isNext -> Color(0xFF50C878).copy(alpha = 0.30f)
        else   -> c.border
    }
    val timeColor   = if (isNow) c.todayAccent else c.text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp)),
    ) {
        // Левая цветная полоска — как в PairCard
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(barColor),
        )

        // Контент
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // Значок номера пары
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(barColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = period.roman,
                    color = barColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                )
            }

            // Основной блок: время + детали
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Строка 1: общий диапазон + значок статуса
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${period.totalStart} — ${period.totalEnd}",
                        color = timeColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (isNow || isNext) {
                        val badgeColor = if (isNow) c.todayAccent else Color(0xFF50C878)
                        val badgeText  = if (isNow) "▶ СЕЙЧАС" else "СЛЕДУЮЩАЯ"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(badgeColor)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.05.sp,
                            )
                        }
                    }
                }

                // Строка 2: детали (полупары + перемена) — только если есть перемена
                if (period.hasBreak) {
                    Text(
                        text = buildString {
                            append("${period.start1}–${period.end1}")
                            append("  ·  перем. ${period.breakMin} мин")
                            append("  ·  ${period.start2}–${period.end2}")
                        },
                        color = c.textSub,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }

                // Строка 3: длительность (только для суббот и IV–VI — там нет перемены,
                // но длительность пары больше стандартных 45 мин)
                if (!period.hasBreak) {
                    Text(
                        text = "${period.durationMin} мин",
                        color = c.textSub,
                        fontSize = 11.sp,
                    )
                } else {
                    // При наличии перемены выводим общую длительность после деталей
                    Text(
                        text = "итого ${period.durationMin} мин",
                        color = c.textSub.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(name = "Bells · Dark", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun PreviewBellsDark() = AppTheme(ThemePreset.DARK) { BellsScreen() }

@Preview(name = "Bells · AMOLED", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun PreviewBellsAmoled() = AppTheme(ThemePreset.AMOLED) { BellsScreen() }
