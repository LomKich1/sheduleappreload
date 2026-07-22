package com.schedule.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.model.TeacherDay
import com.schedule.app.data.model.TeacherParseResult
import com.schedule.app.data.parser.DocParser
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.data.repository.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// ─── UI-состояния TeacherScheduleScreen ───────────────────────────────────────
// Почти зеркало ScheduleUiState, но без OnPractice: "на практике" — это
// статус ГРУППЫ, а не преподавателя. LoadingStage переиспользуем из
// ScheduleViewModel.kt — он в том же пакете и не завязан на группу.

sealed interface TeacherUiState {
    data object Idle                                                 : TeacherUiState
    data class  Loading(val stage: LoadingStage)                     : TeacherUiState
    data class  TeacherPicker(val teachers: List<String>)            : TeacherUiState
    data class  Success(val day: TeacherDay)                         : TeacherUiState
    data class  Error(val message: String)                           : TeacherUiState
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class TeacherScheduleViewModel : ViewModel() {

    private val _uiState  = MutableStateFlow<TeacherUiState>(TeacherUiState.Idle)
    val uiState: StateFlow<TeacherUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // Выбранный преподаватель живёт прямо во ViewModel (а не в AppPrefs, как
    // groupName) — в отличие от группы, эта информация нигде за пределами
    // этого экрана сейчас не нужна, поэтому не тащим её в постоянные настройки.
    private val _teacherName = MutableStateFlow("")
    val teacherName: StateFlow<String> = _teacherName.asStateFlow()

    // ── Живые часы (как в ScheduleViewModel — для подсветки текущей пары) ─────
    private val _clockMin = MutableStateFlow(nowMin())
    val clockMin: StateFlow<Int> = _clockMin.asStateFlow()

    // Кеш байтов файла — чтобы selectTeacher()/clearTeacher() не скачивали
    // файл заново: пикер ↔ расписание работают из кеша.
    private var cachedBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                _clockMin.value = nowMin()
            }
        }
    }

    // ── Загрузка ──────────────────────────────────────────────────────────────

    fun load(file: ScheduleFile) {
        viewModelScope.launch {
            _uiState.value  = TeacherUiState.Loading(LoadingStage.FILE)
            _progress.value = 0f

            val url = AppPrefs.yandexUrl.value

            val bytes = try {
                ScheduleRepository.downloadFile(
                    publicKey  = url,
                    file       = file,
                    onProgress = { _progress.value = it * 0.85f },
                )
            } catch (e: Exception) {
                _progress.value = 0f
                _uiState.value  = TeacherUiState.Error(e.message ?: "Ошибка скачивания")
                return@launch
            }

            cachedBytes     = bytes
            _progress.value = 0.9f

            showTeacherPicker(bytes)
        }
    }

    /** Показывает список преподавателей из файла (детект по байтам, уже скачанным). */
    private suspend fun showTeacherPicker(bytes: ByteArray) {
        withContext(Dispatchers.Default) {
            runCatching { DocParser.detectTeachers(bytes) }
                .onSuccess { teachers ->
                    _progress.value = 1f
                    _uiState.value  = TeacherUiState.TeacherPicker(teachers)
                }
                .onFailure { err ->
                    _uiState.value = TeacherUiState.Error(
                        err.message ?: "Не удалось получить список преподавателей"
                    )
                }
        }
    }

    /** Парсим байты под конкретного преподавателя (после выбора в пикере). */
    private suspend fun parseForTeacher(bytes: ByteArray, teacher: String, fileName: String) {
        withContext(Dispatchers.Default) {
            runCatching { DocParser.parseForTeacher(bytes, teacher, fileName) }
                .onSuccess { result ->
                    _progress.value = 1f
                    _uiState.value  = when (result) {
                        is TeacherParseResult.Found    -> TeacherUiState.Success(result.day)
                        is TeacherParseResult.NotFound -> TeacherUiState.Error(
                            "У «$teacher» в этот день пар не нашлось"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.value = TeacherUiState.Error(
                        err.message ?: "Ошибка парсинга"
                    )
                }
        }
    }

    /** Пользователь выбрал преподавателя из пикера — сразу парсим из кеша. */
    fun selectTeacher(teacher: String, fileName: String) {
        _teacherName.value = teacher
        val bytes = cachedBytes ?: return
        viewModelScope.launch {
            _uiState.value = TeacherUiState.Loading(LoadingStage.SCHEDULE)
            parseForTeacher(bytes, teacher, fileName)
        }
    }

    /** Кнопка «Сменить преподавателя» — сбрасываем и возвращаемся к пикеру из кеша. */
    fun clearTeacher() {
        _teacherName.value = ""
        val bytes = cachedBytes ?: return
        viewModelScope.launch {
            showTeacherPicker(bytes)
        }
    }

    private fun nowMin(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
}
