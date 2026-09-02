package com.schedule.app.data.model

// ─── Файл расписания с Я.Диска / GitHub ──────────────────────────────────────
// Имя файла вида "Понедельник 09.06.doc" — день недели + дата разобраны заранее,
// чтобы карточке не нужно было ничего парсить самой.

data class ScheduleFile(
    val name: String,        // оригинальное имя, напр. "Понедельник 09.06.doc"
    val dayLabel: String,     // "Понедельник"
    val dateLabel: String,    // "09.06"
    val downloadUrl: String,  // прямая ссылка на скачивание файла
    val isToday: Boolean,
    val sha: String = "",     // git blob SHA1 (только для файлов с GitHub — см. GitHubApi),
                               // используется для проверки целостности после скачивания
                               // через зеркала. У файлов с Я.Диска — пустая строка.
)
