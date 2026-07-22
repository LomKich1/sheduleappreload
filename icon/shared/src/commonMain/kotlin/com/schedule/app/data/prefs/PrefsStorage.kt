package com.schedule.app.data.prefs

// ─── PrefsStorage ─────────────────────────────────────────────────────────
// Низкоуровневое хранилище "ключ → значение", за которым прячется
// SharedPreferences на Android и java.util.prefs.Preferences на ПК.
// AppPrefs.kt (общая бизнес-логика настроек) работает только через этот
// интерфейс и не знает, какая платформа под ним.
//
// init(platformHandle) принимает Any? — на Android туда приходит
// android.content.Context (передаётся из MainActivity), на ПК параметр
// не нужен и просто игнорируется (передавайте null).
expect object PrefsStorage {
    fun init(platformHandle: Any?)

    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)

    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}
