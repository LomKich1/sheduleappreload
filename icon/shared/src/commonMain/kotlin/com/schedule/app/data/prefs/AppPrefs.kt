package com.schedule.app.data.prefs

import com.schedule.app.data.repository.ScheduleRepository
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.theme.ThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─── AppPrefs ─────────────────────────────────────────────────────────────────
// Синглтон-обёртка над PrefsStorage (как ScheduleRepository / NavigationHolder).
//
// ВАЖНО: AppPrefs.init(platformHandle) нужно вызвать один раз при старте —
// на Android из MainActivity.onCreate() (передав applicationContext), на ПК
// из main() (передав null). Если init() не вызван, объект просто отдаёт
// дефолты и тихо игнорирует запись — приложение не упадёт, но ничего не
// сохранится.
//
// yandexUrl / groupName / themePreset — StateFlow, а не обычные var. Это значит,
// что любой экран, подписанный через collectAsStateWithLifecycle(), сам
// перерисуется при изменении настроек на SettingsScreen — не нужно прокидывать
// колбэки через весь граф навигации.

object AppPrefs {

    private const val KEY_YANDEX_URL     = "yandex_url"
    private const val KEY_GROUP_NAME     = "group_name"
    private const val KEY_THEME          = "theme_preset"
    private const val KEY_REMEMBER_GROUP = "remember_group"
    private const val KEY_PINNED_GROUP   = "pinned_group"
    private const val KEY_LIST_ENTRANCE_ANIM = "list_entrance_anim"
    private const val KEY_DEFAULT_SCHEDULE_MODE = "default_schedule_mode"

    const val DEFAULT_YANDEX_URL = "https://disk.yandex.ru/d/mjhoc7kysmQEuQ"
    const val DEFAULT_GROUP_NAME = ""   // пусто → новый пользователь сразу видит пикер
    private val DEFAULT_THEME    = ThemePreset.DARK
    private val DEFAULT_SCHEDULE_MODE = ScheduleMode.STUDENT

    private var initialized = false

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
    private val _rememberGroup = MutableStateFlow(true)
    val rememberGroup: StateFlow<Boolean> = _rememberGroup.asStateFlow()

    private val _pinnedGroup = MutableStateFlow("")
    val pinnedGroup: StateFlow<String> = _pinnedGroup.asStateFlow()

    // ── Анимация появления элементов списка (каскад при переключении вкладки) ──
    private val _listEntranceAnim = MutableStateFlow(true)
    val listEntranceAnim: StateFlow<Boolean> = _listEntranceAnim.asStateFlow()

    // ── Какой вид открывается первым на экране файла: Ученики или Преподаватели ──
    private val _defaultScheduleMode = MutableStateFlow(DEFAULT_SCHEDULE_MODE)
    val defaultScheduleMode: StateFlow<ScheduleMode> = _defaultScheduleMode.asStateFlow()

    // Дёргается вручную («Обновить список файлов» в настройках), даже если URL
    // не менялся — например, в той же папке на Я.Диске появились новые файлы.
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    /**
     * @param platformHandle на Android — applicationContext, на ПК — null.
     */
    fun init(platformHandle: Any?) {
        if (initialized) return
        initialized = true
        PrefsStorage.init(platformHandle)

        _yandexUrl.value    = PrefsStorage.getString(KEY_YANDEX_URL, DEFAULT_YANDEX_URL)
        _groupName.value    = PrefsStorage.getString(KEY_GROUP_NAME, DEFAULT_GROUP_NAME)
        _themePreset.value  = runCatching { ThemePreset.valueOf(PrefsStorage.getString(KEY_THEME, DEFAULT_THEME.name)) }
            .getOrDefault(DEFAULT_THEME)
        _rememberGroup.value = PrefsStorage.getBoolean(KEY_REMEMBER_GROUP, true)
        _pinnedGroup.value   = PrefsStorage.getString(KEY_PINNED_GROUP, "")
        _listEntranceAnim.value = PrefsStorage.getBoolean(KEY_LIST_ENTRANCE_ANIM, true)
        _defaultScheduleMode.value = runCatching {
            ScheduleMode.valueOf(PrefsStorage.getString(KEY_DEFAULT_SCHEDULE_MODE, DEFAULT_SCHEDULE_MODE.name))
        }.getOrDefault(DEFAULT_SCHEDULE_MODE)
    }

    /** Мгновенно применяет и сохраняет тему — без ожидания «Сохранить». */
    fun setTheme(preset: ThemePreset) {
        _themePreset.value = preset
        PrefsStorage.putString(KEY_THEME, preset.name)
    }

    /** Сохраняет только URL папки, не трогая группу. */
    fun saveYandexUrl(url: String) {
        val clean = url.trim()
        PrefsStorage.putString(KEY_YANDEX_URL, clean)
        _yandexUrl.value = clean
    }

    /** Сохраняет URL папки и группу одной транзакцией. */
    fun saveDataSource(url: String, group: String) {
        val cleanUrl   = url.trim()
        val cleanGroup = group.trim()
        PrefsStorage.putString(KEY_YANDEX_URL, cleanUrl)
        PrefsStorage.putString(KEY_GROUP_NAME, cleanGroup)
        _yandexUrl.value = cleanUrl
        _groupName.value = cleanGroup
    }

    // ── Новые функции для работы с группой через GroupPicker ──────────────────

    /** Включает / выключает запоминание группы. */
    fun setRememberGroup(enabled: Boolean) {
        _rememberGroup.value = enabled
        PrefsStorage.putBoolean(KEY_REMEMBER_GROUP, enabled)
    }

    /**
     * Сохраняет выбранную в пикере группу.
     * Если rememberGroup включён — также обновляет pinnedGroup,
     * чтобы следующий вызов пикера подсветил её вверху.
     */
    fun saveGroupName(group: String) {
        val clean = group.trim()
        PrefsStorage.putString(KEY_GROUP_NAME, clean)
        if (_rememberGroup.value) PrefsStorage.putString(KEY_PINNED_GROUP, clean)
        _groupName.value = clean
        if (_rememberGroup.value) _pinnedGroup.value = clean
    }

    /**
     * Сбрасывает текущую группу (нажатие карандаша) — GroupPicker откроется снова.
     * pinnedGroup НЕ трогаем: она нужна, чтобы пикер подсветил последнюю группу.
     */
    fun clearGroupName() {
        PrefsStorage.putString(KEY_GROUP_NAME, "")
        _groupName.value = ""
    }

    /** Принудительный пересбор списка файлов без смены URL. */
    fun requestFilesRefresh() {
        ScheduleRepository.clearCache()
        _refreshTick.value++
    }

    /** Включает / выключает каскадную spring-анимацию появления карточек в списках. */
    fun setListEntranceAnim(enabled: Boolean) {
        _listEntranceAnim.value = enabled
        PrefsStorage.putBoolean(KEY_LIST_ENTRANCE_ANIM, enabled)
    }

    /** Какой вид (Ученики/Преподаватели) открывается первым на экране файла. */
    fun setDefaultScheduleMode(mode: ScheduleMode) {
        _defaultScheduleMode.value = mode
        PrefsStorage.putString(KEY_DEFAULT_SCHEDULE_MODE, mode.name)
    }
}
