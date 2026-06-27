package com.schedule.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.data.model.ScheduleDay
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.model.ScheduleParseResult
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

// ─── UI-состояния ScheduleScreen ──────────────────────────────────────────────

sealed interface ScheduleUiState {
    data object Idle      : ScheduleUiState
    data object Loading   : ScheduleUiState
    data class  Success(val day: ScheduleDay)        : ScheduleUiState
    data class  OnPractice(val headerText: String)   : ScheduleUiState
    data class  Error(val message: String)           : ScheduleUiState
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ScheduleViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Idle)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    // Прогресс скачивания (0..1) — для тонкой полоски под шапкой
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // ── Живые часы ────────────────────────────────────────────────────────────
    // Обновляются каждые 30 секунд. ScheduleScreen подписывается через
    // collectAsStateWithLifecycle() и пересчитывает isNow/isNext/remain/progress
    // прямо в Composable — без пересоздания экрана.

    private val _clockMin = MutableStateFlow(nowMin())
    val clockMin: StateFlow<Int> = _clockMin.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                _clockMin.value = nowMin()
            }
        }
    }

    // ── Загрузка расписания ───────────────────────────────────────────────────

    /**
     * Скачивает файл [file], парсит его и публикует результат в [uiState].
     * URL и группа читаются из AppPrefs в момент вызова — всегда свежие.
     */
    fun load(file: ScheduleFile) {
        viewModelScope.launch {
            _uiState.value  = ScheduleUiState.Loading
            _progress.value = 0f

            val url   = AppPrefs.yandexUrl.value
            val group = AppPrefs.groupName.value

            runCatching {
                val bytes = ScheduleRepository.downloadFile(
                    publicKey  = url,
                    file       = file,
                    onProgress = { _progress.value = it * 0.8f },
                )
                withContext(Dispatchers.Default) {
                    _progress.value = 0.95f
                    val result = DocParser.parseForGroup(bytes, group, file.name)
                    _progress.value = 1f
                    result
                }
            }.onSuccess { result ->
                _uiState.value = when (result) {
                    is ScheduleParseResult.Found      -> ScheduleUiState.Success(result.day)
                    is ScheduleParseResult.OnPractice -> ScheduleUiState.OnPractice(result.header)
                    is ScheduleParseResult.NotFound   -> ScheduleUiState.Error(
                        "Группа $group не найдена в файле"
                    )
                }
            }.onFailure { err ->
                _progress.value = 0f
                _uiState.value  = ScheduleUiState.Error(err.message ?: "Неизвестная ошибка")
            }
        }
    }

    // ── Хелперы ──────────────────────────────────────────────────────────────

    private fun nowMin(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
}
