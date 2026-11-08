package com.schedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ─── Общие M3-детали экранов расписания (ученики / преподаватели) ─────────────
// Раньше каждая деталь была скопирована в ScheduleScreen и TeacherScheduleScreen.

/** Заголовок секции над списком пар («Понедельник, 10 ноября» и т.п.). */
@Composable
fun ScheduleSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** Бейдж «Сейчас» / «Следующая». */
@Composable
fun LessonStatusBadge(isNow: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (isNow) scheme.primary else scheme.tertiary,
    ) {
        Text(
            text = if (isNow) "Сейчас" else "Следующая",
            style = MaterialTheme.typography.labelMedium,
            color = if (isNow) scheme.onPrimary else scheme.onTertiary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Строка времени: «09:00–10:30» + чип «ПАРА» / «ПЕРЕМ». */
@Composable
fun PairTimeRow(time: String, tag: String, muted: Boolean, contentColor: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) contentColor.copy(alpha = 0.65f) else contentColor,
        )
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Пустое состояние с иконкой, заголовком и (опц.) кнопкой «Повторить». */
@Composable
fun ScheduleErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = scheme.errorContainer, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.WifiOff,
                    contentDescription = null,
                    tint = scheme.onErrorContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Повторить")
        }
    }
}
