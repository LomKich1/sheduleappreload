package com.schedule.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Цветовая схема ───────────────────────────────────────────────────────────

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val text: Color,
    val textSub: Color,
    val accent: Color,
    val border: Color,
    // Плавающий пузырёк-навигация
    val pillBg: Color,
    val pillActive: Color,
    val pillActiveText: Color,
    val pillInactiveText: Color,
    // Подсветка «сегодня»
    val todayAccent: Color,
    val todayBg: Color,
)

// ─── Тёмная тема (по умолчанию) ──────────────────────────────────────────────

val DarkColors = AppColors(
    bg            = Color(0xFF0F0F17),
    surface       = Color(0xFF1A1A2E),
    surface2      = Color(0xFF1E1E3E),
    surface3      = Color(0xFF252545),
    text          = Color(0xFFE0E0F0),
    textSub       = Color(0xFF6666AA),
    accent        = Color(0xFF7777DD),
    border        = Color(0xFF2A2A5A),
    pillBg        = Color(0xFF1A1A2E),
    pillActive    = Color(0xFF3A3A8A),
    pillActiveText   = Color(0xFFC0C0FF),
    pillInactiveText = Color(0xFF555588),
    todayAccent   = Color(0xFF7777FF),
    todayBg       = Color(0x1A7777FF),
)

// ─── Светлая тема ─────────────────────────────────────────────────────────────

val LightColors = AppColors(
    bg            = Color(0xFFF4F4FF),
    surface       = Color(0xFFFFFFFF),
    surface2      = Color(0xFFF0F0FF),
    surface3      = Color(0xFFE8E8FF),
    text          = Color(0xFF1A1A3E),
    textSub       = Color(0xFF8888BB),
    accent        = Color(0xFF5050BB),
    border        = Color(0xFFDDDDFF),
    pillBg        = Color(0xFFFFFFFF),
    pillActive    = Color(0xFF5050BB),
    pillActiveText   = Color.White,
    pillInactiveText = Color(0xFF9999CC),
    todayAccent   = Color(0xFF5050FF),
    todayBg       = Color(0x1A5050FF),
)

// ─── AMOLED ───────────────────────────────────────────────────────────────────

val AmoledColors = DarkColors.copy(
    bg       = Color(0xFF000000),
    surface  = Color(0xFF0A0A18),
    surface2 = Color(0xFF0E0E20),
    surface3 = Color(0xFF141430),
    pillBg   = Color(0xFF0A0A18),
)

// ─── Enum тем ─────────────────────────────────────────────────────────────────

enum class ThemePreset(val label: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    AMOLED("AMOLED"),
}

fun colorsFor(preset: ThemePreset): AppColors = when (preset) {
    ThemePreset.DARK   -> DarkColors
    ThemePreset.LIGHT  -> LightColors
    ThemePreset.AMOLED -> AmoledColors
}

// ─── CompositionLocal ─────────────────────────────────────────────────────────

val LocalAppColors = staticCompositionLocalOf { DarkColors }

@Composable
fun AppTheme(
    preset: ThemePreset = ThemePreset.DARK,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppColors provides colorsFor(preset)) {
        content()
    }
}
