package com.schedule.app.util

import androidx.compose.runtime.Composable

// iOS has no system back button; in-app back actions remain available in the UI.
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
