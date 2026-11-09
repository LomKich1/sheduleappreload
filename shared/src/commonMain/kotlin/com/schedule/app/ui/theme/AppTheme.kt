package com.schedule.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// ─── Material 3 тема ──────────────────────────────────────────────────────────
//
//  Источник правды теперь — M3 ColorScheme (три схемы ниже). Старый класс
//  AppColors / LocalAppColors СОХРАНЁН как тонкий слой совместимости: он
//  строится из ColorScheme (см. AppColors.from), поэтому экраны, которые ещё
//  читают c.bg / c.surface / c.accent, автоматически получают палитру M3.
//  Новый код должен использовать MaterialTheme.colorScheme напрямую.
//
//  Соответствие старых токенов → токены M3:
//    bg        → background
//    surface   → surfaceContainerLow
//    surface2  → surfaceContainerHigh
//    surface3  → surfaceContainerHighest
//    text      → onSurface
//    textSub   → onSurfaceVariant
//    accent    → primary          (onAccent → onPrimary)
//    border    → outlineVariant
//    pillBg    → surfaceContainer
//    pillActive/pillActiveText → secondaryContainer / onSecondaryContainer
//    todayAccent / todayBg     → tertiary / tertiary с прозрачностью

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val text: Color,
    val textSub: Color,
    val accent: Color,
    val onAccent: Color,
    val border: Color,
    val pillBg: Color,
    val pillActive: Color,
    val pillActiveText: Color,
    val pillInactiveText: Color,
    val todayAccent: Color,
    val todayBg: Color,
) {
    companion object {
        fun from(s: ColorScheme) = AppColors(
            bg               = s.background,
            surface          = s.surfaceContainerLow,
            surface2         = s.surfaceContainerHigh,
            surface3         = s.surfaceContainerHighest,
            text             = s.onSurface,
            textSub          = s.onSurfaceVariant,
            accent           = s.primary,
            onAccent         = s.onPrimary,
            border           = s.outlineVariant,
            pillBg           = s.surfaceContainer,
            pillActive       = s.secondaryContainer,
            pillActiveText   = s.onSecondaryContainer,
            pillInactiveText = s.onSurfaceVariant,
            todayAccent      = s.tertiary,
            todayBg          = s.tertiary.copy(alpha = 0.10f),
        )
    }
}

// ─── Тёмная (M3 baseline, синий seed) ─────────────────────────────────────────

val DarkScheme: ColorScheme = darkColorScheme(
    primary                = Color(0xFFAAC7FF),
    onPrimary              = Color(0xFF0A305F),
    primaryContainer       = Color(0xFF284777),
    onPrimaryContainer     = Color(0xFFD6E3FF),
    inversePrimary         = Color(0xFF415F91),
    secondary              = Color(0xFFBEC6DC),
    onSecondary            = Color(0xFF283141),
    secondaryContainer     = Color(0xFF3E4759),
    onSecondaryContainer   = Color(0xFFDAE2F9),
    tertiary               = Color(0xFFDEBCDF),
    onTertiary             = Color(0xFF402843),
    tertiaryContainer      = Color(0xFF583E5B),
    onTertiaryContainer    = Color(0xFFFBD7FC),
    background             = Color(0xFF111318),
    onBackground           = Color(0xFFE2E2E9),
    surface                = Color(0xFF111318),
    onSurface              = Color(0xFFE2E2E9),
    surfaceVariant         = Color(0xFF44474E),
    onSurfaceVariant       = Color(0xFFC4C6D0),
    surfaceTint            = Color(0xFFAAC7FF),
    inverseSurface         = Color(0xFFE2E2E9),
    inverseOnSurface       = Color(0xFF2F3036),
    error                  = Color(0xFFFFB4AB),
    onError                = Color(0xFF690005),
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),
    outline                = Color(0xFF8E9099),
    outlineVariant         = Color(0xFF44474E),
    scrim                  = Color(0xFF000000),
    surfaceBright          = Color(0xFF37393E),
    surfaceDim             = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow    = Color(0xFF191C20),
    surfaceContainer       = Color(0xFF1D2024),
    surfaceContainerHigh   = Color(0xFF282A2F),
    surfaceContainerHighest= Color(0xFF33353A),
)

