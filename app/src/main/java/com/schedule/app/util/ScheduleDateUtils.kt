package com.schedule.app.util

import com.schedule.app.data.model.ScheduleFile
import java.time.LocalDate
import java.util.Locale

// ─── Разбор имени файла "26.06.2026 ПЯТНИЦА.doc" ────────────────────────────
// Реальный формат файлов с Я.Диска: dd.MM.yyyy ДЕНЬ.doc (точки + пробел)

private val FILE_NAME_REGEX = Regex(
    """^(\d{1,2})\.(\d{1,2})\.(\d{4})\s+(\S+)$""",
)

private val WEEKDAY_RU = mapOf(
    "ПОНЕДЕЛЬНИК" to "Понедельник",
    "ВТОРНИК"     to "Вторник",
    "СРЕДА"       to "Среда",
    "ЧЕТВЕРГ"     to "Четверг",
    "ПЯТНИЦА"     to "Пятница",
    "СУББОТА"     to "Субботa",
)

/**
 * Превращает имя файла в [ScheduleFile].
 * Возвращает null, если имя не соответствует шаблону "dd_MM_yyyy_ДЕНЬ.*".
 */
fun scheduleFileFromName(name: String, downloadUrl: String): ScheduleFile? {
    val withoutExt = name.substringBeforeLast('.')
    val match = FILE_NAME_REGEX.find(withoutExt) ?: return null

    val day   = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val year  = match.groupValues[3].toIntOrNull() ?: return null
    val rawDay = match.groupValues[4].uppercase(Locale.ROOT)

    val dayLabel  = WEEKDAY_RU[rawDay] ?: rawDay.lowercase(Locale.ROOT)
        .replaceFirstChar { it.uppercaseChar() }
    val dateLabel = "${day.toString().padStart(2, '0')}.${month.toString().padStart(2, '0')}"

    val today = LocalDate.now()
    val isToday = day == today.dayOfMonth && month == today.monthValue && year == today.year

    return ScheduleFile(
        name        = name,
        dayLabel    = dayLabel,
        dateLabel   = dateLabel,
        downloadUrl = downloadUrl,
        isToday     = isToday,
    )
}
