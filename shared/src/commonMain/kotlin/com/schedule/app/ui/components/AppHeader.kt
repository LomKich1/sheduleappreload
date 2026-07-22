package com.schedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.navigation.Screen
import com.schedule.app.ui.theme.LocalAppColors

// ─── Единая шапка приложения ──────────────────────────────────────────────────
//
//  Раньше FilesScreen и BellsScreen рисовали каждый свою собственную шапку
//  ("Расписание" / "Расписание звонков" простым статичным текстом) — из-за
//  этого при переключении вкладок одна шапка резко подменялась другой, без
//  какой-либо анимации. Теперь шапка ОДНА, живёт на уровне AppScaffold (тот же
//  приём, что уже применён в ScheduleHostScreen для заголовка группы/препода),
//  а смена подписи "Расписание" ↔ "Звонки" идёт через FlipTransitionText —
//  тот самый сплит-флап переворот букв, что и там.
//
//  Кнопка настроек теперь доступна с ОБЕИХ вкладок (раньше — только с Files,
//  на Bells до неё нужно было сначала переключиться на Files).

@Composable
fun AppHeader(
    activeRoute: String,
    onSettingsClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val title = if (activeRoute == Screen.Files.route) "Расписание" else "Звонки"

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
                imageVector = CamekIcon,
                contentDescription = null,
                tint = c.text,
                modifier = Modifier.size(26.dp),
            )
            FlipTransitionText(
                text = title,
                color = c.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
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
