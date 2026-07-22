package com.schedule.app.util

import android.util.Log

actual object Logger {
    actual fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    actual fun w(tag: String, msg: String, throwable: Throwable?) {
        if (throwable != null) Log.w(tag, msg, throwable) else Log.w(tag, msg)
    }

    actual fun e(tag: String, msg: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, msg, throwable) else Log.e(tag, msg)
    }
}
