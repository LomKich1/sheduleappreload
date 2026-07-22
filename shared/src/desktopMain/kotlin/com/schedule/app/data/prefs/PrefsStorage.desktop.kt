package com.schedule.app.data.prefs

import java.util.prefs.Preferences

// На ПК контекст не нужен — java.util.prefs сама решает, где физически
// хранить данные: в реестре (HKEY_CURRENT_USER на Windows) или в
// конфиг-файлах (~/.java на Linux/macOS). Ключи те же, что и на Android.
actual object PrefsStorage {

    private val node = Preferences.userRoot().node("com/schedule/app")

    actual fun init(platformHandle: Any?) {
        // На ПК инициализировать нечего — узел Preferences создаётся лениво.
    }

    actual fun getString(key: String, default: String): String =
        node.get(key, default)

    actual fun putString(key: String, value: String) {
        node.put(key, value)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        node.getBoolean(key, default)

    actual fun putBoolean(key: String, value: Boolean) {
        node.putBoolean(key, value)
    }
}
