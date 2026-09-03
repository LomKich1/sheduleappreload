package com.schedule.app.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberJsonFilePicker(onFilePicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            onFilePicked(null)
            return@rememberLauncherForActivityResult
        }
        // Некоторые файловые менеджеры отдают .json с "чужим" mime-типом
        // (text/plain, application/octet-stream) — поэтому фильтр по типу при
        // запуске пикера ниже нарочно "*/*", а не "application/json", иначе
        // часть реальных JSON-файлов просто не показалась бы в списке.
        // Валидность самого содержимого как JSON проверяет уже вызывающий код.
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        onFilePicked(text)
    }
    return { launcher.launch("*/*") }
}
