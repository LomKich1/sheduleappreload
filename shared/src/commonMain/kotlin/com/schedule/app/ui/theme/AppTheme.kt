package com.schedule.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
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
    val pillBg: Color,
    val pillActive: Color,
    val pillActiveText: Color,
    val pillInactiveText: Color,
    val todayAccent: Color,
    val todayBg: Color,
)

// ─── Тёмная (Android Dark) ────────────────────────────────────────────────────
// Нейтральные тёмно-серые тона без фиолетового оттенка + синий акцент,
// как в системных приложениях Android (Настройки, Messages, Phone).

val DarkColors = AppColors(
    bg             = Color(0xFF121212),   // Material Design dark baseline
    surface        = Color(0xFF1E1E1E),
    surface2       = Color(0xFF252525),
    surface3       = Color(0xFF2E2E2E),
    text           = Color(0xFFEEEEEE),
    textSub        = Color(0xFF999999),   // было 0x757575 — не проходило WCAG 4.5:1
                                           // (2.95:1 на surface3), теперь ~4.77-6.58:1
    accent         = Color(0xFF90CAF9),   // Material Blue 200 — стандарт тёмной темы
    border         = Color(0xFF3A3A3A),
    pillBg         = Color(0xFF1E1E1E),
    pillActive     = Color(0xFF2D3E52),   // dark blue tint
    pillActiveText = Color(0xFF90CAF9),
    pillInactiveText = Color(0xFF606060),
    todayAccent    = Color(0xFF64B5F6),   // Blue 300
    todayBg        = Color(0x1A64B5F6),
)

// ─── Светлая (Android Light) ──────────────────────────────────────────────────
// Чистые белые и светло-серые поверхности + синий акцент (#1976D2),
// как в светлой теме Google-приложений.

val LightColors = AppColors(
    bg             = Color(0xFFF2F2F2),
    surface        = Color(0xFFFFFFFF),
    surface2       = Color(0xFFF5F5F5),
    surface3       = Color(0xFFEEEEEE),
    text           = Color(0xFF212121),   // Material Grey 900
    textSub        = Color(0xFF757575),   // Material Grey 600
    accent         = Color(0xFF1976D2),   // Material Blue 700
    border         = Color(0xFFDEDEDE),
    pillBg         = Color(0xFFFFFFFF),
    pillActive     = Color(0xFFE3F2FD),   // Blue 50
    pillActiveText = Color(0xFF1565C0),   // Blue 800
    pillInactiveText = Color(0xFF9E9E9E),
    todayAccent    = Color(0xFF1976D2),
    todayBg        = Color(0x1A1976D2),
)

// ─── Монохром (AMOLED Mono) ───────────────────────────────────────────────────
// Чистый чёрный фон + серые поверхности + почти белый акцент.
// Ноль цвета — только градации серого. Идеально для AMOLED-экранов.

val AmoledColors = AppColors(
    bg             = Color(0xFF000000),
    surface        = Color(0xFF0F0F0F),
    surface2       = Color(0xFF1A1A1A),
    surface3       = Color(0xFF232323),
    text           = Color(0xFFF0F0F0),
    textSub        = Color(0xFF909090),   // было 0x646464 — не проходило WCAG 4.5:1
                                           // (2.66:1 на surface3), теперь ~4.92-6.58:1
    accent         = Color(0xFFDEDEDE),   // почти белый — монохром
    border         = Color(0xFF2C2C2C),
    pillBg         = Color(0xFF0F0F0F),
    pillActive     = Color(0xFF232323),
    pillActiveText = Color(0xFFFFFFFF),
    pillInactiveText = Color(0xFF4A4A4A),
    todayAccent    = Color(0xFFAAAAAA),   // серый вместо цветного
    todayBg        = Color(0x1AAAAAAA),
)

// ─── Enum тем ─────────────────────────────────────────────────────────────────

enum class ThemePreset(val label: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    AMOLED("Монохром"),
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
    val colors = colorsFor(preset)

    // В приложении нет MaterialTheme (своя система цветов через LocalAppColors),
    // поэтому раньше LocalIndication брал fallback-реализацию из чистого
    // Foundation — сплошную заливку всей формы при нажатии, без анимации
    // от точки касания. ripple() из material3 работает и без MaterialTheme —
    // подключаем его явно, чтобы "выделение при нажатии" было настоящим
    // анимированным кругом, идущим от места тапа (bounded=true по умолчанию).
    // Цвет — из текста темы: тёмный риппл на светлой теме, светлый на тёмной/
    // AMOLED, как и полагается по конвенции Material.
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalIndication provides ripple(color = colors.text),
    ) {
        content()
    }
}
