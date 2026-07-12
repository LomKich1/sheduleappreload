package com.schedule.app.data.model

// ─── Одна пара в расписании ───────────────────────────────────────────────────
//
//  startMin / endMin — суммарные минуты от полуночи для live-вычислений
//  (учитывают вторую полупару если есть перемена).
//  isNow / isNext / remainText / progressPct — НЕ хранятся здесь:
//  они вычисляются живо в ScheduleScreen через ScheduleViewModel.clockMin.

data class LessonEntry(
    val num: String,          // "I", "II" … "VI"
    val timeStart: String,    // "08:30"
    val timeEnd: String,      // "09:15"
    val breakStart: String?,  // "09:20"  (null если нет перемены)
    val breakEnd: String?,    // "10:05"
    val startMin: Int,        // 510   (8*60+30) — используется для live статуса
    val endMin: Int,          // 605   (10*60+5) — вторая полупара если есть
    val subject: String,      // "Математика (информатика)"
    val teacher: String?,     // "Петров В.С."
    val room: String?,        // "к.118(1)"
    val isWindow: Boolean,    // true — пустое «Окно»
)

// ─── День расписания ─────────────────────────────────────────────────────────

data class ScheduleDay(
    val header: String,               // "Вторник · 10.06.2025"
    val lessons: List<LessonEntry>,
    val isToday: Boolean,             // нужно для live-подсветки в ScheduleScreen
)

// ─── Результат парсинга файла ────────────────────────────────────────────────
// Группа может быть:
//  - найдена в обычной сетке расписания → Found
//  - в списке "На практике: ..." в конце файла → OnPractice
//  - не найдена вообще → NotFound

sealed interface ScheduleParseResult {
    data class Found(val day: ScheduleDay) : ScheduleParseResult
    data class OnPractice(val header: String) : ScheduleParseResult
    data object NotFound : ScheduleParseResult
}

// ─── Расписание ПРЕПОДАВАТЕЛЯ ────────────────────────────────────────────────
// Зеркало LessonEntry/ScheduleDay, но вместо преподавателя в паре хранится
// группа — именно она тут главная информация ("что вести и у кого").
// У одного преподавателя за день может быть несколько пар с одинаковым `num`
// (параллельные подгруппы одной группы или окно, ведущееся у двух групп
// одновременно) — поэтому это плоский список, а не Map<num, ...>.

data class TeacherLessonEntry(
    val num: String,          // "I", "II" … "VI"
    val timeStart: String,    // "08:30"
    val timeEnd: String,      // "09:15"
    val breakStart: String?,  // "09:20"  (null если нет перемены)
    val breakEnd: String?,    // "10:05"
    val startMin: Int,        // используется для live статуса, как в LessonEntry
    val endMin: Int,
    val group: String,        // "ЭМ-1-24"  — вместо teacher в обычном LessonEntry
    val subject: String,      // "Математика (информатика)"
    val room: String?,        // "к.118(1)"
)

data class TeacherDay(
    val header: String,                 // "Вторник · 10.06.2025"
    val lessons: List<TeacherLessonEntry>,
    val isToday: Boolean,
)

// ─── Результат парсинга файла для преподавателя ──────────────────────────────
// Практики тут нет: "на практике" — статус ГРУППЫ, а не преподавателя, у него
// в этот день просто не будет пар с этой группой (что покрывается NotFound,
// если пар вообще не нашлось).

sealed interface TeacherParseResult {
    data class Found(val day: TeacherDay) : TeacherParseResult
    data object NotFound : TeacherParseResult
}
