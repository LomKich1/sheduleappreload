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

// ─── UI-состояния пикера ────────────────────────────────────────────────────
//
// Раньше это был один ScheduleUiState на весь экран — Idle/Loading/GroupPicker
// делили sealed-класс с Success/OnPractice/Error, из-за чего пикер и экран пар
// физически не могли быть двумя независимо живущими композициями (см.
// обсуждение свайпа "выйти из расписания пар"). Теперь пикер — это БАЗА
// ScheduleScreen (см. GroupPickerViewModel), а пары — отдельный оверлей со
// своим PairsViewModel, который создаётся тапом и уничтожается свайпом.
// Success/OnPractice/Error(парсинга) переехали в PairsViewModel.kt.

sealed interface PickerUiState {
    data object Idle                                  : PickerUiState
    data object Loading                               : PickerUiState
    data class  Ready(val groups: List<String>)       : PickerUiState
    data class  Error(val message: String)            : PickerUiState
}

class GroupPickerViewModel : ViewModel() {

    private val _uiState  = MutableStateFlow<PickerUiState>(PickerUiState.Idle)
    val uiState: StateFlow<PickerUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // Кеш байтов файла — раньше существовал только чтобы selectGroup()/
    // clearGroup() не ходили в сеть повторно. Теперь дополнительно это то,
    // что передаётся PairsViewModel при тапе по группе (см. handoffOrNull) —
    // сам hand-off, ради которого всё это разделение и затевалось.
    private var cachedBytes: ByteArray? = null
    private var currentFile: ScheduleFile? = null

    fun load(file: ScheduleFile) {
        viewModelScope.launch {
            _uiState.value  = PickerUiState.Loading
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
                _uiState.value  = PickerUiState.Error(e.message ?: "Ошибка скачивания")
                return@launch
            }

            cachedBytes     = bytes
            currentFile     = file
            _progress.value = 0.9f
            detectGroups(bytes, file)
        }
    }

    private suspend fun detectGroups(bytes: ByteArray, file: ScheduleFile) {
        withContext(Dispatchers.Default) {
            runCatching {
                if (isDebugJsonFile(file)) JsonScheduleParser.detectGroups(bytes.decodeToString())
                else DocParser.detectGroups(bytes)
            }
                .onSuccess { groups ->
                    _progress.value = 1f
                    _uiState.value  = PickerUiState.Ready(groups)
                }
                .onFailure { err ->
                    _uiState.value = PickerUiState.Error(
                        err.message ?: "Не удалось получить список групп"
                    )
                }
        }
    }

    private fun isDebugJsonFile(file: ScheduleFile): Boolean =
        IsDebugBuild && file.name.endsWith(".json", ignoreCase = true)

    /** Байты + файл — для передачи в PairsViewModel при выборе группы тапом. */
    fun handoffOrNull(): Pair<ByteArray, ScheduleFile>? {
        val bytes = cachedBytes ?: return null
        val file  = currentFile ?: return null
        return bytes to file
    }
}
