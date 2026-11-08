package com.schedule.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import com.schedule.app.ui.components.CenteredLoading
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.CascadeEntranceItem
import com.schedule.app.ui.components.FileCard
import com.schedule.app.ui.theme.AppRadius
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import com.schedule.app.util.currentLocalDateTime

// ─── FilesScreen ──────────────────────────────────────────────────────────────
// Шаг 2.2: реальная загрузка через FilesViewModel (Я.Диск → GitHub).
// Три состояния: Loading (скелетоны), Success (список), Error (с кнопкой retry).

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FilesScreen(
    vm: FilesViewModel = viewModel { FilesViewModel() },
    onFileClick: (ScheduleFile) -> Unit = {},
    entranceTrigger: Int = 0,
) {
    val uiState by vm.uiState.collectAsState()

    // Переключатель "Ученики / Преподаватели" переехал на экран файла (см.
    // ScheduleHostScreen) — тут он больше не нужен, клик по файлу просто
    // открывает этот экран, а какой вид (студенческий/преподавательский)
    // показать первым, решает настройка AppPrefs.defaultScheduleMode.

    // Раньше обновление списка запускалось только кнопкой в настройках
    // («Обновить список файлов») — убрали её оттуда в пользу жеста
    // pull-to-refresh прямо тут, на главном экране (см. правки дизайнера).
    // pullRefreshing — локальный флаг именно ДЛЯ ЖЕСТА: гаснет, как только
    // vm.uiState перестаёт быть Loading, независимо от того, что вызвало
    // загрузку (жест, смена URL в настройках, холодный старт).
    var pullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (uiState !is FilesUiState.Loading) pullRefreshing = false
    }

    // Каскад снизу вверх (BOTTOM) — только когда список ФИЗИЧЕСКИ только что
    // подгрузился (холодный старт ИЛИ pull-to-refresh — оба случая реально
    // проходят через FilesUiState.Loading, см. FilesViewModel.refresh()).
    // Каскад слева направо (LEFT) — обычное переключение вкладки/свайп, когда
    // данные уже на месте и никто никуда не грузился.
    //
    // Решение считается СИНХРОННО в теле композиции (а не в LaunchedEffect),
    // чтобы не словить гонку в один кадр: если бы edge выставлялся эффектом,
    // самый первый рендер списка после реальной подгрузки данных мог бы
    // получить ещё старое значение edge и полететь не с той стороны, а
    // перерисовка с правильным edge случилась бы кадром позже — на глаз это
    // читалось бы как "дёрнулось не туда и поправилось".
    val loadingNow = uiState is FilesUiState.Loading
    var wasLoading by remember { mutableStateOf(true) }
    var lastEntranceTrigger by remember { mutableStateOf(entranceTrigger) }
    var filesEntranceEdge by remember { mutableStateOf(CascadeEdge.BOTTOM) }
    if (wasLoading && !loadingNow) {
        filesEntranceEdge = CascadeEdge.BOTTOM
    } else if (entranceTrigger != lastEntranceTrigger) {
        filesEntranceEdge = CascadeEdge.LEFT
    }
    wasLoading = loadingNow
    lastEntranceTrigger = entranceTrigger

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.bg),
    ) {
        // Шапка ("Расписание" + значок колледжа + кнопка настроек) переехала
        // на уровень AppScaffold в единый AppHeader — общий для Files/Bells,
        // с flip-переходом названия при переключении вкладок. Тут её больше
        // нет, поэтому сразу небольшой отступ и дата.
        Spacer(Modifier.height(14.dp))

        TodayDateLine()

        Spacer(Modifier.height(14.dp))

        SectionLabel()

        val pullState = rememberPullToRefreshState()

        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh    = { pullRefreshing = true; vm.refresh() },
            state        = pullState,
            modifier     = Modifier.weight(1f),
            indicator    = {
                PullToRefreshDefaults.Indicator(
                    modifier      = Modifier.align(Alignment.TopCenter),
                    isRefreshing  = pullRefreshing,
                    state         = pullState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color         = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            when (val state = uiState) {
                is FilesUiState.Loading -> FilesLoading()
                is FilesUiState.Success -> FilesList(
                    files     = state.files,
                    onClick   = onFileClick,
                    entranceTrigger = entranceTrigger,
                    entranceEdge = filesEntranceEdge,
                )
                is FilesUiState.Error   -> FilesError(
                    message  = state.message,
                    onRetry  = vm::refresh,
                )
            }
        }
    }
}

// ─── Строка "сегодня" ─────────────────────────────────────────────────────────
// Раньше тут была большая декоративная плашка "РАСПИСАНИЕ" — убрали (см. историю
// правок), но пустое место осталось. Вместо чистой декорации — что-то реально
// полезное: текущая дата, которая и заполняет место, и несёт смысл.

@Composable
private fun TodayDateLine() {
    val today = remember { formatTodayRu() }
    Text(
        text = today,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

private val WEEKDAYS_RU = listOf(
    "воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота",
)

private val MONTHS_RU_GENITIVE = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private fun formatTodayRu(): String {
    val now      = currentLocalDateTime()
    val dayName  = WEEKDAYS_RU[(now.dayOfWeek.ordinal + 1) % 7]
        .replaceFirstChar { it.uppercaseChar() }
    val day      = now.dayOfMonth
    val month    = MONTHS_RU_GENITIVE[now.monthNumber - 1]
    return "Сегодня — $dayName, $day $month"
}

// ─── Метка секции ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel() {
    Text(
        text = "Файлы расписания",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

// ─── Состояние: список ────────────────────────────────────────────────────────

@Composable
private fun FilesList(
    files: List<ScheduleFile>,
    onClick: (ScheduleFile) -> Unit,
    entranceTrigger: Any,
    entranceEdge: CascadeEdge,
) {
    val entranceEnabled by AppPrefs.listEntranceAnim.collectAsState()

    // Раньше короткий список (1-3 файла) центрировался по вертикали внутри
    // доступной высоты — но именно это и было причиной бага со сдвигом:
    // высота BoxWithConstraints меняется на 76dp при открытии/закрытии
    // Schedule/Settings (пропадает/появляется паддинг под пилюлю), и группа
    // карточек пересчитывала центр и прыгала на ~38dp. Список всегда прижат
    // к верху, независимо от количества файлов — по правке дизайнера.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(files, key = { _, file -> file.name }) { index, file ->
            CascadeEntranceItem(
                index      = index,
                triggerKey = entranceTrigger,
                enabled    = entranceEnabled,
                edge       = entranceEdge,
            ) {
                FileCard(file = file, onClick = { onClick(file) })
            }
        }
    }
}

// ─── Состояние: загрузка (скелетоны) ─────────────────────────────────────────

@Composable
private fun FilesLoading() {
    // Скелетон-карточки не совпадали по высоте с настоящими — вместо них
    // M3-индикатор загрузки по центру.
    CenteredLoading()
}

// ─── Состояние: ошибка ────────────────────────────────────────────────────────

@Composable
private fun FilesError(message: String, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(scheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = null,
                tint = scheme.onErrorContainer,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Не удалось загрузить файлы",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Я.Диск и GitHub недоступны.\nПроверь подключение к интернету.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        FilledTonalButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Повторить")
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewDark() = AppTheme(ThemePreset.DARK) { FilesScreen() }

@Preview
@Composable
private fun PreviewAmoled() = AppTheme(ThemePreset.AMOLED) { FilesScreen() }
