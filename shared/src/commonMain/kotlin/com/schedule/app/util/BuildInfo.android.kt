package com.schedule.app.util

import com.schedule.app.BuildConfig

// BuildConfig генерируется Android Gradle Plugin автоматически из
// applicationId модуля app; DEBUG = true для assembleDebug, false для
// assembleRelease. Требует buildFeatures.buildConfig = true в app/build.gradle
// (в AGP 8+ эта генерация выключена по умолчанию).
actual val IsDebugBuild: Boolean = BuildConfig.DEBUG
