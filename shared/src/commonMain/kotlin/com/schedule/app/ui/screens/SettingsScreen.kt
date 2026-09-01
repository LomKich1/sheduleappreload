package com.schedule.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.data.prefs.AnimPrefs
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.data.prefs.TabAnimMode
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.CascadeEntranceItem
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.components.ScheduleModeToggle
import com.schedule.app.ui.theme.AppColors
import com.schedule.app.ui.theme.AppRadius
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import com.schedule.app.ui.theme.colorsFor
import com.schedule.app.util.IsDebugBuild
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── SettingsScreen (Шаг 2.5) ─────────────────────────────────────────────────
// Без отдельной ViewModel: экран не делает сетевых запросов, только читает/пишет
// AppPrefs. Поля URL/группы — локальный буфер редактирования, который коммитится
// в AppPrefs по кнопке «Сохранить». Тема переключается мгновенно, без ожидания
// сохранения — как и договаривались в плане.

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()

    val savedUrl      by AppPrefs.yandexUrl.collectAsState()
    val savedGroup    by AppPrefs.groupName.collectAsState()
    val theme         by AppPrefs.themePreset.collectAsState()
    val rememberOn    by AppPrefs.rememberGroup.collectAsState()
    val pinnedGroup   by AppPrefs.pinnedGroup.collectAsState()
    val entranceAnimOn by AppPrefs.listEntranceAnim.collectAsState()
    val defaultMode    by AppPrefs.defaultScheduleMode.collectAsState()

    var urlField      by remember(savedUrl) { mutableStateOf(savedUrl) }
    var showToast     by remember { mutableStateOf(false) }

    val canSave       = urlField.isNotBlank()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bg),
        ) {
            SettingsHeader(onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                Spacer(Modifier.height(18.dp))

                // Поле ссылки на Я.Диск и кнопка «Обновить список файлов» спрятаны
                // из UI по правкам дизайнера — но AppPrefs.yandexUrl/saveYandexUrl и
                // AppPrefs.requestFilesRefresh() остаются рабочими: список теперь
                // обновляется через pull-to-refresh на главном экране (см. FilesScreen),
                // а урл при необходимости всё ещё можно поменять программно/через
                // saveDataSource(), просто без видимого поля ввода.

                CascadeEntranceItem(index = 0, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    Column {
                        // ── Группа: текущая + переключатель запоминания ────────────
                        SettingsSectionLabel("Группа")
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Group,
                                    contentDescription = null,
                                    tint = c.textSub,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (savedGroup.isBlank()) "Не выбрана" else savedGroup,
                                    color = if (savedGroup.isBlank()) c.textSub else c.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                text = "Выбирается при открытии файла расписания — нажмите ✎ в шапке чтобы сменить",
                                color = c.textSub,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                CascadeEntranceItem(index = 1, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    // Переключатель «Запоминать группу»
                    SettingsCard {
                        GroupRememberRow(
                            rememberOn  = rememberOn,
                            pinnedGroup = pinnedGroup,
                            onToggle    = { AppPrefs.setRememberGroup(!rememberOn) },
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                CascadeEntranceItem(index = 2, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    Column {
                        SettingsSectionLabel("Тема оформления")
                        ThemeRow(
                            selected = theme,
                            onSelect = { AppPrefs.setTheme(it) },
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                CascadeEntranceItem(index = 3, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    Column {
                        SettingsSectionLabel("Экран по умолчанию")
                        SettingsCard {
                            Text(
                                text = "Что открывать первым на экране файла",
                                color = c.textSub,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            ScheduleModeToggle(
                                selected = defaultMode,
                                onSelect = { AppPrefs.setDefaultScheduleMode(it) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ── Debug-only: тонкая настройка анимации переключения вкладок ──
                // Видно только в debug-сборке (IsDebugBuild). Обычный пользователь
                // релиза этой секции вообще не видит и всегда сидит на DEFAULT —
                // старом простом поведении, никаких сюрпризов.
                if (IsDebugBuild) {
                    CascadeEntranceItem(index = 4, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                        Column {
                            SettingsSectionLabel("🐞 Debug: анимация вкладок")
                            SettingsCard {
                                AnimDebugSection()
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }

                CascadeEntranceItem(index = 5, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    SaveButton(
                        enabled = canSave,
                        onClick = {
                            AppPrefs.saveYandexUrl(urlField)
                            showToast = true
                            scope.launch { delay(900); onBack() }
                        },
                    )
                }

                Spacer(Modifier.height(80.dp))
            }
        }

        AnimatedVisibility(
            visible = showToast,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit  = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(AppRadius.capsule)
                    .background(c.accent)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(14.dp))
                Text("Настройки сохранены", color = c.onAccent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Шапка ────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    val c = LocalAppColors.current
    // Раньше здесь был отдельный фон (c.surface) + линия-разделитель снизу —
    // визуально отличалось от общей шапки Files/Bells (AppHeader: просто c.bg,
    // без границы). Теперь один и тот же язык шапки везде в приложении.
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
                .clip(CircleShape)
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
            text = "Настройки",
            color = c.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Мелкие строительные блоки ────────────────────────────────────────────────

@Composable
private fun SettingsSectionLabel(text: String) {
    val c = LocalAppColors.current
    Text(
        text = text.uppercase(),
        color = c.textSub,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.08.sp,
        modifier = Modifier.padding(bottom = 9.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppRadius.card)
            .background(c.surface)
            .border(1.dp, c.border, AppRadius.card)
            .padding(14.dp),
        content = content,
    )
}

// ─── Выбор темы ───────────────────────────────────────────────────────────────

@Composable
private fun ThemeRow(selected: ThemePreset, onSelect: (ThemePreset) -> Unit) {
    val c = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemePreset.values().forEach { preset ->
                ThemeSwatch(
                    preset     = preset,
                    isSelected = preset == selected,
                    onClick    = { onSelect(preset) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Раньше подпись темы дублировалась под каждой из трёх больших карточек —
        // теперь одна строка с названием ВЫБРАННОЙ темы под рядом свотчей: и место
        // экономит, и не нужно гадать, что означает круг без подписи.
        Text(
            text = selected.label,
            color = c.textSub,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ThemeSwatch(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val c: AppColors = LocalAppColors.current
    val swatch = colorsFor(preset)

    val ringColor by animateColorAsState(
        targetValue = if (isSelected) c.accent else Color.Transparent,
        label = "swatchRing",
    )

    // Раньше — сплошной круг цвета swatch.bg + отдельный маленький кружок-бейдж
    // с галочкой в углу ("кружок внутри кружка"). Теперь сам круг честно
    // показывает половину палитры: левая половина — фон темы, правая —
    // акцент. Индикатор выбора — та же кольцевая обводка (ringColor), что
    // была и раньше, без дополнительного бейджа поверх.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(2.dp, ringColor, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val halfWidth = size.width / 2f
            drawRect(
                color = swatch.bg,
                size  = Size(halfWidth, size.height),
            )
            drawRect(
                color   = swatch.accent,
                topLeft = Offset(halfWidth, 0f),
                size    = Size(halfWidth, size.height),
            )
        }
    }
}

// ─── Кнопка сохранения ─────────────────────────────────────────────────────────

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppRadius.capsule)
            .background(if (enabled) c.accent else c.surface2)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Сохранить",
            color = if (enabled) c.onAccent else c.textSub,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Переключатель запоминания группы ─────────────────────────────────────────

@Composable
private fun GroupRememberRow(
    rememberOn: Boolean,
    pinnedGroup: String,
    onToggle: () -> Unit,
) {
    val c = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Запоминать группу",
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (rememberOn && pinnedGroup.isNotBlank())
                        "Запомнена: $pinnedGroup"
                    else
                        "Выключено",
                    color = c.textSub,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TogglePill(checked = rememberOn, onToggle = onToggle)
        }

        AnimatedVisibility(
            visible = rememberOn,
            enter   = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 2 },
            exit    = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 },
        ) {
            Text(
                text = "Запомненная группа отображается первой при открытии расписания",
                color = c.textSub,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

// ─── Debug: настройка анимации переключения вкладок ───────────────────────────

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

// ─── Кастомный переключатель (pill toggle) ────────────────────────────────────

@Composable
private fun TogglePill(checked: Boolean, onToggle: () -> Unit) {
    val c = LocalAppColors.current

    val trackColor by animateColorAsState(
        targetValue   = if (checked) c.accent else c.surface3,
        animationSpec = tween(200),
        label         = "toggleTrack",
    )
    // Анимируем start-padding большого контейнера: 3.dp (выкл) → 23.dp (вкл)
    // Ширина трека 46.dp, паддинг 3.dp, кнопка 20.dp → 46-3-3-20=20.dp хода
    val thumbStart by animateDpAsState(
        targetValue   = if (checked) 23.dp else 3.dp,
        animationSpec = tween(200),
        label         = "toggleThumb",
    )

    // Бегунок раньше был жёстко белым: на тёмном треке (выкл) это ок, но на
    // светлом accent-треке в Монохроме (0xFFDEDEDE) белый кружок почти
    // терялся. Теперь цвет бегунка зависит от состояния — c.onAccent на
    // включённом треке гарантированно контрастен в любой теме.
    val thumbColor by animateColorAsState(
        targetValue   = if (checked) c.onAccent else Color.White,
        animationSpec = tween(200),
        label         = "toggleThumbColor",
    )

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(AppRadius.capsule)
            .background(trackColor)
            .clickable(onClick = onToggle),
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbStart, top = 3.dp, bottom = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
private fun PreviewSettingsDark() = AppTheme(ThemePreset.DARK) { SettingsScreen(onBack = {}) }
