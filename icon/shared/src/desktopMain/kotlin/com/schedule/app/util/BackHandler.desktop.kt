package com.schedule.app.util

import androidx.compose.runtime.Composable

// На ПК нет системной кнопки/жеста "назад" — просто ничего не делаем.
// Если позже понадобится реагировать на Esc или Alt+Left, это можно
// добавить сюда отдельно (например, через onPreviewKeyEvent в окне).
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op на десктопе
}
