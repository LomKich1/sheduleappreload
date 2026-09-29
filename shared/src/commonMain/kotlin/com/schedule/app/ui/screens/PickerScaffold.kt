package com.schedule.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.PickerSearchState
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.components.ScheduleModeToggle
import com.schedule.app.ui.components.ScheduleTogglePlacement
import com.schedule.app.ui.components.SearchHeaderZone
import com.schedule.app.ui.components.blockTouches
import com.schedule.app.ui.components.rememberPullToSearchConnection
import com.schedule.app.ui.theme.LocalAppColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlin.math.roundToInt

// ─── PickerScaffold ─────────────────────────────────────────────────────────
//
// ЕДИНАЯ раскладка слоя пикера (ScheduleScreen / TeacherScheduleScreen делят
// её, чтобы не дублировать инсеты/блюр в двух местах).
//
// Box-модель, НЕ Column: список едет на весь экран и является Haze-ИСТОЧНИКОМ,
// а шапка/тумблер — плавающие оверлеи поверх него с hazeChild. Column со
// weight(1f) (как было в коммите с поиском) сажает список ПОД шапку, и блюрить
// шапке нечего.
//
//   Box(modifier)
//    ├ Box(fillMaxSize, .haze)          ← источник: список + слой результатов
//    ├ TopChrome    (Align.Top)         ← hazeChild → statusBarsPadding →
//    │                                    SearchHeaderZone → [TOP] тумблер
//    └ BottomChrome (Align.Bottom)      ← только при BOTTOM: тумблер над
//                                         union(navigationBars, ime)
//
// Инсеты:
//  • pull-to-search: поле поиска скрыто, выезжает из-под шапки при протяжке
//    списка вниз (PullToSearchConnection в PickerSearch.kt). Зона шапки
//    получает нижний отступ SEARCH_ZONE_BOTTOM_PAD ПОСЛЕ поля — он внутри
//    hazeChild, поэтому под полем остаётся мягкая плашка блюра, а не срез
//    по рамке. Список едет вниз вместе с выдвижением: listTop берётся из
//    live-высоты шапки.
//  • верх — statusBarsPadding стоит ПОСЛЕ hazeChild, поэтому блюр закрывает
//    и зону статус-бара/камеры. У самой ScheduleHeaderRow здесь
//    applyStatusBarsPadding = false — иначе отступ применится дважды и поле
//    поиска (которое в конце анимации встаёт на место шапки) съехало бы.
//  • низ — union(navigationBars, ime), а не два padding подряд: берётся
//    максимум, а не сумма, поэтому тумблер стоит ровно над клавиатурой, а без
//    неё — над навбаром. Корень AppScaffold съедает только ГОРИЗОНТАЛЬНЫЕ
//    системные инсеты, вертикальные до этого места доходят целыми.
//
// Сдвиги по X (counterTranslationX) — через offset{}, не graphicsLayer: Haze
// узнаёт позицию источника через onGloballyPositioned, а он не срабатывает
// при смене одного только transform слоя (см. комментарий в ScheduleHostScreen).

/**
 * Отступы для контента слоя пикера.
 *  • listTop / listBottom — «покоящиеся» значения для обычного списка. Пока
 *    открыт поиск, список погашен, поэтому его отступы намеренно НЕ следуют за
 *    схлопыванием шапки — иначе скрытый список каждый кадр перевёрстывался бы.
 *  • resultsTopPx / resultsBottomPx — живые значения для слоя результатов,
 *    читаются в layout-фазе (без рекомпозиции на каждый кадр анимации клавиатуры).
 */
@Stable
class PickerInsets internal constructor(
    val listTop: Dp,
    val listBottom: Dp,
    liveTopPx: State<Int>,
    bottomChromePx: State<Int>,
    bottomInsets: WindowInsets,
) {
    val resultsTopPx: () -> Int = { liveTopPx.value }
    val resultsBottomPx: Density.() -> Int = { bottomChromePx.value + bottomInsets.getBottom(this) }
}

