package com.schedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.LocalAppColors

// ─── Шапка FilesScreen ────────────────────────────────────────────────────────
// Слева: название группы (крупный акцент) + подпись.
// Справа: круглая кнопка настроек — стиль Telegram (мягкий фон-кружок + иконка).

@Composable
fun FilesHeader(
    groupName: String,
    onSettingsClick: () -> Unit,
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = groupName,
                color = c.accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "расписание занятий",
                color = c.textSub,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

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
