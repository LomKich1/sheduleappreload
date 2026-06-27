package com.schedule.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.LocalAppColors

// ─── Плавающий пузырёк навигации ─────────────────────────────────────────────
// Не имеет фоновой полосы — просто капсула поверх контента

@Composable
fun FloatingPillNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(c.pillBg)
            .border(1.dp, c.border, CircleShape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillItem(
            icon = Icons.Outlined.CalendarMonth,
            label = "Расписание",
            isActive = currentRoute == Screen.Files.route,
            onClick = { onNavigate(Screen.Files.route) },
        )
        PillItem(
            icon = Icons.Outlined.NotificationsNone,
            label = "Звонки",
            isActive = currentRoute == Screen.Bells.route,
            onClick = { onNavigate(Screen.Bells.route) },
        )
    }
}

@Composable
private fun PillItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current

    val bgColor by animateColorAsState(
        targetValue = if (isActive) c.pillActive else c.pillBg,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) c.pillActiveText else c.pillInactiveText,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillContent",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = if (isActive) 16.dp else 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        // Лейбл появляется только у активного таба
        if (isActive) {
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
