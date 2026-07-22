package com.schedule.app.util

// На ПК нет logcat — просто печатаем в консоль (виден в терминале/в окне
// запуска). Формат намеренно похож на Android-лог, чтобы глазами было
// привычно искать.
actual object Logger {
    actual fun d(tag: String, msg: String) {
        println("D/$tag: $msg")
    }

    actual fun w(tag: String, msg: String, throwable: Throwable?) {
        println("W/$tag: $msg")
        throwable?.printStackTrace()
    }

    actual fun e(tag: String, msg: String, throwable: Throwable?) {
        System.err.println("E/$tag: $msg")
        throwable?.printStackTrace()
    }
}
