package com.schedule.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// ─── Индикатор загрузки Material 3 Expressive ─────────────────────────────────
//
//  Официальный LoadingIndicator из material3 (фигура морфится из одной формы в
//  другую и вращается). Раньше здесь был самописный аналог — после перехода на
//  Compose Multiplatform 1.12 (material3 1.5.x) он больше не нужен.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
) {
    LoadingIndicator(modifier = modifier, color = color)
}

/** Индикатор по центру выделенной области — для экранов в состоянии Loading. */
@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AppLoadingIndicator()
    }
}
