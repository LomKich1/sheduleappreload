package com.schedule.app.util

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberJsonFilePicker(onFilePicked: (PickedTextFile?) -> Unit): () -> Unit {
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
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()

        // DISPLAY_NAME через ContentResolver — Uri сам по себе не всегда несёт
        // человекочитаемое имя (может быть content://.../123, без расширения
        // и без даты в пути), а без реального имени файла scheduleFileFromName
        // не сможет понять, какой это день недели/дата.
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment

        if (content == null || name == null) {
            onFilePicked(null)
        } else {
            onFilePicked(PickedTextFile(name = name, content = content))
        }
    }
    return { launcher.launch("*/*") }
}
