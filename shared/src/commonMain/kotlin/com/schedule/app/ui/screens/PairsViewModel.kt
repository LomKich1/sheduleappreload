package com.schedule.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.data.model.ScheduleDay
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.model.ScheduleParseResult
import com.schedule.app.data.parser.DocParser
import com.schedule.app.data.parser.JsonScheduleParser
import com.schedule.app.util.IsDebugBuild
import com.schedule.app.util.currentMinutesSinceMidnight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── UI-состояния экрана пар ────────────────────────────────────────────────
// Больше нет Idle/GroupPicker (это теперь PickerUiState в
// GroupPickerViewModel.kt) и нет LoadingStage — тут ровно один сценарий:
// "распарсить уже скачанные байты под конкретную группу".

sealed interface ScheduleUiState {
    data object  Loading                       : ScheduleUiState
    data class   Success(val day: ScheduleDay) : ScheduleUiState
    data class   OnPractice(val headerText: String) : ScheduleUiState
    data class   Error(val message: String)    : ScheduleUiState
}

/**
 * Живёт от тапа по карточке группы до свайпа/back назад — короткий,
 * "одноразовый" ViewModel (см. ScheduleScreen.PairsSelection и
 * viewModel(key = ...) на месте создания).
 */
class PairsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _clockMin = MutableStateFlow(currentMinutesSinceMidnight())
    val clockMin: StateFlow<Int> = _clockMin.asStateFlow()

    // Нужны только для retry() — повторный парсинг уже полученных байт,
    // без похода в сеть (сети тут вообще нет, см. класс выше).
    private var lastBytes: ByteArray? = null
    private var lastGroup: String = ""
    private var lastFile: ScheduleFile? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                _clockMin.value = currentMinutesSinceMidnight()
            }
        }
    }

    fun load(bytes: ByteArray, group: String, file: ScheduleFile) {
        lastBytes = bytes
        lastGroup = group
        lastFile  = file
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            parseForGroup(bytes, group, file)
        }
    }

    fun retry() {
        val bytes = lastBytes ?: return
        val file  = lastFile ?: return
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            parseForGroup(bytes, lastGroup, file)
        }
    }

    private suspend fun parseForGroup(bytes: ByteArray, group: String, file: ScheduleFile) {
        withContext(Dispatchers.Default) {
            runCatching {
                if (isDebugJsonFile(file)) {
                    JsonScheduleParser.parseForGroup(
                        json    = bytes.decodeToString(),
                        group   = group,
                        header  = "${file.dayLabel} · ${file.dateLabel}",
                        isToday = file.isToday,
                        weekday = weekdayOf(file),
                    )
                } else {
                    DocParser.parseForGroup(bytes, group, file.name)
                }
            }
                .onSuccess { result ->
                    _uiState.value = when (result) {
                        is ScheduleParseResult.Found      -> ScheduleUiState.Success(result.day)
                        is ScheduleParseResult.OnPractice -> ScheduleUiState.OnPractice(result.header)
                        is ScheduleParseResult.NotFound   -> ScheduleUiState.Error(
                            "Группа «$group» не найдена в файле"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.value = ScheduleUiState.Error(err.message ?: "Ошибка парсинга")
                }
        }
    }

    // JSON-путь пока только в debug-сборке — см. исходный комментарий в
    // прежнем ScheduleViewModel, причина не изменилась (схема под препода
    // ещё не обкатана на реальных данных).
    private fun isDebugJsonFile(file: ScheduleFile): Boolean =
        IsDebugBuild && file.name.endsWith(".json", ignoreCase = true)

    private fun weekdayOf(file: ScheduleFile): Int =
        if (file.dayLabel.startsWith("Понедельник", ignoreCase = true)) 1 else 2
}
