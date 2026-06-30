package com.schedule.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.schedule.app.data.repository.ScheduleRepository
import com.schedule.app.ui.theme.ThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─── AppPrefs ─────────────────────────────────────────────────────────────────
// Синглтон-обёртка над SharedPreferences (как ScheduleRepository / NavigationHolder).
//
// ВАЖНО: AppPrefs.init(applicationContext) нужно вызвать один раз в
// MainActivity.onCreate() — ДО setContent {}. Если init() не вызван (например,
// в @Preview), объект просто отдаёт дефолты и тихо игнорирует запись —
// приложение не упадёт, но ничего не сохранится.
//
// yandexUrl / groupName / themePreset — StateFlow, а не обычные var. Это значит,
// что любой экран, подписанный через collectAsStateWithLifecycle(), сам
// перерисуется при изменении настроек на SettingsScreen — не нужно прокидывать
// колбэки через весь граф навигации.

object AppPrefs {

    private const val PREFS_NAME         = "schedule_app_prefs"
    private const val KEY_YANDEX_URL     = "yandex_url"
    private const val KEY_GROUP_NAME     = "group_name"
    private const val KEY_THEME          = "theme_preset"
    private const val KEY_REMEMBER_GROUP = "remember_group"
    private const val KEY_PINNED_GROUP   = "pinned_group"

    const val DEFAULT_YANDEX_URL = "https://disk.yandex.ru/d/mjhoc7kysmQEuQ"
    const val DEFAULT_GROUP_NAME = "МПД-2-24"
    private val DEFAULT_THEME    = ThemePreset.DARK

    private var prefs: SharedPreferences? = null

    private val _yandexUrl = MutableStateFlow(DEFAULT_YANDEX_URL)
    val yandexUrl: StateFlow<String> = _yandexUrl.asStateFlow()

    private val _groupName = MutableStateFlow(DEFAULT_GROUP_NAME)
    val groupName: StateFlow<String> = _groupName.asStateFlow()

    private val _themePreset = MutableStateFlow(DEFAULT_THEME)
    val themePreset: StateFlow<ThemePreset> = _themePreset.asStateFlow()

    // ── Запоминание группы ────────────────────────────────────────────────────
    // rememberGroup — переключатель из настроек.
    // pinnedGroup   — последняя выбранная группа; НЕ сбрасывается при нажатии
    //                 карандаша, чтобы GroupPicker мог её подсветить вверху.
    private val _rememberGroup = MutableStateFlow(false)
    val rememberGroup: StateFlow<Boolean> = _rememberGroup.asStateFlow()

    private val _pinnedGroup = MutableStateFlow("")
    val pinnedGroup: StateFlow<String> = _pinnedGroup.asStateFlow()

    // Дёргается вручную («Обновить список файлов» в настройках), даже если URL
    // не менялся — например, в той же папке на Я.Диске появились новые файлы.
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        _yandexUrl.value    = sp.getString(KEY_YANDEX_URL, DEFAULT_YANDEX_URL) ?: DEFAULT_YANDEX_URL
        _groupName.value    = sp.getString(KEY_GROUP_NAME, DEFAULT_GROUP_NAME) ?: DEFAULT_GROUP_NAME
        _themePreset.value  = sp.getString(KEY_THEME, null)
            ?.let { name -> runCatching { ThemePreset.valueOf(name) }.getOrNull() }
            ?: DEFAULT_THEME
        _rememberGroup.value = sp.getBoolean(KEY_REMEMBER_GROUP, false)
        _pinnedGroup.value   = sp.getString(KEY_PINNED_GROUP, "") ?: ""
    }

    /** Мгновенно применяет и сохраняет тему — без ожидания «Сохранить». */
    fun setTheme(preset: ThemePreset) {
        _themePreset.value = preset
        prefs?.edit { putString(KEY_THEME, preset.name) }
    }

    /** Сохраняет только URL папки, не трогая группу. */
    fun saveYandexUrl(url: String) {
        val clean = url.trim()
        prefs?.edit { putString(KEY_YANDEX_URL, clean) }
        _yandexUrl.value = clean
    }

    /** Сохраняет URL папки и группу одной транзакцией. */
    fun saveDataSource(url: String, group: String) {
        val cleanUrl   = url.trim()
        val cleanGroup = group.trim()
        prefs?.edit {
            putString(KEY_YANDEX_URL, cleanUrl)
            putString(KEY_GROUP_NAME, cleanGroup)
        }
        _yandexUrl.value = cleanUrl
        _groupName.value = cleanGroup
    }

    // ── Новые функции для работы с группой через GroupPicker ──────────────────

    /** Включает / выключает запоминание группы. */
    fun setRememberGroup(enabled: Boolean) {
        _rememberGroup.value = enabled
        prefs?.edit { putBoolean(KEY_REMEMBER_GROUP, enabled) }
    }

    /**
     * Сохраняет выбранную в пикере группу.
     * Если rememberGroup включён — также обновляет pinnedGroup,
     * чтобы следующий вызов пикера подсветил её вверху.
     */
    fun saveGroupName(group: String) {
        val clean = group.trim()
        prefs?.edit {
            putString(KEY_GROUP_NAME, clean)
            if (_rememberGroup.value) putString(KEY_PINNED_GROUP, clean)
        }
        _groupName.value = clean
        if (_rememberGroup.value) _pinnedGroup.value = clean
    }

    /**
     * Сбрасывает текущую группу (нажатие карандаша) — GroupPicker откроется снова.
     * pinnedGroup НЕ трогаем: она нужна, чтобы пикер подсветил последнюю группу.
     */
    fun clearGroupName() {
        prefs?.edit { putString(KEY_GROUP_NAME, "") }
        _groupName.value = ""
    }

    /** Принудительный пересбор списка файлов без смены URL. */
    fun requestFilesRefresh() {
        ScheduleRepository.clearCache()
        _refreshTick.value++
    }
}
