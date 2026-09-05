package com.schedule.app.util

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Current local wall-clock time, available on JVM, Android and Kotlin/Native. */
fun currentLocalDateTime(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun currentMinutesSinceMidnight(): Int {
    val now = currentLocalDateTime()
    return now.hour * 60 + now.minute
}