// ─── Светлая (M3 baseline, синий seed) ────────────────────────────────────────

val LightScheme: ColorScheme = lightColorScheme(
    primary                = Color(0xFF415F91),
    onPrimary              = Color(0xFFFFFFFF),
    primaryContainer       = Color(0xFFD6E3FF),
    onPrimaryContainer     = Color(0xFF001B3E),
    inversePrimary         = Color(0xFFAAC7FF),
    secondary              = Color(0xFF565F71),
    onSecondary            = Color(0xFFFFFFFF),
    secondaryContainer     = Color(0xFFDAE2F9),
    onSecondaryContainer   = Color(0xFF131C2B),
    tertiary               = Color(0xFF705575),
    onTertiary             = Color(0xFFFFFFFF),
    tertiaryContainer      = Color(0xFFFAD8FD),
    onTertiaryContainer    = Color(0xFF28132E),
    background             = Color(0xFFF9F9FF),
    onBackground           = Color(0xFF1A1B20),
    surface                = Color(0xFFF9F9FF),
    onSurface              = Color(0xFF1A1B20),
    surfaceVariant         = Color(0xFFE0E2EC),
    onSurfaceVariant       = Color(0xFF44474E),
    surfaceTint            = Color(0xFF415F91),
    inverseSurface         = Color(0xFF2F3036),
    inverseOnSurface       = Color(0xFFF0F0F7),
    error                  = Color(0xFFBA1A1A),
    onError                = Color(0xFFFFFFFF),
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),
    outline                = Color(0xFF74777F),
    outlineVariant         = Color(0xFFC4C6D0),
    scrim                  = Color(0xFF000000),
    surfaceBright          = Color(0xFFF9F9FF),
    surfaceDim             = Color(0xFFD9D9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow    = Color(0xFFF3F3FA),
    surfaceContainer       = Color(0xFFEDEDF4),
    surfaceContainerHigh   = Color(0xFFE7E8EE),
    surfaceContainerHighest= Color(0xFFE2E2E9),
)

// ─── Монохром / AMOLED (чёрный фон, серые тона) ───────────────────────────────
// Ноль цвета — только градации серого, фон чисто чёрный.

val AmoledScheme: ColorScheme = darkColorScheme(
    primary                = Color(0xFFE2E2E2),
    onPrimary              = Color(0xFF1A1A1A),
    primaryContainer       = Color(0xFF303030),
    onPrimaryContainer     = Color(0xFFE6E6E6),
    inversePrimary         = Color(0xFF474747),
    secondary              = Color(0xFFC6C6C6),
    onSecondary            = Color(0xFF2A2A2A),
    secondaryContainer     = Color(0xFF2E2E2E),
    onSecondaryContainer   = Color(0xFFE2E2E2),
    tertiary               = Color(0xFFB5B5B5),
    onTertiary             = Color(0xFF1F1F1F),
    tertiaryContainer      = Color(0xFF3A3A3A),
    onTertiaryContainer    = Color(0xFFE8E8E8),
    background             = Color(0xFF000000),
    onBackground           = Color(0xFFE6E6E6),
    surface                = Color(0xFF000000),
    onSurface              = Color(0xFFE6E6E6),
    surfaceVariant         = Color(0xFF2A2A2A),
    onSurfaceVariant       = Color(0xFFB0B0B0),
    surfaceTint            = Color(0xFFE2E2E2),
    inverseSurface         = Color(0xFFE6E6E6),
    inverseOnSurface       = Color(0xFF1A1A1A),
    error                  = Color(0xFFFFB4AB),
    onError                = Color(0xFF690005),
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),
    outline                = Color(0xFF7A7A7A),
    outlineVariant         = Color(0xFF2E2E2E),
    scrim                  = Color(0xFF000000),
    surfaceBright          = Color(0xFF2A2A2A),
    surfaceDim             = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow    = Color(0xFF0D0D0D),
    surfaceContainer       = Color(0xFF141414),
    surfaceContainerHigh   = Color(0xFF1C1C1C),
    surfaceContainerHighest= Color(0xFF262626),
)

