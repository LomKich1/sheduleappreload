package com.schedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.LocalAppColors

// ─── Плашка "РАСПИСАНИЕ" ──────────────────────────────────────────────────────
//
// Занимает место старой шапки с названием группы. По ширине — как файлы
// расписания (те же 18.dp отступы по бокам), по высоте — примерно в 2.3 раза
// больше карточки файла (карточка файла ≈ 68.dp, тут 160.dp).
//
// Bloom реализован БЕЗ Modifier.blur (он появился только в Android 12/API 31
// и не сработает на старых телефонах) — вместо этого два трюка:
//  1. Текст "РАСПИСАНИЕ" использует TextStyle.shadow с offset = 0 и большим
//     blurRadius — это создаёт мягкое свечение вокруг букв на ЛЮБОЙ версии Android.
//  2. Вокруг самой плашки — два полупрозрачных "кольца"-обводки чуть большего
//     размера с падающей прозрачностью: издалека читается как размытое
//     свечение по краям, хотя по факту это просто три вложенных Box.

@Composable
fun ScheduleTitlePlaque(
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current

    // Обводка плашки — лёгкий диагональный градиент между двумя "ступенями"
    // серого из существующей палитры (surface3 светлее border), чтобы обводка
    // не была плоской заливкой, а чуть переливалась.
    val borderBrush = Brush.linearGradient(
        colors = listOf(c.surface3, c.border, c.surface3),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(184.dp), // 160.dp сама плашка + 24.dp на "дыхание" bloom-колец
        contentAlignment = Alignment.Center,
    ) {
        // Кольцо bloom №2 (самое дальнее, самое бледное)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(34.dp))
                .border(1.dp, c.accent.copy(alpha = 0.08f), RoundedCornerShape(34.dp)),
        )
        // Кольцо bloom №1 (ближе к плашке, чуть заметнее)
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(30.dp))
                .border(2.dp, c.accent.copy(alpha = 0.14f), RoundedCornerShape(30.dp)),
        )

        // Сама плашка — сероватый фон (тот же c.surface, что был у старой шапки)
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.surface)
                .border(3.dp, borderBrush, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "РАСПИСАНИЕ",
                style = TextStyle(
                    color         = c.text,
                    fontSize      = 30.sp,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 2.sp,
                    shadow = Shadow(
                        color      = c.accent.copy(alpha = 0.65f),
                        offset     = Offset.Zero,
                        blurRadius = 42f,
                    ),
                ),
            )
        }
    }
}
