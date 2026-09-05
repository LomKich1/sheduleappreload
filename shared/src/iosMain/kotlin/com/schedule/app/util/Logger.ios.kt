package com.schedule.app.util

actual object Logger {
    actual fun d(tag: String, msg: String) = println("D/$tag: $msg")
    actual fun w(tag: String, msg: String, throwable: Throwable?) =
        println("W/$tag: $msg${throwable?.let { " (${it.message})" }.orEmpty()}")
    actual fun e(tag: String, msg: String, throwable: Throwable?) =
        println("E/$tag: $msg${throwable?.let { " (${it.message})" }.orEmpty()}")
}
