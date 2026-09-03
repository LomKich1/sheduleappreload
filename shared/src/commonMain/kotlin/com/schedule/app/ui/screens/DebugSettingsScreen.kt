package com.schedule.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.data.parser.JsonScheduleParser
import com.schedule.app.data.prefs.AnimPrefs
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.data.prefs.TabAnimMode
import com.schedule.app.data.repository.DebugFileStore
import com.schedule.app.ui.theme.AppRadius
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import com.schedule.app.util.PickedTextFile
import com.schedule.app.util.rememberJsonFilePicker

// ─── DebugSettingsScreen ──────────────────────────────────────────────────────
// Отдельный экран для всех debug-only настроек (раньше жили инлайном прямо в
// SettingsScreen — по мере роста числа debug-инструментов это начало раздувать
// основной экран настроек, который обычный пользователь релиза даже не видит
// целиком). Доступен только из SettingsScreen, только когда IsDebugBuild
// (сама кнопка-переход туда тоже скрыта в релизе).
//
// Внутри — тонкая настройка анимации переключения вкладок, и тестовый
// прогон JSON-файлов расписания через JsonScheduleParser (см. JsonTestSection
// ниже) — без сети, без Я.Диска/GitHub, просто локальный файл с телефона.

@Composable
fun DebugSettingsScreen(onBack: () -> Unit) {
    val c = LocalAppColors.current

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        DebugSettingsHeader(onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            SettingsSectionLabel("Анимация вкладок")
            SettingsCard {
                AnimDebugSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Тест JSON-файла расписания")
            SettingsCard {
                JsonTestSection()
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DebugSettingsHeader(onBack: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(c.surface2)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Назад",
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "🐞 Debug-настройки",
            color = c.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Анимация вкладок ─────────────────────────────────────────────────────────

@Composable
private fun AnimDebugSection() {
    val c = LocalAppColors.current

    val mode        by AnimPrefs.mode.collectAsState()
    val durationMs  by AnimPrefs.durationMs.collectAsState()
    val damping     by AnimPrefs.springDamping.collectAsState()
    val stiffness   by AnimPrefs.springStiffness.collectAsState()
    val parallaxPow by AnimPrefs.parallaxPower.collectAsState()

    Column {
        Text(
            text = "Меняется сразу — переключись на вкладки Расписание/Звонки чтобы проверить",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // ── Выбор режима: три чипа ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimModeChip("Default", mode == TabAnimMode.DEFAULT, Modifier.weight(1f)) { AnimPrefs.setMode(TabAnimMode.DEFAULT) }
            AnimModeChip("Spring", mode == TabAnimMode.SPRING, Modifier.weight(1f)) { AnimPrefs.setMode(TabAnimMode.SPRING) }
            AnimModeChip("Parallax", mode == TabAnimMode.PARALLAX, Modifier.weight(1f)) { AnimPrefs.setMode(TabAnimMode.PARALLAX) }
        }

        Spacer(Modifier.height(16.dp))

        // ── Крутилки: разные для разных режимов ─────────────────────────────
        AnimatedVisibility(visible = mode == TabAnimMode.DEFAULT) {
            LabeledSlider(
                label     = "Длительность",
                valueText = "${durationMs}мс",
                value     = durationMs.toFloat(),
                range     = 100f..600f,
                onChange  = { AnimPrefs.setDurationMs(it.toInt()) },
            )
        }

        AnimatedVisibility(visible = mode == TabAnimMode.SPRING || mode == TabAnimMode.PARALLAX) {
            Column {
                LabeledSlider(
                    label     = "Damping (упругость)",
                    valueText = "%.2f".format(damping),
                    value     = damping,
                    range     = 0.3f..1.5f,
                    onChange  = { AnimPrefs.setSpringDamping(it) },
                )
                Spacer(Modifier.height(10.dp))
                LabeledSlider(
                    label     = "Stiffness (жёсткость)",
                    valueText = "%.0f".format(stiffness),
                    value     = stiffness,
                    range     = 50f..1500f,
                    onChange  = { AnimPrefs.setSpringStiffness(it) },
                )
                if (mode == TabAnimMode.PARALLAX) {
                    Spacer(Modifier.height(10.dp))
                    LabeledSlider(
                        label     = "Parallax power",
                        valueText = "%.2f".format(parallaxPow),
                        value     = parallaxPow,
                        range     = 1f..3f,
                        onChange  = { AnimPrefs.setParallaxPower(it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Сбросить к дефолтам",
            color = c.accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { AnimPrefs.resetToDefaults() }
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun AnimModeChip(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(
        targetValue   = if (isSelected) c.accent else c.surface2,
        animationSpec = tween(150),
        label         = "animChipBg",
    )
    val fg by animateColorAsState(
        targetValue   = if (isSelected) c.onAccent else c.textSub,
        animationSpec = tween(150),
        label         = "animChipFg",
    )
    Box(
        modifier = modifier
            .clip(AppRadius.capsule)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val c = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(text = valueText, color = c.textSub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor         = c.accent,
                activeTrackColor   = c.accent,
                inactiveTrackColor = c.surface3,
            ),
        )
    }
}

// ─── Тест JSON-файла ──────────────────────────────────────────────────────────
// Кнопка выбора локального .json-файла с телефона/диска + мгновенный
// предпросмотр того, что из него распарсилось (группы/преподы) — не нужно
// заливать тестовый файл на Я.Диск/GitHub каждый раз, пока JSON-конструктора
// ещё не существует и файлы приходится писать руками для проверки схемы.

private sealed class JsonTestState {
    object Idle : JsonTestState()
    data class Error(val message: String) : JsonTestState()
    data class Success(val file: PickedTextFile, val groups: List<String>, val teachers: List<String>) : JsonTestState()
    data class Saved(val fileName: String) : JsonTestState()
}

@Composable
private fun JsonTestSection() {
    val c = LocalAppColors.current
    var state by remember { mutableStateOf<JsonTestState>(JsonTestState.Idle) }

    val pickFile = rememberJsonFilePicker { picked ->
        state = when {
            picked == null -> JsonTestState.Idle // отмена выбора — молча ничего не меняем
            else -> runCatching {
                val groups   = JsonScheduleParser.detectGroups(picked.content)
                val teachers = JsonScheduleParser.detectTeachers(picked.content)
                JsonTestState.Success(picked, groups, teachers)
            }.getOrElse { err ->
                JsonTestState.Error(err.message ?: "Не удалось разобрать файл как JSON")
            }
        }
    }

    Column {
        Text(
            text = "Выбери .json-файл дня — покажем, что из него нашлось, без сети и без ScheduleViewModel",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppRadius.capsule)
                .background(c.accent)
                .clickable { pickFile() }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("📄 Выбрать JSON-файл", color = c.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        when (val s = state) {
            is JsonTestState.Idle -> Unit

            is JsonTestState.Error -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "❌ ${s.message}",
                    color = c.text,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            is JsonTestState.Saved -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "✅ Сохранено как «${s.fileName}» — открой вкладку Файлы, он там сверху списка",
                    color = c.text,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            is JsonTestState.Success -> {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Найдено групп: ${s.groups.size}",
                    color = c.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (s.groups.isNotEmpty()) {
                    Text(
                        text = s.groups.joinToString(", "),
                        color = c.textSub,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                }
                Text(
                    text = "Найдено преподавателей: ${s.teachers.size}",
                    color = c.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (s.teachers.isNotEmpty()) {
                    Text(
                        text = s.teachers.joinToString(", "),
                        color = c.textSub,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Сохранение — держит файл в оперативной памяти (DebugFileStore),
                // ровно как обычный кеш файлов с Я.Диска. Пропадёт при
                // перезапуске приложения — это ожидаемо, не баг.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppRadius.capsule)
                            .background(c.surface2)
                            .clickable { state = JsonTestState.Idle }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Не сохранять", color = c.textSub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppRadius.capsule)
                            .background(c.accent)
                            .clickable {
                                val saved = DebugFileStore.save(s.file.name, s.file.content)
                                state = if (saved != null) {
                                    AppPrefs.requestFilesRefresh()
                                    JsonTestState.Saved(saved.name)
                                } else {
                                    JsonTestState.Error(
                                        "Имя файла «${s.file.name}» не похоже на день расписания " +
                                            "(нужен формат dd_MM_yyyy_ДЕНЬ.json)"
                                    )
                                }
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Сохранить", color = c.onAccent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
private fun PreviewDebugSettingsDark() = AppTheme(ThemePreset.DARK) { DebugSettingsScreen(onBack = {}) }
