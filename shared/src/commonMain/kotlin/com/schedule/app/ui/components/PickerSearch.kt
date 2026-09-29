package com.schedule.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.AppRadius
import com.schedule.app.ui.theme.LocalAppColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
// Pull-to-search (как в Telegram): по умолчанию поле СКРЫТО (высота 0 у
// зоны шапки). Оно выезжает из-под шапки, когда список у верхнего края
// протягивают вниз (PullToSearchConnection ниже):
//   • drag вниз у верха списка → reveal 0→1 (с сопротивлением), шапка
//     и список едут вниз вместе с полем;
//   • отпустили — доводка: ≥ половины (или fling вниз) → поле остаётся
//     открытым, иначе уезжает обратно;
//   • прокрутка списка вверх, пока поле выдвинуто → сначала сворачивает поле;
//   • тап по выдвинутому полю → поиск (active), после чего поле «встаёт»
//     на место шапки и жест игнорируется — строка зафиксирована;
//   • закрытие поиска → поле убирается обратно (reveal → 0).

val SEARCH_SHIFT = 20.dp
const val SEARCH_ANIM_MS = 300

@Stable
class PickerSearchState {
    var active by mutableStateOf(false)
        private set
    var query by mutableStateOf("")

    /**
     * Pull-to-search: 0 — поле спрятано, 1 — полностью выдвинуто. Меняется
     * жестом (PullToSearchConnection), доводкой и закрытием поиска.
     */
    var reveal by mutableStateOf(0f)
        internal set

    fun open() {
        reveal = 1f
        active = true
    }
    fun close() { active = false }

    /** Плавно довести reveal до [target]; повторный вызов отменяет предыдущий. */
    suspend fun animateRevealTo(target: Float, durationMs: Int = SEARCH_ANIM_MS) {
        if (reveal == target) return
        animate(
            initialValue  = reveal,
            targetValue   = target,
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        ) { v, _ -> reveal = v }
    }
}

// ─── Pull-to-search: NestedScrollConnection ──────────────────────────────────

/** Высота слота поля без нижнего отступа: 8.dp сверху + 42.dp поле. */
val SEARCH_FIELD_SLOT = 50.dp

/** Нижний отступ зоны шапки ПОСЛЕ поля — его закрывает блюр (мягкая плашка). */
val SEARCH_ZONE_BOTTOM_PAD = 14.dp

/** Сколько px reveal-хода даёт 1px протяжки (резинка — поле «тяжелее» пальца). */
private const val PULL_RESISTANCE = 0.55f

/** Порог скорости (px/с), после которого направление fling решает доводку. */
private const val SETTLE_FLING_VELOCITY = 400f

@Composable
fun rememberPullToSearchConnection(search: PickerSearchState): NestedScrollConnection {
    val slotPx = with(LocalDensity.current) { (SEARCH_FIELD_SLOT + SEARCH_ZONE_BOTTOM_PAD).toPx() }
    val scope = rememberCoroutineScope()
    return remember(search, slotPx, scope) {
        PullToSearchConnection(search, slotPx) { block -> scope.launch { block() } }
    }
}

private class PullToSearchConnection(
    private val search: PickerSearchState,
    private val slotPx: Float,
    private val launch: (suspend () -> Unit) -> Job,
) : NestedScrollConnection {

    private var settleJob: Job? = null

    private fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
    }

    // Список едет ВВЕРХ (палец вверх), поле выдвинуто → сначала сворачиваем
    // поле и «съедаем» этот ход, чтобы список и поле не двигались вдвойне.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (search.active || source != NestedScrollSource.UserInput) return Offset.Zero
        val dy = available.y
        val r = search.reveal
        if (dy < 0f && r > 0f) {
            cancelSettle()
            val newR = max(0f, r + dy * PULL_RESISTANCE / slotPx)
            search.reveal = newR
            return Offset(0f, (newR - r) * slotPx / PULL_RESISTANCE)
        }
        return Offset.Zero
    }

    // Список уже упёрся в верх и не забрал ход (оверскролл) → тянем поле.
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (search.active || source != NestedScrollSource.UserInput) return Offset.Zero
        val dy = available.y
        val r = search.reveal
        if (dy > 0f && r < 1f) {
            cancelSettle()
            val newR = min(1f, r + dy * PULL_RESISTANCE / slotPx)
            search.reveal = newR
            return Offset(0f, (newR - r) * slotPx / PULL_RESISTANCE)
        }
        return Offset.Zero
    }

    // Отпустили палец: пока поле на полпути, довести до края ДО того, как
    // список начнёт свой fling. Скорость (не потребляем) решает направление.
    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!search.active) settleIfPartial(available.y)
        return Velocity.Zero
    }

    private fun settleIfPartial(velocityY: Float) {
        val r = search.reveal
        if (r <= 0f || r >= 1f) return
        val target = when {
            velocityY >  SETTLE_FLING_VELOCITY -> 1f
            velocityY < -SETTLE_FLING_VELOCITY -> 0f
            r >= 0.5f -> 1f
            else -> 0f
        }
        cancelSettle()
        settleJob = launch { search.animateRevealTo(target, durationMs = 220) }
    }
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
    val bottomPadPx = with(LocalDensity.current) { SEARCH_ZONE_BOTTOM_PAD.toPx() }

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

            // Поле — вторым: рисуется поверх шапки, пока та гаснет. Пока оно
            // выезжает из-под шапки, рисуется только та его часть, что НИЖЕ
            // нижней кромки шапки (clipRect) — «шторка», без наложения на
            // заголовок и кнопку «назад».
            Box(
                modifier = Modifier.drawWithContent {
                    val slot = size.height + bottomPadPx
                    val r = max(search.reveal, progress.value)
                    clipRect(top = slot * (1f - r)) {
                        this@drawWithContent.drawContent()
                    }
                },
            ) {
                PickerSearchField(search = search, progress = progress, isVisibleHalf = isVisibleHalf)
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val headerPlaceable = measurables[0].measure(loose)
        val fieldPlaceable  = measurables[1].measure(loose)

        // slot = поле + нижний отступ (его закрывает блюр родителя). Видимая
        // доля слота r = max(pull-reveal, прогресс поиска).
        //   • покой:  r = 0 → зона = только шапка, поле спрятано;
        //   • pull:   зона растёт на slot·r, поле выезжает из-под шапки;
        //   • поиск:  p = 1 → шапка схлопнулась, зона = поле + нижний отступ.
        val p = progress.value
        val r = max(search.reveal, p).coerceIn(0f, 1f)
        val slot = fieldPlaceable.height + bottomPadPx
        val headerH = headerPlaceable.height * (1f - p)
        val zoneH = (headerH + slot * r).roundToInt()
        val fieldY = (headerH - slot * (1f - r)).roundToInt()
        layout(constraints.maxWidth, zoneH) {
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
            // Пока поиск закрыт — тап по выдвинутому полю открывает его. Спрятанное
            // или выезжающее поле тапов не ловит (иначе перекроет кнопку «назад»).
            .then(
                if (!active && search.reveal >= 0.98f) Modifier.clickable { search.open() }
                else Modifier,
            )
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
