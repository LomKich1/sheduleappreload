package com.schedule.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.schedule.app.data.prefs.TabAnimMode
import kotlinx.coroutines.launch

/**
 * Прогресс 0..1 между двумя "страницами" (0 — фоновый/левый слой, 1 —
 * передний/правый), общий для Files↔Bells и Ученики↔Преподаватели — раньше
 * в обоих местах это был чистый animateFloatAsState(target), реагирующий
 * ТОЛЬКО на смену activeIndex извне (тап по кнопке/пиллу/тумблеру).
 *
 * Теперь прогресс живёт в Animatable, у которого два независимых источника
 * движения:
 *  — тап извне: activeIndex меняется → LaunchedEffect ниже гонит Animatable
 *    к новому target той же кривой (tween/spring), что и раньше — поведение
 *    "по кнопке" не изменилось ни на пиксель;
 *  — драг пальцем: во время жеста Animatable двигается через snapTo строго
 *    синхронно с пальцем (1:1, без интерполяции). На отпускании — если
 *    пройдена дистанция ИЛИ скорость выше порога (обычный fling-порог, как
 *    у ViewPager/HorizontalPager) — вызывается onSwitch(другая страница),
 *    что меняет activeIndex и снова запускает тот же LaunchedEffect (доезд
 *    доедет от текущей позиции пальца, а не с нуля — швов не будет). Если
 *    порог не пройден — просто едем обратно к текущему activeIndex.
 *
 * Направление специально совпадает с "контент едет за пальцем": палец влево
 * (dragAmount < 0) → progress растёт (0→1), палец вправо → progress падает.
 */
@Composable
fun rememberSwipableProgress(
    activeIndex: Int,
    onSwitch: (Int) -> Unit,
    widthPx: Float,
    animMode: TabAnimMode,
    tweenDurationMs: Int,
    springDamping: Float,
    springStiffness: Float,
    dragEnabled: Boolean = true,
): SwipableProgressState {
    val targetProgress = activeIndex.toFloat()
    val animatable = remember { Animatable(targetProgress) }
    val scope = rememberCoroutineScope()

    val animSpec: AnimationSpec<Float> = when (animMode) {
        TabAnimMode.DEFAULT -> tween(tweenDurationMs, easing = FastOutSlowInEasing)
        TabAnimMode.SPRING,
        TabAnimMode.PARALLAX -> spring(dampingRatio = springDamping, stiffness = springStiffness)
    }

    // Тап-триггер — тот же контракт, что был у animateFloatAsState: изменился
    // activeIndex (или сами параметры кривой в debug-панели) — едем к цели.
    LaunchedEffect(activeIndex, animMode, tweenDurationMs, springDamping, springStiffness) {
        animatable.animateTo(targetProgress, animSpec)
    }

    val dragModifier =
        if (!dragEnabled || widthPx <= 0f) {
            Modifier
        } else {
            Modifier.pointerInput(widthPx, activeIndex) {
                val velocityTracker = VelocityTracker()

                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        scope.launch { animatable.stop() }
                    },
                    onDragCancel = {
                        scope.launch { animatable.animateTo(targetProgress, animSpec) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val delta = dragAmount / widthPx
                        val newValue = (animatable.value - delta).coerceIn(0f, 1f)
                        scope.launch { animatable.snapTo(newValue) }
                    },
                    onDragEnd = {
                        val velocityPxPerSec = velocityTracker.calculateVelocity().x
                        val current = animatable.value
                        val movedFromActive = current - activeIndex.toFloat()

                        // Пороги — как у обычного fling-жеста: либо утащил
                        // за треть ширины, либо резко "стрельнул" пальцем,
                        // даже если сместился не сильно.
                        val distanceThreshold = 0.35f
                        val flickThresholdPxPerSec = 800f

                        val shouldAdvance = activeIndex == 0 &&
                            (movedFromActive > distanceThreshold || velocityPxPerSec < -flickThresholdPxPerSec)
                        val shouldRetreat = activeIndex == 1 &&
                            (movedFromActive < -distanceThreshold || velocityPxPerSec > flickThresholdPxPerSec)

                        when {
                            shouldAdvance -> onSwitch(1)
                            shouldRetreat -> onSwitch(0)
                            else -> scope.launch { animatable.animateTo(targetProgress, animSpec) }
                        }
                    },
                )
            }
        }

    return SwipableProgressState(progress = animatable.value, dragModifier = dragModifier)
}

data class SwipableProgressState(
    val progress: Float,
    val dragModifier: Modifier,
)
