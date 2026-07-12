package com.schedule.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.CascadeEntranceItem
import com.schedule.app.ui.components.FileCard
import com.schedule.app.ui.components.FilesHeader
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.components.ScheduleModeToggle
import com.schedule.app.ui.components.ScheduleTitlePlaque
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset

// ─── FilesScreen ──────────────────────────────────────────────────────────────
// Шаг 2.2: реальная загрузка через FilesViewModel (Я.Диск → GitHub).
// Три состояния: Loading (скелетоны), Success (список), Error (с кнопкой retry).

@Composable
fun FilesScreen(
    vm: FilesViewModel = viewModel(),
    onFileClick: (ScheduleFile, ScheduleMode) -> Unit = { _, _ -> },
    onSettingsClick: () -> Unit = {},
    entranceTrigger: Int = 0,
) {
    val uiState by vm.uiState.collectAsState()

    // Режим "Ученики / Преподаватели". Раньше был чисто визуальным —
    // теперь передаётся наверх при клике на файл (см. onFileClick выше),
    // AppScaffold решает по нему, какой экран открыть.
    var scheduleMode by rememberSaveable { mutableStateOf(ScheduleMode.STUDENT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.bg),
    ) {
        FilesHeader(onSettingsClick = onSettingsClick)

        ScheduleTitlePlaque()

        Spacer(Modifier.height(14.dp))

        ScheduleModeToggle(
            selected = scheduleMode,
            onSelect = { scheduleMode = it },
            modifier = Modifier.padding(horizontal = 18.dp),
        )

        Spacer(Modifier.height(16.dp))

        SectionLabel()

        when (val state = uiState) {
            is FilesUiState.Loading -> FilesLoading()
            is FilesUiState.Success -> FilesList(
                files     = state.files,
                onClick   = { file -> onFileClick(file, scheduleMode) },
                entranceTrigger = entranceTrigger,
            )
            is FilesUiState.Error   -> FilesError(
                message  = state.message,
                onRetry  = vm::refresh,
            )
        }
    }
}

// ─── Метка секции ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel() {
    val c = LocalAppColors.current
    Text(
        text = "ФАЙЛЫ РАСПИСАНИЯ",
        color = c.textSub,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.08.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
    )
}

// ─── Состояние: список ────────────────────────────────────────────────────────

@Composable
private fun FilesList(
    files: List<ScheduleFile>,
    onClick: (ScheduleFile) -> Unit,
    entranceTrigger: Int,
) {
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        itemsIndexed(files, key = { _, file -> file.name }) { index, file ->
            CascadeEntranceItem(
                index      = index,
                triggerKey = entranceTrigger,
                enabled    = entranceEnabled,
                edge       = CascadeEdge.LEFT,
            ) {
                FileCard(file = file, onClick = { onClick(file) })
            }
        }
    }
}

// ─── Состояние: загрузка (скелетоны) ─────────────────────────────────────────

@Composable
private fun FilesLoading() {
    val c = LocalAppColors.current
    val alpha by rememberInfiniteTransition(label = "skeleton")
        .animateFloat(
            initialValue = 1f,
            targetValue  = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alpha",
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        repeat(5) { i ->
            SkeletonCard(alpha = alpha, delay = i * 0.12f)
        }
    }
}

@Composable
private fun SkeletonCard(alpha: Float, delay: Float) {
    val c = LocalAppColors.current
    // Небольшой сдвиг фазы между карточками через offset на alpha
    val a = (alpha - delay).coerceIn(0.3f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface.copy(alpha = a))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(c.surface2.copy(alpha = a))
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 110.dp, height = 11.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2.copy(alpha = a)))
            Box(Modifier.size(width = 60.dp, height = 9.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2.copy(alpha = a)))
        }
    }
}

// ─── Состояние: ошибка ────────────────────────────────────────────────────────

@Composable
private fun FilesError(message: String, onRetry: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = null,
                tint = c.textSub,
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Не удалось загрузить файлы",
            color = c.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Я.Диск и GitHub недоступны.\nПроверь подключение к интернету.",
            color = c.textSub,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
            Text("Повторить", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "Dark", showBackground = true)
@Composable
private fun PreviewDark() = AppTheme(ThemePreset.DARK) { FilesScreen() }

@Preview(name = "AMOLED", showBackground = true)
@Composable
private fun PreviewAmoled() = AppTheme(ThemePreset.AMOLED) { FilesScreen() }
