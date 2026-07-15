package com.schedule.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
//  CascadeEntrance — каскадное появление элементов списка с пружинным отскоком.
//
//  Каждый элемент выезжает со своего края экрана (слева для Files, справа для
//  Bells — совпадает с направлением слайда между вкладками в AppScaffold),
//  с небольшой задержкой относительно предыдущего — отсюда «каскад».
//
//  triggerKey — важная часть: раз оба экрана (Files/Bells) теперь всегда живут
//  в композиции (см. AppScaffold), анимация не может полагаться на «первое
//  появление экрана». Вместо этого AppScaffold увеличивает свой счётчик
//  каждый раз, когда вкладка становится активной, и передаёт его сюда —
//  смена этого числа заново запускает анимацию у всех видимых элементов.
// ══════════════════════════════════════════════════════════════════════════════

enum class CascadeEdge { LEFT, RIGHT, BOTTOM, TOP }

private const val START_OFFSET_PX   = 72f  // горизонтальный старт (LEFT/RIGHT) — язык навигации
private const val START_OFFSET_Y_PX = 46f  // вертикальный старт (BOTTOM/TOP) — язык "контент только что загрузился".
                                            // Меньше горизонтального: если элементов много, лететь издалека
                                            // будет слишком долго и медленно смотреться.
private const val STAGGER_MS        = 60L  // 50-100мс — практика реальных приложений для
                                            // "вау-момента" загрузки контента (официальный Material
                                            // Design рекомендует ≤20мс, но это правило про рутинные
                                            // обновления списков, а не про момент "контент загрузился")
private const val MAX_STAGGER_ITEMS = 10 // дальше 10-го элемента задержка не растёт — иначе долго ждать

@Composable
fun CascadeEntranceItem(
    index: Int,
    triggerKey: Any?,
    enabled: Boolean,
    edge: CascadeEdge,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val startX = when (edge) {
        CascadeEdge.LEFT   -> -START_OFFSET_PX
        CascadeEdge.RIGHT  -> START_OFFSET_PX
        CascadeEdge.BOTTOM, CascadeEdge.TOP -> 0f
    }
    val startY = when (edge) {
        CascadeEdge.BOTTOM -> START_OFFSET_Y_PX
        CascadeEdge.TOP    -> -START_OFFSET_Y_PX
        else               -> 0f
    }

    // remember(triggerKey) — при смене triggerKey создаются новые Animatable,
    // то есть элемент откатывается за край/вниз и проигрывает анимацию заново.
    // Важно: то же самое происходит и БЕЗ смены triggerKey, если сам элемент
    // целиком пересоздаётся — например, вышёл из зоны композиции LazyColumn
    // при прокрутке и вернулся обратно. Раньше это выглядело как "случайный"
    // повторный вход элементов при скролле; теперь это осознанная фича —
    // см. ScrollCascadeState ниже, который подбирает edge/index для такого
    // случая отдельно от первого появления после навигации.
    val offsetX = remember(triggerKey) { Animatable(startX) }
    val offsetY = remember(triggerKey) { Animatable(startY) }
    val alpha   = remember(triggerKey) { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        val delayMs = STAGGER_MS * index.coerceAtMost(MAX_STAGGER_ITEMS)
        delay(delayMs)
        launch {
            offsetX.animateTo(
                targetValue   = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessLow,
                ),
            )
        }
        launch {
            offsetY.animateTo(
                targetValue   = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessLow,
                ),
            )
        }
        launch {
            alpha.animateTo(1f, animationSpec = tween(220))
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            translationX = offsetX.value
            translationY = offsetY.value
            this.alpha   = alpha.value
        },
    ) {
        content()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  ScrollCascadeState — та же каскадная анимация, но для прокрутки списка.
//
//  LazyColumn полностью уничтожает композицию элементов, ушедших далеко за
//  пределы экрана, и создаёт её заново, когда они возвращаются в видимую
//  область — из-за этого CascadeEntranceItem выше "случайно" проигрывал
//  анимацию входа повторно при прокрутке, используя тот же edge, что и вход
//  на экран (включая LEFT после возврата с расписания пары/преподавателя —
//  выглядело нелогично, эффект навигации назад никак не должен быть связан
//  с прокруткой списка).
//
//  Здесь это осознанно разделено на два разных случая:
//   - первое появление ключа в рамках текущего triggerKey — это часть
//     перехода на экран, используется переданный navigationEdge как раньше;
//   - повторное появление того же ключа (пересоздание при прокрутке) —
//     всегда тот же "язык", что и у появления после открытия файла (BOTTOM),
//     инвертированный на TOP при прокрутке вверх, и БЕЗ стаггер-задержки по
//     абсолютному индексу в списке (задержка расчитана на пачку из ~10
//     элементов, а тут за раз обычно возвращается один — с полной задержкой
//     по индексу это выглядело как "почему-то медленно").
// ══════════════════════════════════════════════════════════════════════════════

class ScrollCascadeState internal constructor(
    private val seenKeys: MutableSet<Any>,
    private val scrollingDown: Boolean,
) {
    /** index — 0, если это повторный вход при прокрутке (без стаггера). */
    data class Mount(val edge: CascadeEdge, val index: Int)

    @Composable
    fun resolve(key: Any, index: Int, navigationEdge: CascadeEdge): Mount {
        val isFirstMount = key !in seenKeys
        LaunchedEffect(key) { seenKeys.add(key) }
        return if (isFirstMount) {
            Mount(navigationEdge, index)
        } else {
            Mount(if (scrollingDown) CascadeEdge.BOTTOM else CascadeEdge.TOP, 0)
        }
    }
}

@Composable
fun rememberScrollCascadeState(listState: LazyListState, triggerKey: Any): ScrollCascadeState {
    // Обычный (не-snapshot) MutableSet — реактивность тут не нужна, каждый
    // элемент читает его один раз при своей собственной композиции, которая
    // и так происходит из-за скролла/навигации, а не из-за изменений в сете.
    val seenKeys = remember(triggerKey) { mutableSetOf<Any>() }
    var scrollingDown by remember(triggerKey) { mutableStateOf(true) }

    LaunchedEffect(listState, triggerKey) {
        var lastIndex  = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index != lastIndex || offset != lastOffset) {
                    scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset)
                    lastIndex = index
                    lastOffset = offset
                }
            }
    }

    return ScrollCascadeState(seenKeys, scrollingDown)
}
