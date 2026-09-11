package com.schedule.app.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─── TabAnimMode ────────────────────────────────────────────────────────────
// DEFAULT  — как было до экспериментов: простой offset по tween(FastOutSlowIn).
//            Это то, что видят обычные пользователи в релизе.
// SPRING   — тот же простой 1:1 offset, но по пружинной физике вместо tween —
//            изолированно проверяем ощущение "физичности" без параллакса.
// PARALLAX — полный эффект: разные кривые движения для двух слоёв + лёгкий
//            scale/alpha (то, что мы собирали вместе в этом чате).
enum class TabAnimMode { DEFAULT, SPRING, PARALLAX }

// ─── AnimPrefs ──────────────────────────────────────────────────────────────
// Отдельный синглтон-объект по образцу AppPrefs. Всё здесь актуально только
// для debug-сборки (см. IsDebugBuild) — SettingsScreen прячет эту секцию в
// релизе, но сами настройки хранятся так же надёжно, через PrefsStorage.
object AnimPrefs {

    private const val KEY_MODE            = "tab_anim_mode"
    private const val KEY_DEFAULT_MS      = "tab_anim_default_ms"
    private const val KEY_SPRING_DAMPING  = "tab_anim_spring_damping"
    private const val KEY_SPRING_STIFF    = "tab_anim_spring_stiffness"
    private const val KEY_PARALLAX_POWER  = "tab_anim_parallax_power"
    private const val KEY_NAV_MS          = "nav_anim_duration_ms"

    // ── Свайп-назад (пары → пикер, ScheduleScreen/TeacherScheduleScreen) ───────
    // Отдельный от табов набор ключей — то же трио режимов (DEFAULT/SPRING/
    // PARALLAX), но со своими значениями, потому что это другой жест
    // (одноразовый pop, а не туда-обратно между двумя постоянными вкладками) и
    // крутить его хочется независимо от табов. См. SwipeBackGesture.kt.
    private const val KEY_SWIPEBACK_MODE           = "swipeback_anim_mode"
    private const val KEY_SWIPEBACK_MS             = "swipeback_anim_default_ms"
    private const val KEY_SWIPEBACK_SPRING_DAMPING = "swipeback_spring_damping"
    private const val KEY_SWIPEBACK_SPRING_STIFF   = "swipeback_spring_stiffness"
    private const val KEY_SWIPEBACK_PARALLAX_POWER = "swipeback_parallax_power"

    // Дефолты подобраны в этом чате: DEFAULT_MS — как было в проекте изначально,
    // SPRING/PARALLAX — то, что мы вместе настроили и на чём остановились.
    val DEFAULT_MODE            = TabAnimMode.DEFAULT
    const val DEFAULT_DURATION_MS      = 250
    const val DEFAULT_SPRING_DAMPING   = 0.78f
    const val DEFAULT_SPRING_STIFFNESS = 380f
    const val DEFAULT_PARALLAX_POWER   = 1.6f
    // Отдельная длительность — для NavHost-переходов между "глубокими" экранами
    // (Files/Bells → Schedule/Settings/DebugSettings), а не для табов внутри
    // экрана (те выше, DEFAULT_DURATION_MS и др.). Два разных механизма
    // анимации в проекте, каждый со своей настройкой — см. AppScaffold.kt.
    const val DEFAULT_NAV_DURATION_MS  = 340

    // По умолчанию — DEFAULT (простой синхронный dual-mount reveal, 1:1 с
    // пальцем, без scale/alpha) — именно то, что попросили воспроизвести
    // "как в Телеграме", без лишних эффектов поверх. SPRING/PARALLAX доступны
    // в дебаг-панели для сравнения, как и с табами.
    val DEFAULT_SWIPEBACK_MODE                = TabAnimMode.DEFAULT
    const val DEFAULT_SWIPEBACK_DURATION_MS   = 280 // совпадает с SUBSCREEN_ANIM_MS
    const val DEFAULT_SWIPEBACK_SPRING_DAMPING   = 0.78f
    const val DEFAULT_SWIPEBACK_SPRING_STIFFNESS = 380f
    const val DEFAULT_SWIPEBACK_PARALLAX_POWER   = 1.6f

    private var initialized = false

