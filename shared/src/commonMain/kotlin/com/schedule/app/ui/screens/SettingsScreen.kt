package com.schedule.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.CascadeEntranceItem
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.components.ScheduleModeSegmented
import com.schedule.app.ui.components.rememberSwipeDismissState
import com.schedule.app.ui.components.swipeToDismiss
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.ThemePreset
import com.schedule.app.util.BackHandler
import com.schedule.app.util.IsDebugBuild
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── SettingsScreen (Шаг 2.5) ─────────────────────────────────────────────────
// Без отдельной ViewModel: экран не делает сетевых запросов, только читает/пишет
// AppPrefs. Поля URL/группы — локальный буфер редактирования, который коммитится
// в AppPrefs по кнопке «Сохранить». Тема переключается мгновенно, без ожидания
// сохранения — как и договаривались в плане.
//
// Живой свайп-закрытие (см. rememberSwipeDismissState/swipeToDismiss) — тот же
// Telegram-style жест, что уже был у PairsOverlay в расписании (см.
// ScheduleScreen.kt). Здесь он проще: под этим экраном НЕТ второго "слоя"
// внутри самого SettingsScreen (в отличие от Picker/Pairs) — снизу просто
// постоянно смонтированные Files/Bells из AppScaffold (NavHost рисуется
// поверх них), так что естественная "раскрывающаяся" картинка получается
// бесплатно, без какой-либо специальной подготовки на этом экране.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    // onDismissProgressChanged — наружу, в AppScaffold: там рисуется
    // DismissDimScrim НАД уже смонтированными Files/Bells, а сам
    // SwipeDismissState живёт здесь. SideEffect — честное "публикуем значение
    // после успешной композиции".
    onDismissProgressChanged: (Float) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val savedUrl       by AppPrefs.yandexUrl.collectAsState()
    val savedGroup     by AppPrefs.groupName.collectAsState()
    val theme          by AppPrefs.themePreset.collectAsState()
    val rememberOn     by AppPrefs.rememberGroup.collectAsState()
    val pinnedGroup    by AppPrefs.pinnedGroup.collectAsState()
    val entranceAnimOn by AppPrefs.listEntranceAnim.collectAsState()
    val defaultMode    by AppPrefs.defaultScheduleMode.collectAsState()

    var urlField  by remember(savedUrl) { mutableStateOf(savedUrl) }
    var showToast by remember { mutableStateOf(false) }

    val canSave = urlField.isNotBlank()

    val dismissState = rememberSwipeDismissState(onDismissed = onBack)
    BackHandler { dismissState.dismiss() }

    // 0 — экран на месте, 1 — уехал за край. coerceAtLeast(1f) — защита от
    // деления на ноль в первом кадре до layout.
    val dismissProgress = (dismissState.offsetX.value / dismissState.widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    SideEffect { onDismissProgressChanged(dismissProgress) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeToDismiss(dismissState)
                .background(MaterialTheme.colorScheme.background),
        ) {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { dismissState.dismiss() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                // Инсеты уже сняты родителем (см. AppScaffold) — свои не нужны.
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                // Поле ссылки на Я.Диск и кнопка «Обновить список файлов» скрыты
                // из UI по правкам дизайнера — AppPrefs.yandexUrl/saveYandexUrl
                // остаются рабочими, список обновляется pull-to-refresh.

                // ── Группа ──────────────────────────────────────────────────
                CascadeEntranceItem(index = 0, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    Column {
                        SettingsSectionLabel("Группа")
                        SettingsListCard {
                            ListItem(
                                colors = transparentListItemColors(),
                                leadingContent = { Icon(Icons.Outlined.Group, contentDescription = null) },
                                headlineContent = {
                                    Text(if (savedGroup.isBlank()) "Не выбрана" else savedGroup)
                                },
                                supportingContent = {
                                    Text("Выбирается при открытии файла расписания — нажмите ✎ в шапке, чтобы сменить")
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            ListItem(
                                colors = transparentListItemColors(),
                                modifier = Modifier.clickable { AppPrefs.setRememberGroup(!rememberOn) },
                                headlineContent = { Text("Запоминать группу") },
                                supportingContent = {
                                    Text(
                                        if (rememberOn && pinnedGroup.isNotBlank()) "Запомнена: $pinnedGroup"
                                        else if (rememberOn) "Запомненная группа отображается первой при открытии расписания"
                                        else "Выключено",
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = rememberOn,
                                        onCheckedChange = { AppPrefs.setRememberGroup(it) },
                                    )
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Внешний вид: тема через мини-меню ───────────────────────
                CascadeEntranceItem(index = 1, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    Column {
                        SettingsSectionLabel("Внешний вид")
                        SettingsListCard {
                            ListItem(
                                colors = transparentListItemColors(),
                                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                                headlineContent = { Text("Тема оформления") },
                                trailingContent = {
                                    ThemeMenuButton(
                                        selected = theme,
                                        onSelect = { AppPrefs.setTheme(it) },
                                    )
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Экран по умолчанию ──────────────────────────────────────
                CascadeEntranceItem(index = 2, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    Column {
                        SettingsSectionLabel("Экран по умолчанию")
                        SettingsListCard {
                            ListItem(
                                colors = transparentListItemColors(),
                                leadingContent = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                                headlineContent = { Text("Что открывать первым на экране файла") },
                            )
                            ScheduleModeSegmented(
                                selected = defaultMode,
                                onSelect = { AppPrefs.setDefaultScheduleMode(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Debug-only ──────────────────────────────────────────────
                // Видно только в debug-сборке (IsDebugBuild).
                if (IsDebugBuild) {
                    CascadeEntranceItem(index = 3, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                        Column {
                            SettingsSectionLabel("Debug")
                            SettingsListCard {
                                ListItem(
                                    colors = transparentListItemColors(),
                                    modifier = Modifier.clickable(onClick = onNavigateToDebug),
                                    leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                                    headlineContent = { Text("Debug-настройки") },
                                    trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                CascadeEntranceItem(index = 4, triggerKey = Unit, enabled = entranceAnimOn, edge = CascadeEdge.RIGHT) {
                    Button(
                        onClick = {
                            AppPrefs.saveYandexUrl(urlField)
                            showToast = true
                            scope.launch { delay(900); dismissState.dismiss() }
                        },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("Сохранить")
                    }
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
                .padding(16.dp),
        ) {
            Snackbar {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Настройки сохранены")
                }
            }
        }
    }
}

// ─── Выбор темы: кнопка + мини-меню (DropdownMenu) ────────────────────────────
// Кнопка показывает название текущей темы. По нажатию раскрывается маленькое
// выпадающее меню прямо под ней (НЕ новый экран) со списком тем по названиям;
// у выбранной — галочка. Тема применяется сразу, меню закрывается.

@Composable
private fun ThemeMenuButton(
    selected: ThemePreset,
    onSelect: (ThemePreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // Box — якорь: DropdownMenu позиционируется относительно него.
    Box {
        FilledTonalButton(onClick = { expanded = true }) {
            Text(selected.label)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = "Выбрать тему",
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ThemePreset.values().forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                    trailingIcon = {
                        if (preset == selected) {
                            Icon(Icons.Outlined.Check, contentDescription = "Выбрано")
                        }
                    },
                )
            }
        }
    }
}

// ─── Мелкие строительные блоки (используются и на других экранах) ─────────────

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

/** Карточка с внутренним отступом — для произвольного контента (Debug-экран). */
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Карточка без отступа — для строк ListItem, у которых свой padding. */
@Composable
private fun SettingsListCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        content = content,
    )
}

@Composable
private fun transparentListItemColors() =
    ListItemDefaults.colors(containerColor = Color.Transparent)

// ─── Preview ────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
private fun PreviewSettingsDark() = AppTheme(ThemePreset.DARK) { SettingsScreen(onBack = {}) }
