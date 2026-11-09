package com.schedule.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import com.schedule.app.ui.components.wideContentWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Сколько нужно удерживать «Сохранить», чтобы открылась пасхалка (CreditsDialog).
private const val SECRET_HOLD_MS = 2000L

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

    // Пасхалка: удержание «Сохранить» ~2 с открывает окно с авторами. Обычный тап
    // сохраняет как раньше; если удержание сработало, отпускание кнопки
    // (onClick) ничего не сохраняет и экран не закрывает.
    var showCredits by remember { mutableStateOf(false) }
    var secretFired by remember { mutableStateOf(false) }
    val saveInteraction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(saveInteraction) {
        var hold: Job? = null
        saveInteraction.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    secretFired = false
                    hold?.cancel()
                    hold = launch {
                        delay(SECRET_HOLD_MS)
                        secretFired = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCredits = true
                    }
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> hold?.cancel()
            }
        }
    }

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
                .background(MaterialTheme.colorScheme.background)
                // Инсеты — ПОСЛЕ фона: цвет темы заливает и зону под статус-баром,
                // и под жестовой полосой, и уезжает вместе с экраном при свайпе.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical)),
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

            // Скролл — на весь экран (колесо мыши работает по всей ширине), а сам
            // контент на ПК ограничен по ширине и стоит по центру.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
            Column(
                modifier = Modifier
                    .wideContentWidth()
                    .fillMaxWidth()
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
                            )
                            ThemeMenuButton(
                                selected = theme,
                                onSelect = { AppPrefs.setTheme(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
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
                            if (secretFired) {
                                secretFired = false
                            } else {
                                AppPrefs.saveYandexUrl(urlField)
                                showToast = true
                                scope.launch { delay(900); dismissState.dismiss() }
                            }
                        },
                        enabled = canSave,
                        interactionSource = saveInteraction,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("Сохранить")
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
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

        if (showCredits) {
            CreditsDialog(onDismiss = { showCredits = false })
        }
    }
}

// ─── Выбор темы: кнопка + «приклеенное» мини-меню ────────────────────────────
//
//  Кнопка на всю ширину карточки показывает название текущей темы. По нажатию
//  под ней раскрывается мини-меню (НЕ новый экран) со списком тем по названиям:
//    • меню ровно по ширине кнопки и прилегает к её нижнему краю без зазора;
//    • пока меню открыто, нижние углы кнопки плавно становятся прямыми, а у
//      меню прямые верхние и скруглённые нижние — получается единая форма;
//    • стрелка на кнопке поворачивается: ▼ закрыто → ▲ открыто;
//    • у выбранной темы стоит галочка, последняя строка скруглена вместе с меню.
//  Меню самописное (Popup): стандартный DropdownMenu не умеет прилегать к
//  якорю и менять форму кнопки.

private val MENU_BUTTON_HEIGHT = 48.dp
private val MENU_CORNER = 24.dp

@Composable
private fun ThemeMenuButton(
    selected: ThemePreset,
    onSelect: (ThemePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme

    // Нижние углы кнопки: скруглены, пока меню закрыто; прямые, пока открыто.
    val bottomCorner by animateDpAsState(
        targetValue = if (expanded) 0.dp else MENU_CORNER,
        animationSpec = tween(200),
        label = "themeButtonCorner",
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "themeArrow",
    )

    // Popup живёт, пока играет анимация закрытия (иначе он исчез бы мгновенно).
    val menuState = remember { MutableTransitionState(false) }
    menuState.targetState = expanded

    Box(modifier = modifier) {
        FilledTonalButton(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(
                topStart = MENU_CORNER,
                topEnd = MENU_CORNER,
                bottomEnd = bottomCorner,
                bottomStart = bottomCorner,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(MENU_BUTTON_HEIGHT)
                .onSizeChanged { buttonSize = it },
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = if (expanded) "Свернуть список тем" else "Выбрать тему",
                modifier = Modifier.size(24.dp).rotate(arrowRotation),
            )
        }

        if (menuState.currentState || menuState.targetState) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, buttonSize.height),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = menuState,
                    enter = fadeIn(tween(120)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)),
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 0.dp,
                            bottomStart = MENU_CORNER,
                            bottomEnd = MENU_CORNER,
                        ),
                        color = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        modifier = Modifier.width(with(density) { buttonSize.width.toDp() }),
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 288.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ThemePreset.values().forEach { preset ->
                                ThemeMenuItem(
                                    label = preset.label,
                                    isSelected = preset == selected,
                                    onClick = {
                                        onSelect(preset)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeMenuItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (isSelected) scheme.onSecondaryContainer.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(Icons.Outlined.Check, contentDescription = "Выбрано", modifier = Modifier.size(20.dp))
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
