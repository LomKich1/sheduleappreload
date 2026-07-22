package com.schedule.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.data.repository.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ─── UI-состояния FilesScreen ─────────────────────────────────────────────────

sealed interface FilesUiState {
    data object Loading : FilesUiState
    data class Success(val files: List<ScheduleFile>, val source: String) : FilesUiState
    data class Error(val message: String) : FilesUiState
}

// ─── FilesViewModel ───────────────────────────────────────────────────────────
// Источник URL — AppPrefs.yandexUrl. Подписка на combine(yandexUrl, refreshTick)
// делает ViewModel реактивным: список сам перезагрузится при возврате с
// SettingsScreen (сменился URL) или по кнопке «Обновить файлы» (тот же URL).
// Первая эмиссия (index == 0, холодный старт) использует кеш, если он есть;
// все последующие — forceRefresh, чтобы не показать устаревшие данные.

class FilesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<FilesUiState>(FilesUiState.Loading)
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(AppPrefs.yandexUrl, AppPrefs.refreshTick) { url, _ -> url }
                .collectIndexed { index, url ->
                    loadFiles(url, forceRefresh = index > 0)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadFiles(AppPrefs.yandexUrl.value, forceRefresh = true)
        }
    }

    private suspend fun loadFiles(url: String, forceRefresh: Boolean) {
        _uiState.value = FilesUiState.Loading
        runCatching {
            ScheduleRepository.getFiles(url, forceRefresh)
        }.onSuccess { files ->
            _uiState.value = FilesUiState.Success(
                files  = files,
                source = ScheduleRepository.lastSource,
            )
        }.onFailure { err ->
            _uiState.value = FilesUiState.Error(
                message = err.message ?: "Неизвестная ошибка"
            )
        }
    }
}
