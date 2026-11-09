package com.schedule.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

// ─── Единая точка настройки блюра (Haze 2.x) ──────────────────────────────────
//
//  Раньше в четырёх местах (AppScaffold, PickerScaffold ×2, DebugSettings)
//  повторялся Modifier.hazeChild(state, HazeStyle(backgroundColor, blurRadius,
//  HazeTint(...))). В Haze 2 этот API заменён на Modifier.hazeBlur с
//  неизменяемым HazeBlurStyle. Стиль кэшируется через remember — Haze
//  сравнивает стили по идентичности, и новый объект на каждую рекомпозицию
//  заставлял бы все ноды блюра пересоздаваться.
//
//  Источник блюра по-прежнему помечается Modifier.hazeSource(state)
//  (бывший Modifier.haze(state)).

@Composable
fun Modifier.glassBlur(
    state: HazeState,
    background: Color,
    tint: Color,
    radius: Dp = 20.dp,
): Modifier {
    val style = remember(background, tint, radius) {
        HazeBlurStyle {
            backgroundColor(background)
            blurRadius(radius)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
        }
    }
    return this.hazeBlur(
        input = HazeInput.Sources(state),
        style = style,
    )
}
