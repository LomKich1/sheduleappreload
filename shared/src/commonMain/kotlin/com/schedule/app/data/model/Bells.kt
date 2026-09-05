package com.schedule.app.data.model

// ─── Bells ────────────────────────────────────────────────────────────────────
// Единый справочник времени звонков. Пока используется только JsonScheduleParser
// (JSON не хранит время каждой пары — только номер, время берётся отсюда по дню
// недели). DocParser.kt и BellsScreen.kt пока держат СВОИ отдельные копии тех же
// данных (исторически, до появления этого файла) — трогать их сейчас не будем,
// но в перспективе стоит перевести и их сюда же, чтобы времена звонков менялись
// в одном месте, а не в трёх.

data class BellPeriod(
    val num: String,          // "I", "II" … "VI"
    val start: String,        // "08:30"
    val end: String,          // "09:15"
    val breakStart: String?,  // "09:20"  (null если нет перемены)
    val breakEnd: String?,    // "10:05"
)

object Bells {
    val MON = listOf(
        BellPeriod("I",   "09:00", "09:45", "09:50", "10:35"),
        BellPeriod("II",  "10:45", "11:30", "11:35", "12:20"),
        BellPeriod("III", "12:40", "13:25", "13:30", "14:15"),
        BellPeriod("IV",  "14:25", "15:10", "15:15", "16:00"),
        BellPeriod("V",   "16:10", "16:55", "17:00", "17:45"),
        BellPeriod("VI",  "17:55", "18:40", "18:45", "19:30"),
    )
    val TUE_SAT = listOf(
        BellPeriod("I",   "08:30", "09:15", "09:20", "10:05"),
        BellPeriod("II",  "10:15", "11:00", "11:05", "11:50"),
        BellPeriod("III", "12:10", "12:55", "13:00", "13:45"),
        BellPeriod("IV",  "13:55", "14:40", "14:45", "15:30"),
        BellPeriod("V",   "15:40", "16:25", "16:30", "17:15"),
        BellPeriod("VI",  "17:25", "18:10", "18:15", "19:00"),
    )

    /** [dayOfWeek] is ISO-8601: Monday = 1 through Sunday = 7. */
    fun forWeekday(dayOfWeek: Int): List<BellPeriod> =
        if (dayOfWeek == 1) MON else TUE_SAT
}