@Composable
fun PickerScaffold(
    hazeState: HazeState,
    search: PickerSearchState,
    searchProgress: State<Float>,
    isVisibleHalf: Boolean,
    header: ScheduleHeaderInfo,
    mode: ScheduleMode,
    onModeSelect: (ScheduleMode) -> Unit,
    modeSwipeProgress: Float,
    counterTranslationX: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PickerInsets) -> Unit,
) {
    val c = LocalAppColors.current
    val density = LocalDensity.current
    val placement by AppPrefs.scheduleTogglePlacement.collectAsState()
    val toggleOnTop = placement == ScheduleTogglePlacement.TOP

    // Высота верхнего блока: live — как есть (меняется с pull-to-search и
    // схлопыванием шапки), rest — «покоящаяся», только когда поиск полностью
    // закрыт. Rest обновляется через snapshotFlow, а не в onSizeChanged: конец
    // анимации reveal и конец анимации поиска не совпадают по кадру, и
    // onSizeChanged мог бы поймать последнее значение при progress > 0.
    val restTopPx = remember { mutableStateOf(0) }
    val liveTopPx = remember { mutableStateOf(0) }
    LaunchedEffect(search, searchProgress) {
        snapshotFlow { if (!search.active && searchProgress.value == 0f) liveTopPx.value else -1 }
            .collect { if (it >= 0) restTopPx.value = it }
    }
    val pullToSearch = rememberPullToSearchConnection(search)
    // Высота КОНТЕНТА нижнего блока (тумблер + его отступы, без инсетов).
    val bottomChromePx = remember { mutableStateOf(0) }
    LaunchedEffect(toggleOnTop) { if (toggleOnTop) bottomChromePx.value = 0 }

    val navInsets = WindowInsets.navigationBars
    val imeInsets = WindowInsets.ime
    val bottomInsets = remember(navInsets, imeInsets) {
        navInsets.union(imeInsets).only(WindowInsetsSides.Bottom)
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val listTop = with(density) { restTopPx.value.toDp() }
    val listBottom = if (toggleOnTop) {
        80.dp + navBottom
    } else {
        with(density) { bottomChromePx.value.toDp() } + navBottom + 8.dp
    }

    val insets = remember(listTop, listBottom, bottomInsets) {
        PickerInsets(listTop, listBottom, liveTopPx, bottomChromePx, bottomInsets)
    }

    Box(modifier = modifier.fillMaxSize()) {

        // ── Источник блюра ─────────────────────────────────────────────────
        // nestedScroll стоит ВЫШЕ списка: ловит оверскролл любого скролла внутри
        // (pull-to-search) и не мешает горизонтальному свайпу режимов.
        Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToSearch).haze(hazeState)) {
            content(insets)
        }

        // ── Верхний блок: шапка/поле поиска (+ тумблер при TOP) ─────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { size -> liveTopPx.value = size.height }
                .offset { IntOffset(x = (-counterTranslationX).roundToInt(), y = 0) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = c.bg,
                            blurRadius = 20.dp,
                            tint = HazeTint(c.surface.copy(alpha = 0.55f)),
                        ),
                    )
                    .statusBarsPadding(),
            ) {
                SearchHeaderZone(
                    search        = search,
                    progress      = searchProgress,
                    isVisibleHalf = isVisibleHalf,
                ) {
                    Column {
                        ScheduleHeaderRow(
                            header = header,
                            opaqueBackground = false,
                            applyStatusBarsPadding = false,
                        )
                        if (header.isLoading) {
                            LinearProgressIndicator(
                                progress   = { header.progress },
                                modifier   = Modifier.fillMaxWidth().height(2.dp),
                                color      = c.accent,
                                trackColor = c.surface2,
                            )
                        }
                    }
                }
            }

            if (toggleOnTop) {
                // TOP: при поиске тумблер гаснет и схлопывается по высоте.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .collapseWithSearch(searchProgress)
                        .blockTouches(search.active),
                ) {
                    Spacer(Modifier.height(10.dp))
                    PickerToggle(
                        hazeState = hazeState,
                        mode = mode,
                        onModeSelect = onModeSelect,
                        modeSwipeProgress = modeSwipeProgress,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Нижний блок: тумблер при BOTTOM. Поиск его НЕ прячет ───────────
        if (!toggleOnTop) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset { IntOffset(x = (-counterTranslationX).roundToInt(), y = 0) }
                    .windowInsetsPadding(bottomInsets)
                    // onSizeChanged ПОСЛЕ инсетов и ДО padding: меряем тумблер
                    // вместе с его отступами, но без системного инсета.
                    .onSizeChanged { bottomChromePx.value = it.height }
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
            ) {
                PickerToggle(
                    hazeState = hazeState,
                    mode = mode,
                    onModeSelect = onModeSelect,
                    modeSwipeProgress = modeSwipeProgress,
                )
            }
        }
    }
}

@Composable
private fun PickerToggle(
    hazeState: HazeState,
    mode: ScheduleMode,
    onModeSelect: (ScheduleMode) -> Unit,
    modeSwipeProgress: Float,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    ScheduleModeToggle(
        selected = mode,
        onSelect = onModeSelect,
        progress = modeSwipeProgress,
        modifier = modifier,
        opaqueBackground = false,
        hazeModifier = Modifier.hazeChild(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = c.bg,
                blurRadius = 20.dp,
                tint = HazeTint(c.pillBg.copy(alpha = 0.5f)),
            ),
        ),
    )
}

/** Гаснет и схлопывается по высоте вместе с прогрессом поиска. */
private fun Modifier.collapseWithSearch(progress: State<Float>): Modifier =
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val h = (placeable.height * (1f - progress.value)).roundToInt()
            layout(placeable.width, h) { placeable.place(0, 0) }
        }
        .clipToBounds()
        .graphicsLayer { alpha = 1f - progress.value }
