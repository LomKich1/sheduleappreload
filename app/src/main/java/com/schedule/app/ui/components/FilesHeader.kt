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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.R
import com.schedule.app.ui.theme.LocalAppColors

// ─── Шапка FilesScreen ────────────────────────────────────────────────────────
// Название приложения слева (как в Telegram) + кнопка меню справа. Перед
// названием — значок текущего колледжа (ic_camek.xml, трассирован из
// логотипа СаМеК). Красится в c.text, как обычная иконка, а не хранит
// собственный брендовый цвет — сейчас колледж всего один, но когда их
// станет больше, тут будет логотип конкретно выбранного. Кнопка настроек:
// без круглой серой подложки (осталась только риппл-анимация при нажатии —
// сама форма кнопки визуально не нужна, это просто иконка), значок —
// "гамбургер", как раньше было в мобильном Telegram.

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_camek),
                contentDescription = null,
                tint = c.text,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = "Расписание",
                color = c.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

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
