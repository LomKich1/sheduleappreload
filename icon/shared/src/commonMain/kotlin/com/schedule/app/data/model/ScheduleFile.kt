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
)