// ─── Цветные тёмные темы по трём опорным цветам ───────────────────────────────
//
//  Для каждой темы задано три цвета: Primary (акцент), Background (фон) и
//  Surface (карточки). Остальные роли M3 (контейнеры, on-цвета, outline,
//  градация surfaceContainer*) строятся из них в darkSchemeFrom — в HSL,
//  с тем же оттенком, что у акцента/фона.

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val hh = (((h % 360f) + 360f) % 360f) / 360f
    val ss = s.coerceIn(0f, 1f)
    val ll = l.coerceIn(0f, 1f)
    if (ss == 0f) return Color(ll, ll, ll)
    val q = if (ll < 0.5f) ll * (1f + ss) else ll + ss - ll * ss
    val p = 2f * ll - q
    fun channel(t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else        -> p
        }
    }
    return Color(channel(hh + 1f / 3f), channel(hh), channel(hh - 1f / 3f))
}

/** [hue (0..360), saturation (0..1), lightness (0..1)] */
private fun Color.toHsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } / 6f * 360f
    return floatArrayOf(h, s, l)
}

private fun darkSchemeFrom(primary: Color, background: Color, surface: Color): ColorScheme {
    val ph = primary.toHsl()
    val h = ph[0]
    val s = ph[1]
    val bh = background.toHsl()[0]

    val onSurface = hslColor(bh, 0.10f, 0.90f)
    val onSurfaceVariant = hslColor(bh, 0.08f, 0.76f)
    val outline = hslColor(bh, 0.06f, 0.56f)
    val outlineVariant = lerp(surface, onSurface, 0.20f)

    return darkColorScheme(
        primary                 = primary,
        onPrimary               = hslColor(h, s * 0.90f, 0.16f),
        primaryContainer        = hslColor(h, s * 0.55f, 0.30f),
        onPrimaryContainer      = hslColor(h, s, 0.90f),
        inversePrimary          = hslColor(h, s * 0.80f, 0.40f),
        secondary               = hslColor(h, s * 0.30f, 0.78f),
        onSecondary             = hslColor(h, s * 0.30f, 0.17f),
        secondaryContainer      = hslColor(h, s * 0.22f, 0.27f),
        onSecondaryContainer    = hslColor(h, s * 0.40f, 0.90f),
        tertiary                = hslColor(h + 55f, s * 0.45f, 0.80f),
        onTertiary              = hslColor(h + 55f, s * 0.45f, 0.18f),
        tertiaryContainer       = hslColor(h + 55f, s * 0.30f, 0.28f),
        onTertiaryContainer     = hslColor(h + 55f, s * 0.50f, 0.90f),
        background              = background,
        onBackground            = onSurface,
        surface                 = background,
        onSurface               = onSurface,
        surfaceVariant          = lerp(surface, onSurface, 0.16f),
        onSurfaceVariant        = onSurfaceVariant,
        surfaceTint             = primary,
        inverseSurface          = onSurface,
        inverseOnSurface        = background,
        error                   = Color(0xFFFFB4AB),
        onError                 = Color(0xFF690005),
        errorContainer          = Color(0xFF93000A),
        onErrorContainer        = Color(0xFFFFDAD6),
        outline                 = outline,
        outlineVariant          = outlineVariant,
        scrim                   = Color.Black,
        surfaceBright           = lerp(surface, onSurface, 0.10f),
        surfaceDim              = background,
        surfaceContainerLowest  = lerp(background, Color.Black, 0.35f),
        surfaceContainerLow     = lerp(background, surface, 0.50f),
        surfaceContainer        = surface,
        surfaceContainerHigh    = lerp(surface, onSurface, 0.07f),
        surfaceContainerHighest = lerp(surface, onSurface, 0.14f),
    )
}

