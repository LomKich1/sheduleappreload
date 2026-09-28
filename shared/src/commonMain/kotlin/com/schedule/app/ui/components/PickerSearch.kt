package com.schedule.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.AppRadius
import com.schedule.app.ui.theme.LocalAppColors
import kotlin.math.roundToInt

// ─── Поиск на экранах выбора группы / преподавателя ──────────────────────────
//
// Состояние поиска ОДНО на оба вида (Ученики/Преподаватели) и живёт в
// ScheduleHostScreen. Свайп Ученики↔Преподаватели при открытом поиске НЕ
// блокируется: поиск остаётся открытым, а текст запроса сохраняется и
// фильтрует уже другой список.
//
// Тумблер режима при поиске (см. PickerScaffold):
//   • TOP    — гаснет через fade и схлопывается по высоте;
//   • BOTTOM — остаётся на месте и едет вместе с клавиатурой (union инсетов
//              navigationBars/ime), ровно над ней.
//
// Что происходит при открытии (progress 0 → 1, одна и та же анимация в обе
// стороны):
//   • шапка (заголовок + дата + полоса загрузки) гаснет через alpha и чуть
//     уезжает вверх (SEARCH_SHIFT) — ощущение, что поле наслаивается на неё;
//   • поле поиска поднимается на место шапки (layout-высота зоны схлопывается
//     на высоту шапки, поэтому тумблер (TOP) и слой результатов едут вместе с ним);
//   • обычный список гаснет с тем же смещением вверх, вместо него — слой
//     результатов (показывается только когда что-то введено);
//   • клавиатура открывается сразу.
// Закрытие — крестик в поле или системный back — та же схема в обратную
// сторону.
//
// ПОКА поле видно всегда — оверскролл-жест (как в Telegram: поле спрятано и
// выезжает при протяжке за верх списка) будет отдельным шагом.

val SEARCH_SHIFT = 20.dp
const val SEARCH_ANIM_MS = 300

@Stable
class PickerSearchState {
    var active by mutableStateOf(false)
        private set
    var query by mutableStateOf("")

    fun open() { active = true }
    fun close() { active = false }
}

@Composable
fun rememberSearchProgress(search: PickerSearchState): State<Float> =
    animateFloatAsState(
        targetValue   = if (search.active) 1f else 0f,
        animationSpec = tween(SEARCH_ANIM_MS, easing = FastOutSlowInEasing),
        label         = "pickerSearchProgress",
    )

/** Глотает все касания, пока enabled — для скрытых (но ещё смонтированных) слоёв. */
fun Modifier.blockTouches(enabled: Boolean): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

/** Прячет клавиатуру (снимает фокус), не закрывая сам поиск. */
@Composable
fun rememberHideKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
}

// ─── Зона шапки: [шапка] + [поле поиска] ─────────────────────────────────────

@Composable
fun SearchHeaderZone(
    search: PickerSearchState,
    progress: State<Float>,
    isVisibleHalf: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
) {
    val shiftPx = with(LocalDensity.current) { SEARCH_SHIFT.toPx() }

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val p = progress.value
                        alpha = 1f - p
                        translationY = -shiftPx * p
                    }
                    // Невидимая шапка не должна ловить тап по кнопке «назад».
                    .blockTouches(search.active),
            ) { header() }

            // Поле — вторым: рисуется поверх шапки, пока та гаснет.
            PickerSearchField(search = search, progress = progress, isVisibleHalf = isVisibleHalf)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val headerPlaceable = measurables[0].measure(loose)
        val fieldPlaceable  = measurables[1].measure(loose)
        val p = progress.value
        val fieldY = (headerPlaceable.height * (1f - p)).roundToInt()
        layout(constraints.maxWidth, fieldPlaceable.height + fieldY) {
            headerPlaceable.place(0, 0)
            fieldPlaceable.place(0, fieldY)
        }
    }
}

