package com.schedule.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.io.File

@Composable
actual fun rememberJsonFilePicker(onFilePicked: (String?) -> Unit): () -> Unit {
    // FileDialog.isVisible = true — модальный вызов (блокирует до выбора
    // файла или отмены), это ожидаемое поведение для системного диалога,
    // ничего чинить не нужно.
    return remember {
        {
            val dialog = FileDialog(null as java.awt.Frame?, "Выбери JSON-файл", FileDialog.LOAD)
            dialog.file = "*.json"
            dialog.isVisible = true

            val dir  = dialog.directory
            val name = dialog.file
            if (dir == null || name == null) {
                onFilePicked(null)
            } else {
                onFilePicked(runCatching { File(dir, name).readText() }.getOrNull())
            }
        }
    }
}
