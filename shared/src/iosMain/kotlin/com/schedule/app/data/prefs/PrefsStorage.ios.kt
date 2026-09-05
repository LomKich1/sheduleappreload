package com.schedule.app.data.prefs

import platform.Foundation.NSUserDefaults

/** Native iOS persistent storage, matching Android SharedPreferences semantics. */
actual object PrefsStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun init(platformHandle: Any?) = Unit

    actual fun getString(key: String, default: String): String =
        defaults.stringForKey(key) ?: default

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    actual fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }
}
