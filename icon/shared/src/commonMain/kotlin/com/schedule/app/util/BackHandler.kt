package com.schedule.app.util

import androidx.compose.runtime.Composable

// androidx.activity.compose.BackHandler существует только на Android (у ПК
// нет системной кнопки "Назад"). AppScaffold теперь вызывает этот
// expect-composable вместо андроидного напрямую.
//   • Android  → реальный androidx.activity.compose.BackHandler
//   • Desktop  → ничего не делает (жест "назад" на ПК не нужен)
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
