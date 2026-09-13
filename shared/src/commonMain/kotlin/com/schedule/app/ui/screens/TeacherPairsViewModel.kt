package com.schedule.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.model.TeacherDay
import com.schedule.app.data.model.TeacherParseResult
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

// Зеркало PairsViewModel.kt (студенческая ветка) — нет OnPractice, "на
// практике" это статус ГРУППЫ, а не преподавателя (см. исходный комментарий
// в бывшем TeacherScheduleViewModel).

sealed interface TeacherScheduleUiState {
    data object Loading                        : TeacherScheduleUiState
    data class  Success(val day: TeacherDay)   : TeacherScheduleUiState
    data class  Error(val message: String)     : TeacherScheduleUiState
}

class TeacherPairsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<TeacherScheduleUiState>(TeacherScheduleUiState.Loading)
    val uiState: StateFlow<TeacherScheduleUiState> = _uiState.asStateFlow()

    private val _clockMin = MutableStateFlow(currentMinutesSinceMidnight())
    val clockMin: StateFlow<Int> = _clockMin.asStateFlow()

    private var lastBytes: ByteArray? = null
    private var lastTeacher: String = ""
    private var lastFile: ScheduleFile? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                _clockMin.value = currentMinutesSinceMidnight()
            }
        }
    }

    fun load(bytes: ByteArray, teacher: String, file: ScheduleFile) {
        lastBytes   = bytes
        lastTeacher = teacher
        lastFile    = file
        viewModelScope.launch {
            _uiState.value = TeacherScheduleUiState.Loading
            parseForTeacher(bytes, teacher, file)
        }
    }

    fun retry() {
        val bytes = lastBytes ?: return
        val file  = lastFile ?: return
        viewModelScope.launch {
            _uiState.value = TeacherScheduleUiState.Loading
            parseForTeacher(bytes, lastTeacher, file)
        }
    }

    private suspend fun parseForTeacher(bytes: ByteArray, teacher: String, file: ScheduleFile) {
        withContext(Dispatchers.Default) {
            runCatching {
                if (isDebugJsonFile(file)) {
                    JsonScheduleParser.parseForTeacher(
                        json        = bytes.decodeToString(),
                        teacherName = teacher,
                        header      = "${file.dayLabel} · ${file.dateLabel}",
                        isToday     = file.isToday,
                        weekday     = weekdayOf(file),
                    )
                } else {
                    DocParser.parseForTeacher(bytes, teacher, file.name)
                }
            }
                .onSuccess { result ->
                    _uiState.value = when (result) {
                        is TeacherParseResult.Found    -> TeacherScheduleUiState.Success(result.day)
                        is TeacherParseResult.NotFound -> TeacherScheduleUiState.Error(
                            "У «$teacher» в этот день пар не нашлось"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.value = TeacherScheduleUiState.Error(err.message ?: "Ошибка парсинга")
                }
        }
    }

    private fun isDebugJsonFile(file: ScheduleFile): Boolean =
        IsDebugBuild && file.name.endsWith(".json", ignoreCase = true)

    private fun weekdayOf(file: ScheduleFile): Int =
        if (file.dayLabel.startsWith("Понедельник", ignoreCase = true)) 1 else 2
}