    private val _mode = MutableStateFlow(DEFAULT_MODE)
    val mode: StateFlow<TabAnimMode> = _mode.asStateFlow()

    private val _durationMs = MutableStateFlow(DEFAULT_DURATION_MS)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _springDamping = MutableStateFlow(DEFAULT_SPRING_DAMPING)
    val springDamping: StateFlow<Float> = _springDamping.asStateFlow()

    private val _springStiffness = MutableStateFlow(DEFAULT_SPRING_STIFFNESS)
    val springStiffness: StateFlow<Float> = _springStiffness.asStateFlow()

    private val _parallaxPower = MutableStateFlow(DEFAULT_PARALLAX_POWER)
    val parallaxPower: StateFlow<Float> = _parallaxPower.asStateFlow()

    private val _navDurationMs = MutableStateFlow(DEFAULT_NAV_DURATION_MS)
    val navDurationMs: StateFlow<Int> = _navDurationMs.asStateFlow()

    private val _swipeBackMode = MutableStateFlow(DEFAULT_SWIPEBACK_MODE)
    val swipeBackMode: StateFlow<TabAnimMode> = _swipeBackMode.asStateFlow()

    private val _swipeBackDurationMs = MutableStateFlow(DEFAULT_SWIPEBACK_DURATION_MS)
    val swipeBackDurationMs: StateFlow<Int> = _swipeBackDurationMs.asStateFlow()

    private val _swipeBackSpringDamping = MutableStateFlow(DEFAULT_SWIPEBACK_SPRING_DAMPING)
    val swipeBackSpringDamping: StateFlow<Float> = _swipeBackSpringDamping.asStateFlow()

    private val _swipeBackSpringStiffness = MutableStateFlow(DEFAULT_SWIPEBACK_SPRING_STIFFNESS)
    val swipeBackSpringStiffness: StateFlow<Float> = _swipeBackSpringStiffness.asStateFlow()

    private val _swipeBackParallaxPower = MutableStateFlow(DEFAULT_SWIPEBACK_PARALLAX_POWER)
    val swipeBackParallaxPower: StateFlow<Float> = _swipeBackParallaxPower.asStateFlow()

    /** Вызывается вместе с AppPrefs.init() — PrefsStorage.init() уже идемпотентен. */
    fun init(platformHandle: Any?) {
        if (initialized) return
        initialized = true
        PrefsStorage.init(platformHandle)

        _mode.value = runCatching {
            TabAnimMode.valueOf(PrefsStorage.getString(KEY_MODE, DEFAULT_MODE.name))
        }.getOrDefault(DEFAULT_MODE)

        _durationMs.value = runCatching {
            PrefsStorage.getString(KEY_DEFAULT_MS, DEFAULT_DURATION_MS.toString()).toInt()
        }.getOrDefault(DEFAULT_DURATION_MS)

        _springDamping.value = runCatching {
            PrefsStorage.getString(KEY_SPRING_DAMPING, DEFAULT_SPRING_DAMPING.toString()).toFloat()
        }.getOrDefault(DEFAULT_SPRING_DAMPING)

        _springStiffness.value = runCatching {
            PrefsStorage.getString(KEY_SPRING_STIFF, DEFAULT_SPRING_STIFFNESS.toString()).toFloat()
        }.getOrDefault(DEFAULT_SPRING_STIFFNESS)

        _parallaxPower.value = runCatching {
            PrefsStorage.getString(KEY_PARALLAX_POWER, DEFAULT_PARALLAX_POWER.toString()).toFloat()
        }.getOrDefault(DEFAULT_PARALLAX_POWER)

        _navDurationMs.value = runCatching {
            PrefsStorage.getString(KEY_NAV_MS, DEFAULT_NAV_DURATION_MS.toString()).toInt()
        }.getOrDefault(DEFAULT_NAV_DURATION_MS)

        _swipeBackMode.value = runCatching {
            TabAnimMode.valueOf(PrefsStorage.getString(KEY_SWIPEBACK_MODE, DEFAULT_SWIPEBACK_MODE.name))
        }.getOrDefault(DEFAULT_SWIPEBACK_MODE)

        _swipeBackDurationMs.value = runCatching {
            PrefsStorage.getString(KEY_SWIPEBACK_MS, DEFAULT_SWIPEBACK_DURATION_MS.toString()).toInt()
        }.getOrDefault(DEFAULT_SWIPEBACK_DURATION_MS)

        _swipeBackSpringDamping.value = runCatching {
            PrefsStorage.getString(KEY_SWIPEBACK_SPRING_DAMPING, DEFAULT_SWIPEBACK_SPRING_DAMPING.toString()).toFloat()
        }.getOrDefault(DEFAULT_SWIPEBACK_SPRING_DAMPING)

        _swipeBackSpringStiffness.value = runCatching {
            PrefsStorage.getString(KEY_SWIPEBACK_SPRING_STIFF, DEFAULT_SWIPEBACK_SPRING_STIFFNESS.toString()).toFloat()
        }.getOrDefault(DEFAULT_SWIPEBACK_SPRING_STIFFNESS)

        _swipeBackParallaxPower.value = runCatching {
            PrefsStorage.getString(KEY_SWIPEBACK_PARALLAX_POWER, DEFAULT_SWIPEBACK_PARALLAX_POWER.toString()).toFloat()
        }.getOrDefault(DEFAULT_SWIPEBACK_PARALLAX_POWER)
    }

