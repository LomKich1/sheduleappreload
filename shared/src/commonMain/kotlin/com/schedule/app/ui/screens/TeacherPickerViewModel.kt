package com.schedule.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.parser.DocParser
import com.schedule.app.data.parser.JsonScheduleParser
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.data.repository.ScheduleRepository
import com.schedule.app.util.IsDebugBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Зеркало GroupPickerViewModel.kt — см. подробный комментарий там про то,
// почему пикер и экран пар разъехались по разным ViewModel'ям (свайп-выход
// с расписания пар, обсуждение в чате).

sealed interface TeacherPickerUiState {
    data object Idle                                  : TeacherPickerUiState
    data object Loading                               : TeacherPickerUiState
    data class  Ready(val teachers: List<String>)     : TeacherPickerUiState
    data class  Error(val message: String)            : TeacherPickerUiState
}

class TeacherPickerViewModel : ViewModel() {

    private val _uiState  = MutableStateFlow<TeacherPickerUiState>(TeacherPickerUiState.Idle)
    val uiState: StateFlow<TeacherPickerUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // См. GroupPickerViewModel.cachedBytes/currentFile — тот же hand-off,
    // без повторного скачивания, в TeacherPairsViewModel при тапе.
    private var cachedBytes: ByteArray? = null
    private var currentFile: ScheduleFile? = null

    fun load(file: ScheduleFile) {
        viewModelScope.launch {
            _uiState.value  = TeacherPickerUiState.Loading
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
                _uiState.value  = TeacherPickerUiState.Error(e.message ?: "Ошибка скачивания")
                return@launch
            }

            cachedBytes     = bytes
            currentFile     = file
            _progress.value = 0.9f
            detectTeachers(bytes, file)
        }
    }

    private suspend fun detectTeachers(bytes: ByteArray, file: ScheduleFile) {
        withContext(Dispatchers.Default) {
            runCatching {
                if (isDebugJsonFile(file)) JsonScheduleParser.detectTeachers(bytes.decodeToString())
                else DocParser.detectTeachers(bytes)
            }
                .onSuccess { teachers ->
                    _progress.value = 1f
                    _uiState.value  = TeacherPickerUiState.Ready(teachers)
                }
                .onFailure { err ->
                    _uiState.value = TeacherPickerUiState.Error(
                        err.message ?: "Не удалось получить список преподавателей"
                    )
                }
        }
    }

    private fun isDebugJsonFile(file: ScheduleFile): Boolean =
        IsDebugBuild && file.name.endsWith(".json", ignoreCase = true)

    fun handoffOrNull(): Pair<ByteArray, ScheduleFile>? {
        val bytes = cachedBytes ?: return null
        val file  = currentFile ?: return null
        return bytes to file
    }
}
