package com.schedule.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import com.schedule.app.data.prefs.AnimPrefs
import com.schedule.app.data.prefs.TabAnimMode
import kotlinx.coroutines.launch
import kotlin.math.abs

// Мёртвая зона для этого жеста — специально БОЛЬШЕ системного touchSlop
// (тот обычно ~8dp, рассчитан на защиту от дрожания пальца при обычном
// тапе). Тут жест на весь экран, а не в узкой зоне у края — без осознанного
// порога любое случайное касание с небольшим смещением (например, при
// скролле списка пар кто-то чуть повёл пальцем не строго вертикально)
// могло бы случайно чуть сдвинуть контент. 24dp — тот же порядок величины,
// что и в Телеграме у свайпа-ответа на сообщение: заметно пальцу, но не
// требует прямо размашистого жеста.
private val DEAD_ZONE_DP = 24.dp

// ══════════════════════════════════════════════════════════════════════════════
//  SwipeBackHandle / rememberSwipeBackHandle — свайп слева направо "как в
//  iOS/Telegram": передний слой (пары) живо следует за пальцем, а вызывающий
//  экран (ScheduleScreen/TeacherScheduleScreen) параллельно рисует "призрак"
//  пикера снизу, тоже двигая его от offsetPx/widthPx — так во время самого
//  драга уже видно, куда возвращаешься, а не пустоту с рывком анимации после
//  отпускания. См. подробности синхронизации в комментарии над PickerSideBody
//  в ScheduleScreen.kt.
//
//  Раньше это была просто rememberSwipeBackModifier(): Modifier — этого хватало
//  для переднего слоя, но снаружи не было доступа к текущему offsetX/ширине,
//  чтобы отрисовать что-то ещё синхронно с драгом. Теперь оборачиваем это в
//  небольшой holder-класс с тем же modifier + сырыми offsetPx/widthPx.
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

/**
 * @param modifier применяется на передний слой (сам контент "пар") — двигает
 *        его вправо 1:1 с пальцем через graphicsLayer.translationX.
 * @param offsetPx текущий сдвиг переднего слоя в пикселях, 0f в покое —
 *        читай снаружи для расчёта позиции "призрака" пикера под ним.
 * @param widthPx измеренная ширина контейнера, 0f пока не измерено/жест выключен.
 */
data class SwipeBackHandle(
    val modifier: Modifier,
    val offsetPx: Float,
    val widthPx: Float,
)

@Composable
fun rememberSwipeBackHandle(
    enabled: Boolean,
    onBack: () -> Unit,
): SwipeBackHandle {
    if (!enabled) return SwipeBackHandle(Modifier, 0f, 0f)

    var widthPx by remember { mutableStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val modifier = Modifier
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
                val slop = DEAD_ZONE_DP.toPx()

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

                    // Спека релиза берётся из AnimPrefs.swipeBackMode — читаем
                    // .value напрямую (не collectAsState выше), чтобы не
                    // держать этот pointerInput-блок привязанным к отдельному
                    // ключу и не ловить устаревшее замыкание: pointerInput тут
                    // перезапускается только по widthPx, а не по смене режима
                    // в дебаг-панели, так что нужен свежий читаемый именно в
                    // момент отпускания пальца, а не значение из замыкания,
                    // захваченного при последней перекомпозиции.
                    val mode = AnimPrefs.swipeBackMode.value
                    val releaseSpec: AnimationSpec<Float> = when (mode) {
                        TabAnimMode.DEFAULT -> tween(
                            durationMillis = AnimPrefs.swipeBackDurationMs.value,
                            easing         = FastOutSlowInEasing,
                        )
                        TabAnimMode.SPRING, TabAnimMode.PARALLAX -> spring(
                            dampingRatio = AnimPrefs.swipeBackSpringDamping.value,
                            stiffness    = AnimPrefs.swipeBackSpringStiffness.value,
                        )
                    }

                    // Всё целиком — ОДНОЙ launch{}, а не по отдельности:
                    // awaitEachGesture — restricted suspension scope (как
                    // sequence{}), напрямую вызывать animateTo/snapTo внутри
                    // него нельзя (именно это и упало на сборке — "Restricted
                    // suspending functions can invoke..."). А порядок здесь
                    // важен — animateTo (доехать) должен ЗАВЕРШИТЬСЯ ДО
                    // onBack(), так что каждый вызов по отдельности в своём
                    // launch{} не подошёл бы: они бы не гарантировали порядок
                    // и не дожидались друг друга. Один launch на scope
                    // (обычный, не restricted) — последовательно, как надо.
                    scope.launch {
                        if (shouldGoBack) {
                            // Дотягиваем контент ВЕСЬ путь вправо (визуально
                            // "ушёл с экрана"), и только ПОСЛЕ этого зовём
                            // onBack() — дальше уже AnimatedContent в
                            // ScheduleScreen/TeacherScheduleScreen сам играет
                            // свой обычный goingBack-переход (пикер въезжает
                            // слева) — но с EnterTransition.None, т.к. к этому
                            // моменту "призрак" пикера уже отрисован в той же
                            // позиции (см. PickerSideBody-ghost в
                            // ScheduleScreen.kt). snapTo(0f) в конце — сбрасываем
                            // себя, чтобы следующий показ этого экрана (снова
                            // выбрали группу) не унаследовал остаточный сдвиг.
                            offsetX.animateTo(
                                targetValue     = widthPx,
                                animationSpec   = releaseSpec,
                                initialVelocity = velocityPxPerSec,
                            )
                            onBack()
                            offsetX.snapTo(0f)
                        } else {
                            offsetX.animateTo(
                                targetValue     = 0f,
                                animationSpec   = releaseSpec,
                                initialVelocity = velocityPxPerSec,
                            )
                        }
                    }
                }
            }
        }

    return SwipeBackHandle(modifier = modifier, offsetPx = offsetX.value, widthPx = widthPx)
}
