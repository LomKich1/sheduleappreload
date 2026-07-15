package com.schedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
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
// Название приложения слева (как в Telegram) + кнопка меню справа — теперь
// это единственный акцент наверху экрана, плашка "РАСПИСАНИЕ" ниже осталась
// как есть. Кнопка настроек: убрали круглую серую подложку (осталась только
// риппл-анимация при нажатии — сама форма кнопки визуально не нужна, это
// просто иконка), значок сменили с шестерёнки на "гамбургер" — три горизонтальные
// полоски, как раньше было в мобильном Telegram. Значок перед названием
// приложения — на будущее, отдельная и не самая тривиальная задача.

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
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Расписание",
            color = c.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                // 36dp — единый размер круглых кнопок по всему приложению
                // (см. кнопки "назад"/"карандаш" в ScheduleScreen, TeacherScheduleScreen,
                // SettingsScreen). Фон убран по правке — форма нужна только чтобы
                // ограничить область риппла кругом, а не для видимой подложки.
                .size(36.dp)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Настройки",
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
