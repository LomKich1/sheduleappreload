package com.schedule.app.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Порог "широкого" окна (ПК, планшет в ландшафте, раскрытый складной экран).
// 840.dp — граница Expanded в Material window size classes.
val WIDE_LAYOUT_MIN_WIDTH = 840.dp

// true — в окне хватает места показать два экрана рядом (две колонки).
// Выставляется один раз в AppScaffold, читается любым экраном ниже по дереву.
val LocalWideLayout = compositionLocalOf { false }

// Ограничивает ширину контента в широком окне (ПК): настройки и подобные экраны
// не должны растягиваться на весь монитор. На телефоне ничего не меняет.
@Composable
fun Modifier.wideContentWidth(max: Dp = 640.dp): Modifier =
    if (LocalWideLayout.current) this.widthIn(max = max) else this
