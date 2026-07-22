package com.schedule.app.util

// ─── Logger ───────────────────────────────────────────────────────────────
// android.util.Log существует только на Android, поэтому в общем (commonMain)
// коде вместо него используется этот expect-объект. Настоящая реализация
// (actual) лежит отдельно для каждой платформы:
//   • androidMain/util/Logger.android.kt — обычный android.util.Log
//   • desktopMain/util/Logger.desktop.kt — println в консоль
expect object Logger {
    fun d(tag: String, msg: String)
    fun w(tag: String, msg: String, throwable: Throwable? = null)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}
