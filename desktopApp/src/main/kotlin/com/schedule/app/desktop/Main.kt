package com.schedule.app.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.schedule.app.data.prefs.AnimPrefs
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.AppScaffold
import com.schedule.app.ui.theme.AppTheme

fun main() = application {
    // На ПК platformHandle не нужен (нет Context) — передаём null.
    AppPrefs.init(null)
    AnimPrefs.init(null)

    Window(
        onCloseRequest = ::exitApplication,
        title = "ScheduleApp",
        state = initialWindowState(),
    ) {
        val theme by AppPrefs.themePreset.collectAsState()
        AppTheme(preset = theme) {
            AppScaffold()
        }
    }
}

private fun initialWindowState() = WindowState(
    position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
    // Минимально широкое окно: от 840.dp включается двухколоночная раскладка
    // (см. WIDE_LAYOUT_MIN_WIDTH), 880.dp — с небольшим запасом. Высота чуть
    // больше прежних 860.dp. Если сузить окно — вернётся телефонный вид.
    size = DpSize(880.dp, 900.dp),
)
