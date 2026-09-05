package com.schedule.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// The local JSON importer is a development-only tool and is hidden on iOS.
@Composable
actual fun rememberJsonFilePicker(onFilePicked: (PickedTextFile?) -> Unit): () -> Unit =
    remember { { onFilePicked(null) } }
