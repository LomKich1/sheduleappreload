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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

// ══════════════════════════════════════════════════════════════════════════════
//  rememberSwipeBackModifier — свайп слева направо "как в iOS", с живым
//  следованием контента за пальцем: потянул вправо мимо порога/резко
//  флик — доезжает и вызывает onBack(), не дотянул — пружинит обратно на 0.
//
//  Жест ловится с ЛЮБОЙ точки экрана (не только от края) — используется
//  на экране расписания пар конкретной группы/преподавателя, чтобы выйти
//  обратно к пикеру, не дотягиваясь до стрелки "назад" в шапке.
//
//  Та же Initial-pass axis-lock схема, что и в SwipableTabProgress.kt (см.
//  подробный комментарий там про то, почему именно Initial, а не обычный
//  Main-pass detectHorizontalDragGestures) — список пар дня сам по себе
//  может быть скроллящимся (много пар за день), и без Initial-pass захвата
//  вертикальный скролл внутри мог бы "отменять" уже идущий горизонтальный
//  драг на середине жеста, ровно как это было со свайпом Ученики/Преподы.
//
//  В отличие от SwipableTabProgress — это НЕ "две страницы туда-обратно",
//  а одноразовый жест: локаем только НАПРАВЛЕНИЕ вправо (totalDx > 0),
//  влево/вертикаль — не наш жест, просто ничего не consume'им, отдаём как
//  есть (вертикаль уйдёт в скролл списка пар, левый свайп — никуда, его
//  тут просто не бывает).
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun rememberSwipeBackModifier(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier {
    if (!enabled) return Modifier

    var widthPx by remember { mutableStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    return Modifier
        .onSizeChanged { widthPx = it.width.toFloat() }
        .graphicsLayer { translationX = offsetX.value }
        .pointerInput(widthPx) {
            if (widthPx <= 0f) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                scope.launch { offsetX.stop() }

                val velocityTracker = VelocityTracker()
                var locked = false
                var settled = false
                var totalDx = 0f
                var totalDy = 0f
                val slop = viewConfiguration.touchSlop

                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break

                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y

                    if (!locked && !settled) {
                        totalDx += dx
                        totalDy += dy
                        if (abs(totalDx) > slop || abs(totalDy) > slop) {
                            // Наш жест — ТОЛЬКО горизонталь вправо. Влево и
                            // вертикаль (в т.ч. скролл списка пар) — не наш,
                            // отдаём как есть, ничего не consume'им.
                            if (abs(totalDx) > abs(totalDy) && totalDx > 0f) {
                                locked = true
                                velocityTracker.resetTracking()
                            } else {
                                settled = true
                            }
                        }
                    }

                    if (locked) {
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val newValue = (offsetX.value + dx).coerceIn(0f, widthPx)
                        scope.launch { offsetX.snapTo(newValue) }
                    }
                }

                if (locked) {
                    val velocityPxPerSec = velocityTracker.calculateVelocity().x
                    val distanceThreshold      = widthPx * 0.35f
                    val flickThresholdPxPerSec = 800f
                    val shouldGoBack = offsetX.value > distanceThreshold ||
                        velocityPxPerSec > flickThresholdPxPerSec

                    if (shouldGoBack) {
                        // Дотягиваем контент ВЕСЬ путь вправо (визуально
                        // "ушёл с экрана"), и только ПОСЛЕ этого зовём
                        // onBack() — дальше уже AnimatedContent в
                        // ScheduleScreen/TeacherScheduleScreen сам играет
                        // свой обычный goingBack-переход (пикер въезжает
                        // слева). snapTo(0f) в конце — сбрасываем себя,
                        // чтобы следующий показ этого экрана (снова выбрали
                        // группу) не унаследовал остаточный сдвиг.
                        offsetX.animateTo(
                            targetValue    = widthPx,
                            animationSpec  = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness    = Spring.StiffnessMedium,
                            ),
                            initialVelocity = velocityPxPerSec,
                        )
                        onBack()
                        offsetX.snapTo(0f)
                    } else {
                        offsetX.animateTo(
                            targetValue   = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessLow,
                            ),
                            initialVelocity = velocityPxPerSec,
                        )
                    }
                }
            }
        }
}
