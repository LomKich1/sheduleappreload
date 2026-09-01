package com.schedule.app.util

import com.schedule.app.shared.BuildConfig

// BuildConfig генерируется Android Gradle Plugin автоматически из namespace
// МОДУЛЯ, в котором объявлен (namespace 'com.schedule.app.shared' в
// shared/build.gradle) — а не из applicationId модуля :app. Модуль :app
// зависит от :shared, а не наоборот, поэтому BuildConfig приложения отсюда
// в принципе не виден; нужен собственный BuildConfig модуля :shared.
// Требует buildFeatures.buildConfig = true в shared/build.gradle.
actual val IsDebugBuild: Boolean = BuildConfig.DEBUG
