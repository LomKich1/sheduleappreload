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

enum class LoadingStage { FILE, SCHEDULE }

sealed interface ScheduleUiState {
    data object Idle                                                : ScheduleUiState
    data class  Loading(val stage: LoadingStage)                    : ScheduleUiState
    data class  GroupPicker(val groups: List<String>)               : ScheduleUiState
    data class  Success(val day: ScheduleDay)                       : ScheduleUiState
    data class  OnPractice(val headerText: String)                  : ScheduleUiState
    data class  Error(val message: String)                          : ScheduleUiState
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ScheduleViewModel : ViewModel() {

    private val _uiState  = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Idle)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // ── Живые часы ────────────────────────────────────────────────────────────
    private val _clockMin = MutableStateFlow(nowMin())
    val clockMin: StateFlow<Int> = _clockMin.asStateFlow()

    // Кеш байтов файла — чтобы selectGroup() и clearGroup() не делали
    // повторный сетевой запрос: пикер ↔ расписание без скачивания.
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
            _uiState.value  = ScheduleUiState.Loading(LoadingStage.FILE)
            _progress.value = 0f

            val url = AppPrefs.yandexUrl.value

            // Скачиваем
            val bytes = try {
                ScheduleRepository.downloadFile(
                    publicKey  = url,
                    file       = file,
                    onProgress = { _progress.value = it * 0.85f },
                )
            } catch (e: Exception) {
                _progress.value = 0f
                _uiState.value  = ScheduleUiState.Error(e.message ?: "Ошибка скачивания")
                return@launch
            }

            cachedBytes     = bytes
            _progress.value = 0.9f

            // Пикер показывается всегда — даже если группа уже когда-то выбиралась.
            // Запомненная группа (pinnedGroup) только подсвечивается вверху списка
            // (см. GroupPickerScreen), но не пропускает этот экран автоматически —
            // выбор всегда подтверждается одним тапом.
            showGroupPicker(bytes)
        }
    }

    /** Показывает список групп из файла (детект по байтам, уже скачанным). */
    private suspend fun showGroupPicker(bytes: ByteArray) {
        withContext(Dispatchers.Default) {
            runCatching { DocParser.detectGroups(bytes) }
                .onSuccess { groups ->
                    _progress.value = 1f
                    _uiState.value  = ScheduleUiState.GroupPicker(groups)
                }
                .onFailure { err ->
                    _uiState.value = ScheduleUiState.Error(
                        err.message ?: "Не удалось получить список групп"
                    )
                }
        }
    }

    /** Парсим байты под конкретную группу (после выбора в пикере). */
    private suspend fun parseForGroup(bytes: ByteArray, group: String, fileName: String) {
        withContext(Dispatchers.Default) {
            runCatching { DocParser.parseForGroup(bytes, group, fileName) }
                .onSuccess { result ->
                    _progress.value = 1f
                    _uiState.value  = when (result) {
                        is ScheduleParseResult.Found      -> ScheduleUiState.Success(result.day)
                        is ScheduleParseResult.OnPractice -> ScheduleUiState.OnPractice(result.header)
                        is ScheduleParseResult.NotFound   -> ScheduleUiState.Error(
                            "Группа «$group» не найдена в файле"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.value = ScheduleUiState.Error(
                        err.message ?: "Ошибка парсинга"
                    )
                }
        }
    }

    /** Пользователь выбрал группу из пикера — сохраняем и сразу парсим из кеша */
    fun selectGroup(group: String, fileName: String) {
        AppPrefs.saveGroupName(group)          // ← сохраняет + pinnedGroup если нужно
        val bytes = cachedBytes ?: return
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading(LoadingStage.SCHEDULE)
            parseForGroup(bytes, group, fileName)
        }
    }

    /** Кнопка «Сменить группу» — сбрасываем и возвращаемся к пикеру из кеша */
    fun clearGroup() {
        AppPrefs.clearGroupName()              // ← только groupName, pinnedGroup жива
        val bytes = cachedBytes ?: return
        viewModelScope.launch {
            showGroupPicker(bytes)
        }
    }

    private fun nowMin(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
}
