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
import androidx.compose.material3.Icon
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
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import java.util.Calendar

// ─── FilesScreen ──────────────────────────────────────────────────────────────
// Шаг 2.2: реальная загрузка через FilesViewModel (Я.Диск → GitHub).
// Три состояния: Loading (скелетоны), Success (список), Error (с кнопкой retry).

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FilesScreen(
    vm: FilesViewModel = viewModel(),
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
                    containerColor = LocalAppColors.current.surface,
                    color         = LocalAppColors.current.accent,
                )
            },
        ) {
            when (val state = uiState) {
                is FilesUiState.Loading -> FilesLoading()
                is FilesUiState.Success -> FilesList(
                    files     = state.files,
                    onClick   = onFileClick,
                    entranceTrigger = entranceTrigger,
                    entranceEdge = CascadeEdge.LEFT,
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
    val c     = LocalAppColors.current
    val today = remember { formatTodayRu() }
    Text(
        text = today,
        color = c.textSub,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}

private val WEEKDAYS_RU = listOf(
    "воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота",
) // Calendar.DAY_OF_WEEK: 1 = воскресенье ... 7 = суббота

private val MONTHS_RU_GENITIVE = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private fun formatTodayRu(): String {
    val cal      = Calendar.getInstance()
    val dayName  = WEEKDAYS_RU[cal.get(Calendar.DAY_OF_WEEK) - 1]
        .replaceFirstChar { it.uppercaseChar() }
    val day      = cal.get(Calendar.DAY_OF_MONTH)
    val month    = MONTHS_RU_GENITIVE[cal.get(Calendar.MONTH)]
    return "Сегодня — $dayName, $day $month"
}

// ─── Метка секции ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel() {
    val c = LocalAppColors.current
    Text(
        text = "ФАЙЛЫ РАСПИСАНИЯ",
        color = c.textSub,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.08.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
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

    // Правка дизайнера: короткий список (1-3 файла) не должен прилипать к верху
    // с пустым "хвостом" внизу — центрируем группу карточек по вертикали,
    // сохраняя интервал между ними. Длинный список работает как раньше.
    val isShort = files.size <= 3

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = if (isShort)
            Arrangement.spacedBy(9.dp, Alignment.CenterVertically)
        else
            Arrangement.spacedBy(9.dp),
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
    val c = LocalAppColors.current
    val alpha by rememberInfiniteTransition(label = "skeleton")
        .animateFloat(
            initialValue = 1f,
            targetValue  = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = EaseInOutQuad),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alpha",
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        repeat(5) { i ->
            SkeletonCard(alpha = alpha, delay = i * 0.12f)
        }
    }
}

@Composable
private fun SkeletonCard(alpha: Float, delay: Float) {
    val c = LocalAppColors.current
    // Небольшой сдвиг фазы между карточками через offset на alpha
    val a = (alpha - delay).coerceIn(0.3f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface.copy(alpha = a))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(c.surface2.copy(alpha = a))
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 110.dp, height = 11.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2.copy(alpha = a)))
            Box(Modifier.size(width = 60.dp, height = 9.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2.copy(alpha = a)))
        }
    }
}

// ─── Состояние: ошибка ────────────────────────────────────────────────────────

@Composable
private fun FilesError(message: String, onRetry: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = null,
                tint = c.textSub,
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Не удалось загрузить файлы",
            color = c.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Я.Диск и GitHub недоступны.\nПроверь подключение к интернету.",
            color = c.textSub,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
            Text("Повторить", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
