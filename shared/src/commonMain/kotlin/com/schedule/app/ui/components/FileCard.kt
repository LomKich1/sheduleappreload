package com.schedule.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.schedule.app.data.model.ScheduleFile

// ─── Карточка файла расписания (Material 3 Card + ListItem) ───────────────────
// Обычный файл: filled-карточка surfaceContainer, иконка в secondaryContainer.
// Файл "сегодня": primaryContainer + бейдж "Сегодня".

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCard(
    file: ScheduleFile,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (file.isToday) scheme.primaryContainer else scheme.surfaceContainer
    val contentColor = if (file.isToday) scheme.onPrimaryContainer else scheme.onSurface
    val supportingColor = if (file.isToday) scheme.onPrimaryContainer else scheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = contentColor,
                supportingColor = supportingColor,
            ),
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = if (file.isToday) scheme.primary else scheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = if (file.isToday) scheme.onPrimary else scheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            headlineContent = { Text(file.dayLabel, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(file.dateLabel, style = MaterialTheme.typography.bodyMedium) },
            trailingContent = {
                if (file.isToday) {
                    Surface(shape = CircleShape, color = scheme.primary) {
                        Text(
                            text = "Сегодня",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
