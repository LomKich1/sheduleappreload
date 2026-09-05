package com.schedule.app.data.parser

import com.schedule.app.data.model.Bells
import com.schedule.app.data.model.LessonEntry
import com.schedule.app.data.model.ScheduleDay
import com.schedule.app.data.model.ScheduleParseResult
import com.schedule.app.data.model.TeacherDay
import com.schedule.app.data.model.TeacherLessonEntry
import com.schedule.app.data.model.TeacherParseResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ─── JsonScheduleParser ─────────────────────────────────────────────────────
// This parser intentionally uses kotlinx.serialization's tree API rather than
// JVM-only org.json so the same debug JSON fixtures work on iOS.
object JsonScheduleParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseForGroup(
        json: String,
        group: String,
        header: String,
        isToday: Boolean,
        weekday: Int,
    ): ScheduleParseResult {
        val root = rootObject(json)
        val normalizedGroup = group.trim().uppercase()
        val practiceGroups = root.array("practiceGroups")
            .map { it.jsonPrimitive.contentOrNull.orEmpty().trim().uppercase() }
            .toSet()
        if (normalizedGroup in practiceGroups) return ScheduleParseResult.OnPractice(header)

        val groups = root.objectOrNull("groups") ?: return ScheduleParseResult.NotFound
        val matchedKey = groups.keys.firstOrNull { it.trim().uppercase() == normalizedGroup }
            ?: return ScheduleParseResult.NotFound
        val lessons = groups.array(matchedKey).map { lesson ->
            val item = lesson.jsonObject
            val number = item.requiredString("num")
            val period = Bells.forWeekday(weekday).find { it.num == number }
            val isWindow = item.boolean("window")
            LessonEntry(
                num = number,
                timeStart = period?.start.orEmpty(),
                timeEnd = period?.end.orEmpty(),
                breakStart = period?.breakStart,
                breakEnd = period?.breakEnd,
                startMin = period?.let { toMin(it.start) } ?: 0,
                endMin = period?.let { toMin(it.breakEnd ?: it.end) } ?: 0,
                subject = if (isWindow) "" else item.string("subject"),
                teacher = if (isWindow) null else item.string("teacher").ifBlank { null },
                room = if (isWindow) null else item.string("room").ifBlank { null },
                isWindow = isWindow,
            )
        }
        return ScheduleParseResult.Found(ScheduleDay(header, lessons, isToday))
    }

    fun parseForTeacher(
        json: String,
        teacherName: String,
        header: String,
        isToday: Boolean,
        weekday: Int,
    ): TeacherParseResult {
        val groups = rootObject(json).objectOrNull("groups") ?: return TeacherParseResult.NotFound
        val normalizedTeacher = teacherName.trim().uppercase()
        val bells = Bells.forWeekday(weekday)
        val lessons = buildList {
            for ((group, value) in groups) {
                for (lesson in value.jsonArray) {
                    val item = lesson.jsonObject
                    if (item.boolean("window")) continue
                    if (item.string("teacher").trim().uppercase() != normalizedTeacher) continue
                    val number = item.requiredString("num")
                    val period = bells.find { it.num == number }
                    add(
                        TeacherLessonEntry(
                            num = number,
                            timeStart = period?.start.orEmpty(),
                            timeEnd = period?.end.orEmpty(),
                            breakStart = period?.breakStart,
                            breakEnd = period?.breakEnd,
                            startMin = period?.let { toMin(it.start) } ?: 0,
                            endMin = period?.let { toMin(it.breakEnd ?: it.end) } ?: 0,
                            group = group,
                            subject = item.string("subject"),
                            room = item.string("room").ifBlank { null },
                        ),
                    )
                }
            }
        }.sortedBy { it.startMin }
        return if (lessons.isEmpty()) TeacherParseResult.NotFound
        else TeacherParseResult.Found(TeacherDay(header, lessons, isToday))
    }

    fun detectGroups(json: String): List<String> {
        val root = rootObject(json)
        val groups = root.objectOrNull("groups") ?: return emptyList()
        return (groups.keys + root.array("practiceGroups").map { it.jsonPrimitive.contentOrNull.orEmpty() })
            .distinct()
            .sorted()
    }

    fun detectTeachers(json: String): List<String> {
        val groups = rootObject(json).objectOrNull("groups") ?: return emptyList()
        return buildSet {
            for ((_, value) in groups) {
                for (lesson in value.jsonArray) {
                    val item = lesson.jsonObject
                    if (!item.boolean("window")) item.string("teacher").trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }.sorted()
    }

    private fun rootObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject
    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key]?.jsonObject
    private fun JsonObject.array(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.requiredString(key: String): String =
        string(key).takeIf { it.isNotEmpty() } ?: error("Поле '$key' не заполнено")
    private fun JsonObject.boolean(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false

    private fun toMin(value: String): Int {
        val parts = value.split(':')
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
