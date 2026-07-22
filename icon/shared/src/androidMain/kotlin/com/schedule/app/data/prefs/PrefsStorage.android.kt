package com.schedule.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

private const val PREFS_NAME = "schedule_app_prefs"

actual object PrefsStorage {

    private var prefs: SharedPreferences? = null

    // ВАЖНО: вызывается один раз из MainActivity.onCreate() ДО setContent{},
    // с applicationContext. Пока не вызван — просто отдаём default и молча
    // игнорируем запись (например, во время @Preview).
    actual fun init(platformHandle: Any?) {
        if (prefs != null) return
        val context = platformHandle as? Context ?: return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun getString(key: String, default: String): String =
        prefs?.getString(key, default) ?: default

    actual fun putString(key: String, value: String) {
        prefs?.edit { putString(key, value) }
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        prefs?.getBoolean(key, default) ?: default

    actual fun putBoolean(key: String, value: Boolean) {
        prefs?.edit { putBoolean(key, value) }
    }
}
