package com.schedule.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// ─── Swipe-to-dismiss (Telegram-style "выйти из чата") ────────────────────────
//
// Это НЕ переиспользование rememberSwipableProgress из SwipableTabProgress.kt —
// сознательное решение после обсуждения с пользователем. rememberSwipableProgress
// рассчитан на ДВА одновременно смонтированных слоя с общим progress: Float
// (0..1), которые взаимно сдвигаются — это подходит для пар типа Files↔Bells
// или Ученики↔Преподаватели, где обе "страницы" вечно живы и переключение
// симметрично в обе стороны.
//
// У нас другой случай: экран расписания пар/препода СОЗДАЁТСЯ при тапе по
// карточке в пикере и РЕАЛЬНО УДАЛЯЕТСЯ из композиции (вместе со своим
// PairsViewModel) при свайпе назад — обратного перехода свайпом нет, только
// тапом. Ровно как выход из чата в Telegram: свайп с любой точки экрана
// вправо → чат закрывается и выгружается, назад в чат свайпом не попасть.
//
// Поэтому здесь — одноразовый жест-дисмисс, а не двухстраничный progress:
// свой Animatable, свой overscroll-lock по оси, свой threshold/velocity-релиз.
// Общее с SwipableTabProgress — только сам принцип "дедзона → лок оси на
// PointerEventPass.Initial → threshold ИЛИ velocity".

private val DEAD_ZONE = 24.dp

/**
 * Держит анимируемое смещение по X и позволяет закрыть экран либо жестом
 * (см. [Modifier.swipeToDismiss]), либо программно — из кнопки "назад" в
 * шапке или системного BackHandler'а, — доигрывая ТУ ЖЕ анимацию выезда,
 * а не мгновенно убирая контент.
 */
class SwipeDismissState internal constructor(
    private val scope: CoroutineScope,
    private val onDismissed: () -> Unit,
) {
    internal val offsetX = Animatable(0f)
    internal var widthPx by mutableStateOf(0f)

    /** Кнопка "назад" / системный back-жест — не свайп пальцем. */
    fun dismiss() {
        scope.launch {
            val target = if (widthPx > 0f) widthPx else 1f
            offsetX.animateTo(
                targetValue   = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMedium,
                ),
            )
            onDismissed()
        }
    }

    internal fun snapTo(value: Float) {
        scope.launch { offsetX.snapTo(value.coerceIn(0f, widthPx.coerceAtLeast(1f))) }
    }

    internal fun settleBack() {
        scope.launch {
            offsetX.animateTo(
                targetValue   = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessLow,
                ),
            )
        }
    }

    internal fun settleDismiss(velocity: Float) {
        scope.launch {
            offsetX.animateTo(
                targetValue     = widthPx,
                animationSpec   = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMedium,
                ),
                initialVelocity = velocity,
            )
            onDismissed()
        }
    }
}

@Composable
fun rememberSwipeDismissState(onDismissed: () -> Unit): SwipeDismissState {
    val scope         = rememberCoroutineScope()
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    return remember { SwipeDismissState(scope) { latestOnDismissed() } }
}

/**
 * Живой свайп слева направо с любой точки экрана — палец таскает контент за
 * собой (graphicsLayer.translationX), отпускание за порогом дистанции ИЛИ
 * с флик-скоростью доигрывает уход за край и дёргает onDismissed; иначе —
 * пружиной обратно в 0.
 */
fun Modifier.swipeToDismiss(state: SwipeDismissState, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    return this
        .onSizeChanged { state.widthPx = it.width.toFloat() }
        .graphicsLayer { translationX = state.offsetX.value }
        .pointerInput(state) {
            val slop = DEAD_ZONE.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id

                val velocityTracker = VelocityTracker()
                var locked   = false
                var settled  = false
                var totalDx  = 0f
                var totalDy  = 0f

                while (true) {
                    val event  = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break

                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y

                    if (!locked && !settled) {
                        totalDx += dx
                        totalDy += dy
                        if (abs(totalDx) > slop || abs(totalDy) > slop) {
                            if (abs(totalDx) > abs(totalDy) && totalDx > 0f) {
                                locked = true
                                velocityTracker.resetTracking()
                            } else {
                                settled = true // вертикальный/влево — не наш жест, отпускаем скролл
                            }
                        }
                    }

                    if (locked) {
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        state.snapTo(state.offsetX.value + dx)
                    }
                }

                if (locked) {
                    val velocity          = velocityTracker.calculateVelocity().x
                    val distanceThreshold = state.widthPx * 0.35f
                    val flickThreshold    = 800f
                    val shouldDismiss = state.offsetX.value > distanceThreshold || velocity > flickThreshold

                    if (shouldDismiss) state.settleDismiss(velocity) else state.settleBack()
                }
            }
        }
}
