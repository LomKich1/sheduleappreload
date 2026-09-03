package com.schedule.app.data.parser

import com.schedule.app.data.model.Bells
import com.schedule.app.data.model.LessonEntry
import com.schedule.app.data.model.ScheduleDay
import com.schedule.app.data.model.ScheduleParseResult
import com.schedule.app.data.model.TeacherDay
import com.schedule.app.data.model.TeacherLessonEntry
import com.schedule.app.data.model.TeacherParseResult
import org.json.JSONObject

// ─── JsonScheduleParser ─────────────────────────────────────────────────────
// Параллельный путь к DocParser: читает JSON-файл дня вместо бинарного .doc.
// Пока используется ТОЛЬКО в debug-сборке (см. IsDebugBuild в вызывающем коде
// ViewModel) — формат генерируется будущим JSON-конструктором для препода,
// которого ещё нет, поэтому схема ниже не "черновик под утверждение", а просто
// то, что мы сами спроектировали и вольны менять по ходу дела.
//
// Формат файла (один файл = один день, как и .doc сейчас):
//
// {
//   "practiceGroups": ["1С-1-24", "2С-1-24"],
//   "groups": {
//     "Б-1-26": [
//       {"num": "I", "subject": "Русский язык", "teacher": "Макатова Е.И.", "room": "к.25"},
//       {"num": "II", "window": true}
//     ],
//     "1СР-1-26": [ ... ]
//   }
// }
//
// Осознанные отличия от .doc-формата (все — прямое следствие того, что этот
// JSON пишет НАША программа-конструктор, а не человек руками в Word):
//
// 1. Время каждой пары (start/end/break) в файле не хранится вообще — берётся
//    из Bells.forWeekday() по дню недели. В .doc так пришлось бы городить
//    отдельный парсинг, здесь просто не о чем рассинхронизироваться: звонки
//    меняются раз в год в одном файле, а не путём правки N файлов дней.
// 2. "Окно" — явное поле "window": true, а не угадывается по пустому тексту
//    ячейки. Никакой матрицы случаев вида "текст есть, но это на самом деле
//    не пара" — как было в DocParser.
// 3. group/teacher — раз конструктор будет давать их выбором из справочника
//    (а не свободным вводом на экране расписания), сравнение точное, без
//    normalize()-костылей на неразрывные пробелы и опечатки — см. историю
//    багов DocParser.normalize() за сегодня.

object JsonScheduleParser {

    fun parseForGroup(
        json: String,
        group: String,
        header: String,
        isToday: Boolean,
        weekday: Int,
    ): ScheduleParseResult {
        val root = JSONObject(json)
        val normGroup = group.trim().uppercase()

        val practiceGroups = root.optJSONArray("practiceGroups")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it).trim().uppercase() }.toSet()
        } ?: emptySet()
        if (normGroup in practiceGroups) return ScheduleParseResult.OnPractice(header)

        val groupsObj = root.optJSONObject("groups") ?: return ScheduleParseResult.NotFound
        val matchedKey = groupsObj.keys().asSequence()
            .firstOrNull { it.trim().uppercase() == normGroup }
            ?: return ScheduleParseResult.NotFound

        val bells = Bells.forWeekday(weekday)
        val lessonsArr = groupsObj.getJSONArray(matchedKey)
        val lessons = (0 until lessonsArr.length()).map { i ->
            val o = lessonsArr.getJSONObject(i)
            val num = o.getString("num")
            val period = bells.find { it.num == num }
            val isWindow = o.optBoolean("window", false)

            LessonEntry(
                num        = num,
                timeStart  = period?.start ?: "",
                timeEnd    = period?.end ?: "",
                breakStart = period?.breakStart,
                breakEnd   = period?.breakEnd,
                startMin   = period?.let { toMin(it.start) } ?: 0,
                endMin     = period?.let { p -> toMin(p.breakEnd ?: p.end) } ?: 0,
                subject    = if (isWindow) "" else o.optString("subject", ""),
                teacher    = if (isWindow) null else o.optString("teacher", "").ifBlank { null },
                room       = if (isWindow) null else o.optString("room", "").ifBlank { null },
                isWindow   = isWindow,
            )
        }

        return ScheduleParseResult.Found(ScheduleDay(header = header, lessons = lessons, isToday = isToday))
    }

    fun parseForTeacher(
        json: String,
        teacherName: String,
        header: String,
        isToday: Boolean,
        weekday: Int,
    ): TeacherParseResult {
        val root = JSONObject(json)
        val groupsObj = root.optJSONObject("groups") ?: return TeacherParseResult.NotFound
        val bells = Bells.forWeekday(weekday)
        val normTeacher = teacherName.trim().uppercase()

        val lessons = mutableListOf<TeacherLessonEntry>()
        for (groupKey in groupsObj.keys()) {
            val lessonsArr = groupsObj.getJSONArray(groupKey)
            for (i in 0 until lessonsArr.length()) {
                val o = lessonsArr.getJSONObject(i)
                if (o.optBoolean("window", false)) continue

                val teacher = o.optString("teacher", "")
                if (teacher.trim().uppercase() != normTeacher) continue

                val num = o.getString("num")
                val period = bells.find { it.num == num }

                lessons += TeacherLessonEntry(
                    num        = num,
                    timeStart  = period?.start ?: "",
                    timeEnd    = period?.end ?: "",
                    breakStart = period?.breakStart,
                    breakEnd   = period?.breakEnd,
                    startMin   = period?.let { toMin(it.start) } ?: 0,
                    endMin     = period?.let { p -> toMin(p.breakEnd ?: p.end) } ?: 0,
                    group      = groupKey,
                    subject    = o.optString("subject", ""),
                    room       = o.optString("room", "").ifBlank { null },
                )
            }
        }

        // Пары собирались по порядку групп в JSON (см. цикл выше), а не по
        // времени — отсюда "скачущие" номера пар в UI. Группы, которые
        // сходятся у препода на одном и том же номере пары (ведёт несколько
        // групп параллельно), это не дубликат, а два реальных занятия —
        // сортировка просто ставит их рядом в правильном месте по времени.
        val sortedLessons = lessons.sortedBy { it.startMin }

        return if (sortedLessons.isNotEmpty()) {
            TeacherParseResult.Found(TeacherDay(header = header, lessons = sortedLessons, isToday = isToday))
        } else {
            TeacherParseResult.NotFound
        }
    }

    /** Список всех групп, упомянутых в файле (для пикера — аналог DocParser.detectGroups). */
    fun detectGroups(json: String): List<String> {
        val root = JSONObject(json)
        val groupsObj = root.optJSONObject("groups") ?: return emptyList()
        val practiceGroups = root.optJSONArray("practiceGroups")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        return (groupsObj.keys().asSequence().toList() + practiceGroups).distinct().sorted()
    }

    /** Список всех преподавателей, упомянутых в файле (аналог DocParser.detectTeachers). */
    fun detectTeachers(json: String): List<String> {
        val root = JSONObject(json)
        val groupsObj = root.optJSONObject("groups") ?: return emptyList()
        val names = linkedSetOf<String>()
        for (groupKey in groupsObj.keys()) {
            val lessonsArr = groupsObj.getJSONArray(groupKey)
            for (i in 0 until lessonsArr.length()) {
                val o = lessonsArr.getJSONObject(i)
                if (o.optBoolean("window", false)) continue
                val teacher = o.optString("teacher", "").trim()
                if (teacher.isNotBlank()) names += teacher
            }
        }
        return names.sorted()
    }

    private fun toMin(t: String): Int {
        val parts = t.split(':')
        if (parts.size < 2) return 0
        return (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
    }
}