    fun setMode(newMode: TabAnimMode) {
        _mode.value = newMode
        PrefsStorage.putString(KEY_MODE, newMode.name)
    }

    fun setDurationMs(ms: Int) {
        _durationMs.value = ms
        PrefsStorage.putString(KEY_DEFAULT_MS, ms.toString())
    }

    fun setSpringDamping(v: Float) {
        _springDamping.value = v
        PrefsStorage.putString(KEY_SPRING_DAMPING, v.toString())
    }

    fun setSpringStiffness(v: Float) {
        _springStiffness.value = v
        PrefsStorage.putString(KEY_SPRING_STIFF, v.toString())
    }

    fun setParallaxPower(v: Float) {
        _parallaxPower.value = v
        PrefsStorage.putString(KEY_PARALLAX_POWER, v.toString())
    }

    fun setNavDurationMs(ms: Int) {
        _navDurationMs.value = ms
        PrefsStorage.putString(KEY_NAV_MS, ms.toString())
    }

    fun setSwipeBackMode(newMode: TabAnimMode) {
        _swipeBackMode.value = newMode
        PrefsStorage.putString(KEY_SWIPEBACK_MODE, newMode.name)
    }

    fun setSwipeBackDurationMs(ms: Int) {
        _swipeBackDurationMs.value = ms
        PrefsStorage.putString(KEY_SWIPEBACK_MS, ms.toString())
    }

    fun setSwipeBackSpringDamping(v: Float) {
        _swipeBackSpringDamping.value = v
        PrefsStorage.putString(KEY_SWIPEBACK_SPRING_DAMPING, v.toString())
    }

    fun setSwipeBackSpringStiffness(v: Float) {
        _swipeBackSpringStiffness.value = v
        PrefsStorage.putString(KEY_SWIPEBACK_SPRING_STIFF, v.toString())
    }

    fun setSwipeBackParallaxPower(v: Float) {
        _swipeBackParallaxPower.value = v
        PrefsStorage.putString(KEY_SWIPEBACK_PARALLAX_POWER, v.toString())
    }

    /** Сброс всех крутилок к значениям по умолчанию (кнопка в дебаг-панели). */
    fun resetToDefaults() {
        setMode(DEFAULT_MODE)
        setDurationMs(DEFAULT_DURATION_MS)
        setSpringDamping(DEFAULT_SPRING_DAMPING)
        setSpringStiffness(DEFAULT_SPRING_STIFFNESS)
        setParallaxPower(DEFAULT_PARALLAX_POWER)
        setNavDurationMs(DEFAULT_NAV_DURATION_MS)
        setSwipeBackMode(DEFAULT_SWIPEBACK_MODE)
        setSwipeBackDurationMs(DEFAULT_SWIPEBACK_DURATION_MS)
        setSwipeBackSpringDamping(DEFAULT_SWIPEBACK_SPRING_DAMPING)
        setSwipeBackSpringStiffness(DEFAULT_SWIPEBACK_SPRING_STIFFNESS)
        setSwipeBackParallaxPower(DEFAULT_SWIPEBACK_PARALLAX_POWER)
    }
}
