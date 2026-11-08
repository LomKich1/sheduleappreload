package com.schedule.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Совместимость: старые экраны ещё ссылаются на AppRadius.
// Значения приведены к шкале форм Material 3 (см. AppShapes).
//  card    — M3 "large" (16dp)
//  capsule — полностью скруглённый элемент (стадион)
object AppRadius {
    val card = RoundedCornerShape(16.dp)
    val capsule = RoundedCornerShape(percent = 50)
}
