package com.schedule.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState // ВОТ ЭТОТ ИМПОРТ МЫ ДОБАВИЛИ!
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.AppScaffold
import com.schedule.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)   // до setContent — остальной код читает AppPrefs синхронно
        enableEdgeToEdge()
        setContent {
            // Теперь collectAsState() скомпилируется без проблем!
            val theme by AppPrefs.themePreset.collectAsState()
            AppTheme(preset = theme) {
                AppScaffold()
            }
        }
    }
}
