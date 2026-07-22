package com.schedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.ui.theme.LocalAppColors

// ─── Карточка файла расписания ───────────────────────────────────────────────
// Обычный файл: surface + border, иконка-документ, textSub дата, шеврон справа.
// Файл "сегодня": todayBg + todayAccent-рамка, акцентная иконка, бейдж "Сегодня".

@Composable
fun FileCard(
    file: ScheduleFile,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val bg = if (file.isToday) c.todayBg else c.surface
    val borderColor = if (file.isToday) c.todayAccent else c.border
    val iconTint = if (file.isToday) c.todayAccent else c.textSub
    val titleColor = if (file.isToday) c.todayAccent else c.text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (file.isToday) c.todayAccent.copy(alpha = 0.18f) else c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(file.dayLabel, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(file.dateLabel, color = c.textSub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }

        if (file.isToday) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.todayAccent)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Сегодня", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = c.textSub,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