private val EmbermireScheme   = darkSchemeFrom(Color(0xFFF5B09C), Color(0xFF231B1A), Color(0xFF2E2524))
private val VelvetRoseScheme  = darkSchemeFrom(Color(0xFFEEB0CB), Color(0xFF231A20), Color(0xFF2F2329))
private val MistyWaveScheme   = darkSchemeFrom(Color(0xFF96E5E3), Color(0xFF1A1F22), Color(0xFF232A2E))
private val GlacierScheme     = darkSchemeFrom(Color(0xFF94D3EE), Color(0xFF1B1F24), Color(0xFF242930))
private val GreenDuskScheme   = darkSchemeFrom(Color(0xFFE2E27C), Color(0xFF211F17), Color(0xFF2B2920))
private val UrbanGrayScheme   = darkSchemeFrom(Color(0xFFC9C6C9), Color(0xFF1B1B1D), Color(0xFF262628))
private val SilentDawnScheme  = darkSchemeFrom(Color(0xFF88D89C), Color(0xFF18201B), Color(0xFF222B25))
private val SkyScheme         = darkSchemeFrom(Color(0xFF58C4DC), Color(0xFF1A1C20), Color(0xFF24262B))

// ─── Enum тем ─────────────────────────────────────────────────────────────────
// Значения сохраняются в настройках по имени (AppPrefs) — существующие имена
// DARK / LIGHT / AMOLED не менять.

enum class ThemePreset(val label: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    AMOLED("Монохром"),
    EMBERMIRE("Эмбермайр"),
    VELVET_ROSE("Бархатная роза"),
    MISTY_WAVE("Туманная волна"),
    GLACIER("Ледник"),
    GREEN_DUSK("Зелёные сумерки"),
    URBAN_GRAY("Городской серый"),
    SILENT_DAWN("Безмолвный рассвет"),
    SKY("Голубая"),
}

fun colorSchemeFor(preset: ThemePreset): ColorScheme = when (preset) {
    ThemePreset.DARK        -> DarkScheme
    ThemePreset.LIGHT       -> LightScheme
    ThemePreset.AMOLED      -> AmoledScheme
    ThemePreset.EMBERMIRE   -> EmbermireScheme
    ThemePreset.VELVET_ROSE -> VelvetRoseScheme
    ThemePreset.MISTY_WAVE  -> MistyWaveScheme
    ThemePreset.GLACIER     -> GlacierScheme
    ThemePreset.GREEN_DUSK  -> GreenDuskScheme
    ThemePreset.URBAN_GRAY  -> UrbanGrayScheme
    ThemePreset.SILENT_DAWN -> SilentDawnScheme
    ThemePreset.SKY         -> SkyScheme
}

// Совместимость со старым кодом.
fun colorsFor(preset: ThemePreset): AppColors = AppColors.from(colorSchemeFor(preset))

val DarkColors   = AppColors.from(DarkScheme)
val LightColors  = AppColors.from(LightScheme)
val AmoledColors = AppColors.from(AmoledScheme)

// ─── CompositionLocal ─────────────────────────────────────────────────────────

val LocalAppColors = staticCompositionLocalOf { DarkColors }

@Composable
fun AppTheme(
    preset: ThemePreset = ThemePreset.DARK,
    content: @Composable () -> Unit,
) {
    val scheme = colorSchemeFor(preset)
    val appColors = remember(preset) { AppColors.from(scheme) }

    // MaterialTheme сам выставляет ripple (LocalIndication), шрифты и формы M3.
    // Surface задаёт фон и LocalContentColor: Text/Icon без явного цвета
    // теперь красятся в onSurface, а не в чёрный.
    MaterialTheme(
        colorScheme = scheme,
        typography  = AppTypography,
        shapes      = AppShapes,
    ) {
        CompositionLocalProvider(LocalAppColors provides appColors) {
            Surface(
                modifier     = Modifier.fillMaxSize(),
                color        = scheme.background,
                contentColor = scheme.onBackground,
                content      = content,
            )
        }
    }
}