@Composable
private fun PickerSearchField(
    search: PickerSearchState,
    progress: State<Float>,
    isVisibleHalf: Boolean,
) {
    val c = LocalAppColors.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val active = search.active

    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(active, isVisibleHalf) {
        if (active && isVisibleHalf) {
            // Ждём пару кадров, чтобы enabled = true долетел до текстового поля.
            withFrameNanos { }
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        } else if (!active && wasActive) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
        wasActive = active
    }

    val shape = AppRadius.capsule
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp)
            .height(42.dp)
            .clip(shape)
            .background(c.pillBg)
            .border(1.dp, c.border, shape)
            // Пока поиск закрыт — тап по любой части поля открывает его.
            .then(if (!active) Modifier.clickable { search.open() } else Modifier)
            .padding(start = 14.dp, end = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Outlined.Search,
                contentDescription = null,
                tint               = c.textSub,
                modifier           = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (search.query.isEmpty()) {
                    Text(text = "Поиск", color = c.textSub, fontSize = 15.sp)
                }
                BasicTextField(
                    value         = search.query,
                    onValueChange = { search.query = it },
                    enabled       = active,
                    singleLine    = true,
                    textStyle     = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush   = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        // Кнопка «поиск» на клавиатуре — просто прячет клавиатуру.
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }

            // Крестик — виден всё время, пока поиск открыт (и с клавиатурой,
            // и без неё). Закрывает поиск целиком.
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer { alpha = progress.value }
                    .clip(CircleShape)
                    .clickable(enabled = active) { search.close() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Close,
                    contentDescription = "Закрыть поиск",
                    tint               = c.textSub,
                    modifier           = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ─── Слой результатов ────────────────────────────────────────────────────────

/**
 * Ложится поверх обычного списка. Пока запрос пуст — ничего не рисует (список
 * под ним уже погашен). Результаты появляются с первой же буквы.
 * Любая попытка прокрутки прячет клавиатуру, но не сам поиск.
 */
@Composable
fun SearchResultsLayer(
    search: PickerSearchState,
    progress: State<Float>,
    items: List<String>,
    modifier: Modifier = Modifier,
    // Живые отступы под верхний/нижний «хром» (шапка, тумблер, клавиатура).
    // Читаются в layout-фазе, поэтому анимация шапки/клавиатуры не вызывает
    // рекомпозицию списка результатов на каждый кадр.
    topInsetPx: () -> Int = { 0 },
    bottomInsetPx: Density.() -> Int = { 0 },
    card: @Composable (String) -> Unit,
) {
    val c = LocalAppColors.current
    val hideKeyboard = rememberHideKeyboard()

    if (search.active || progress.value > 0.001f) {

        val query = search.query
        val matches = remember(items, query) { filterPickerItems(items, query) }

        val hideOnScroll = remember(hideKeyboard) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y != 0f) hideKeyboard()
                    return Offset.Zero
                }
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val top = topInsetPx()
                    val bottom = bottomInsetPx()
                    val h = (constraints.maxHeight - top - bottom).coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, top) }
                }
                .graphicsLayer { alpha = progress.value }
                .nestedScroll(hideOnScroll)
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (query.isNotBlank() && matches.isEmpty()) {
                Text(
                    text     = "Ничего не найдено",
                    color    = c.textSub,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            matches.forEach { card(it) }
        }
    }
}

// ─── Фильтр ──────────────────────────────────────────────────────────────────
// Подстрока в любом месте названия, без учёта регистра, «ё»=«е» и без учёта
// знаков препинания/пробелов: «ис225» найдёт «ИС-2-25», «иванов и» — «Иванов
// И.И.». Совпадения с начала названия идут выше остальных.

private fun normalizeForSearch(s: String): String = buildString {
    for (ch in s.lowercase()) {
        val n = if (ch == 'ё') 'е' else ch
        if (n.isLetterOrDigit()) append(n)
    }
}

fun filterPickerItems(items: List<String>, query: String): List<String> {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return emptyList()
    val starts = ArrayList<String>()
    val inside = ArrayList<String>()
    for (item in items) {
        val n = normalizeForSearch(item)
        when {
            n.startsWith(q) -> starts.add(item)
            n.contains(q)   -> inside.add(item)
        }
    }
    return starts + inside
}
