package com.schedule.app.util

import androidx.compose.runtime.Composable

/** Выбранный файл: имя (для scheduleFileFromName — определить день/дату) + текст содержимого. */
data class PickedTextFile(val name: String, val content: String)

/**
 * Возвращает функцию-лаунчер: вызови её, чтобы открыть системный диалог
 * выбора файла. Колбэк [onFilePicked] получает выбранный файл, либо null —
 * если пользователь отменил выбор или файл не прочитался.
 *
 * Только для debug-сборки: нужен, чтобы проверять JsonScheduleParser на
 * локальных тестовых файлах, не дожидаясь настоящего JSON-конструктора и не
 * заливая тестовые файлы на Я.Диск/GitHub. Вызывающий код сам отвечает за
 * проверку IsDebugBuild перед показом кнопки — сам пикер эту проверку не
 * делает (не его дело решать, показывать кнопку или нет).
 */
@Composable
expect fun rememberJsonFilePicker(onFilePicked: (PickedTextFile?) -> Unit): () -> Unit
