package com.schedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.schedule.app.ui.theme.LocalAppColors

// ─── Шапка FilesScreen ────────────────────────────────────────────────────────
// Раньше тут показывалось название запомненной группы — теперь эта
// информация переехала внутрь плашки-переключателя ниже (см. ScheduleModeToggle
// и экран выбора группы/преподавателя), а сама шапка стала "невидимой":
// фон в цвет экрана (c.bg, а не c.surface, как раньше), никакого текста —
// только круглая кнопка настроек справа. Так плашка "РАСПИСАНИЕ" ниже
// становится единственным акцентом наверху экрана.

@Composable
fun FilesHeader(
    onSettingsClick: () -> Unit,
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(c.surface2),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Настройки",
                tint = c.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
