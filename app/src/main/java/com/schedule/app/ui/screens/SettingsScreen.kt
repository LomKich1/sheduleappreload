package com.schedule.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.theme.AppColors
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import com.schedule.app.ui.theme.colorsFor
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

    val savedUrl   by AppPrefs.yandexUrl.collectAsState()
    val savedGroup by AppPrefs.groupName.collectAsState()
    val theme      by AppPrefs.themePreset.collectAsState()

    var urlField    by remember(savedUrl) { mutableStateOf(savedUrl) }
    var groupField  by remember(savedGroup) { mutableStateOf(savedGroup) }
    var showToast   by remember { mutableStateOf(false) }
    var justRefreshed by remember { mutableStateOf(false) }

    val urlLooksValid   = "disk.yandex.ru" in urlField
    val groupLooksValid = GROUP_HINT_RE.matches(groupField.trim())
    val canSave          = urlField.isNotBlank() && groupField.isNotBlank()

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

                SettingsSectionLabel("Источник данных")
                SettingsCard {
                    FieldLabel("Папка на Яндекс.Диске")
                    SettingsInputRow(
                        value = urlField,
                        onValueChange = { urlField = it },
                        leadingIcon = Icons.Outlined.Cloud,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = if (urlLooksValid)
                            "Публичная ссылка на папку с файлами .doc"
                        else
                            "Похоже, это не ссылка на Я.Диск — проверь адрес",
                        color = if (urlLooksValid) c.textSub else c.accent,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }

                Spacer(Modifier.height(10.dp))

                RefreshFilesRow(
                    justRefreshed = justRefreshed,
                    onClick = {
                        AppPrefs.requestFilesRefresh()
                        justRefreshed = true
                        scope.launch { delay(1400); justRefreshed = false }
                    },
                )

                Spacer(Modifier.height(22.dp))

                SettingsSectionLabel("Моя группа")
                SettingsCard {
                    FieldLabel("Код группы")
                    SettingsInputRow(
                        value = groupField,
                        onValueChange = { groupField = it },
                        leadingIcon = Icons.Outlined.Group,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = if (groupLooksValid)
                            "Например: МПД-2-24, ИС-1-23"
                        else
                            "Формат обычно: БУКВЫ-цифра-цифра (МПД-2-24)",
                        color = if (groupLooksValid) c.textSub else c.accent,
                        fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(22.dp))

                SettingsSectionLabel("Тема оформления")
                ThemeRow(
                    selected = theme,
                    onSelect = { AppPrefs.setTheme(it) },
                )

                Spacer(Modifier.height(22.dp))

                SaveButton(
                    enabled = canSave,
                    onClick = {
                        AppPrefs.saveDataSource(urlField, groupField)
                        showToast = true
                        scope.launch { delay(900); onBack() }
                    },
                )

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
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.accent)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Настройки сохранены", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Лёгкая подсказка по формату группы — НЕ источник истины (тот живёт в DocParser.GRP_RE),
// просто визуальный хинт, чтобы не дублировать private-доступ к парсеру.
private val GROUP_HINT_RE = Regex("""^\d{0,2}[А-ЯЁа-яёA-Za-z]{1,6}-?\d-\d{2}$""")

// ─── Шапка ────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    val c = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().background(c.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(c.border))
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
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun FieldLabel(text: String) {
    val c = LocalAppColors.current
    Text(
        text = text,
        color = c.textSub,
        fontSize = 11.5.sp,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

@Composable
private fun SettingsInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: ImageVector,
) {
    val c = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) c.accent else c.border,
        label = "inputBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(c.surface2)
            .border(1.dp, borderColor, RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = c.textSub,
            modifier = Modifier.size(16.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(color = c.text, fontSize = 13.5.sp),
            singleLine = true,
            cursorBrush = SolidColor(c.accent),
        )
    }
}

// «Обновить файлы» — пригодилось из старой версии: список может поменяться
// внутри той же папки на Я.Диске без смены самого URL.
@Composable
private fun RefreshFilesRow(justRefreshed: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (justRefreshed) "Список будет обновлён" else "Обновить список файлов",
            color = c.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Выбор темы ───────────────────────────────────────────────────────────────

@Composable
private fun ThemeRow(selected: ThemePreset, onSelect: (ThemePreset) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ThemePreset.values().forEach { preset ->
            ThemeChip(
                preset     = preset,
                isSelected = preset == selected,
                onClick    = { onSelect(preset) },
                modifier   = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeChip(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c: AppColors = LocalAppColors.current
    val swatch = colorsFor(preset)

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) c.accent else c.border,
        label = "chipBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) c.accent.copy(alpha = 0.12f) else c.surface,
        label = "chipBg",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(swatch.bg)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(9.dp)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(swatch.accent),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = preset.label,
            color = if (isSelected) c.text else c.textSub,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Кнопка сохранения ─────────────────────────────────────────────────────────

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) c.accent else c.surface2)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Сохранить",
            color = if (enabled) Color.White else c.textSub,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(name = "Settings · Dark", showBackground = true)
@Composable
private fun PreviewSettingsDark() = AppTheme(ThemePreset.DARK) { SettingsScreen(onBack = {}) }
