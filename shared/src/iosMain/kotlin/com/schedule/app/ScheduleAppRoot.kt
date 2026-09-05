package com.schedule.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.schedule.app.data.prefs.AnimPrefs
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.AppScaffold
import com.schedule.app.ui.theme.AppTheme
import platform.UIKit.UIViewController

/** Swift-facing factory used by the lightweight iOS host application. */
object ScheduleAppRoot {
    fun makeViewController(): UIViewController {
        AppPrefs.init(null)
        AnimPrefs.init(null)
        return ComposeUIViewController {
            val theme by AppPrefs.themePreset.collectAsState()
            AppTheme(preset = theme) {
                AppScaffold()
            }
        }
    }
}
