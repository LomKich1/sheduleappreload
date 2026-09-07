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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
 *
 * onDragTowardPage — стреляет РОВНО ОДИН раз за весь жест, в момент когда
 * направление драга определилось (первое движение пальца за микро-порог),
 * а не когда свайп уже завершился. Раньше каскадная анимация элементов
 * (см. CascadeEntranceItem) перезапускалась либо никогда (чтобы не мигало),
 * либо в момент завершения свайпа — а раз оба экрана всегда смонтированы,
 * в момент завершения свайпа контент уже был виден в готовом виде (проехал
 * весь путь пальцем), и сброс в скрытое состояние читался как "мигание":
 * появилось → резко пропало → появилась анимация. Тригеря сброс на СТАРТЕ
 * направления, а не на финише — элементы уже скрыты и анимируются START_OFFSET
 * → 0 задолго до того, как физически доедут до видимой части экрана (сам
 * драг обычно занимает сотни миллисекунд — этого с запасом хватает).
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
    onDragTowardPage: ((Int) -> Unit)? = null,
): SwipableProgressState {
    val targetProgress = activeIndex.toFloat()
    val animatable = remember { Animatable(targetProgress) }
    val scope = rememberCoroutineScope()

    // Скорость флика, "отложенная" до следующего срабатывания LaunchedEffect
    // ниже — нужна, когда доезд до соседней страницы запускается НЕ прямым
    // animateTo из жеста, а реакцией на смену activeIndex (через onSwitch).
    // Обнуляется сразу после чтения — тап по кнопке/пиллу не должен получать
    // чужую скорость от предыдущего свайпа.
    var pendingVelocity by remember { mutableStateOf(0f) }

    val animSpec: AnimationSpec<Float> = when (animMode) {
        TabAnimMode.DEFAULT -> tween(tweenDurationMs, easing = FastOutSlowInEasing)
        TabAnimMode.SPRING,
        TabAnimMode.PARALLAX -> spring(dampingRatio = springDamping, stiffness = springStiffness)
    }

    // Тап-триггер — тот же контракт, что был у animateFloatAsState: изменился
    // activeIndex (или сами параметры кривой в debug-панели) — едем к цели,
    // подхватывая скорость флика, если она есть (см. pendingVelocity).
    LaunchedEffect(activeIndex, animMode, tweenDurationMs, springDamping, springStiffness) {
        val v = pendingVelocity
        pendingVelocity = 0f
        animatable.animateTo(targetProgress, animSpec, initialVelocity = v)
    }

    val dragModifier =
        if (!dragEnabled || widthPx <= 0f) {
            Modifier
        } else {
            Modifier.pointerInput(widthPx, activeIndex) {
                val velocityTracker = VelocityTracker()
                // Сброс на каждый новый жест (onDragStart) — направление
                // сигналим не больше одного раза за один непрерывный драг,
                // даже если палец подёргался туда-сюда до фактического порога.
                var directionSignaled = false

                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        directionSignaled = false
                        scope.launch { animatable.stop() }
                    },
                    onDragCancel = {
                        scope.launch { animatable.animateTo(targetProgress, animSpec) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        // Микро-порог — не сигналим на дрожание пальца на месте,
                        // но и не ждём сколько-нибудь заметного смещения: цель —
                        // поймать направление максимально рано, задолго до того
                        // как соседняя страница физически появится в кадре.
                        //
                        // towardPage считается ЧИСТО по знаку — если дёрнули в
                        // сторону, где и так уже progress=0/1 (упирается в
                        // coerceIn и никуда реально не двигает), towardPage
                        // совпадёт с самим activeIndex. Проверка ниже отсекает
                        // именно этот случай — иначе дрожание пальца на месте
                        // сбрасывало бы каскад у уже видимого, активного экрана.
                        if (!directionSignaled && kotlin.math.abs(dragAmount) > 0.5f) {
                            directionSignaled = true
                            val towardPage = if (dragAmount < 0) 1 else 0
                            if (towardPage != activeIndex) {
                                onDragTowardPage?.invoke(towardPage)
                            }
                        }

                        val delta = dragAmount / widthPx
                        val newValue = (animatable.value - delta).coerceIn(0f, 1f)
                        scope.launch { animatable.snapTo(newValue) }
                    },
                    onDragEnd = {
                        val velocityPxPerSec = velocityTracker.calculateVelocity().x
                        // Скорость в единицах прогресса/сек (а не px/сек) — чтобы
                        // передать её как initialVelocity в animateTo ниже. Знак
                        // инвертирован по той же причине, что и delta выше: палец
                        // влево (velocity < 0) должен ДОБАВЛЯТЬ к прогрессу.
                        val velocityProgressPerSec = -velocityPxPerSec / widthPx

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

                        // Доезд/откат — В ЛЮБОМ случае с реальной скоростью пальца
                        // на выходе, а не с нуля: раньше animateTo стартовал так,
                        // будто отпустили неподвижно, даже после резкого флика.
                        when {
                            shouldAdvance -> {
                                pendingVelocity = velocityProgressPerSec
                                onSwitch(1)
                            }
                            shouldRetreat -> {
                                pendingVelocity = velocityProgressPerSec
                                onSwitch(0)
                            }
                            else -> scope.launch {
                                animatable.animateTo(targetProgress, animSpec, initialVelocity = velocityProgressPerSec)
                            }
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
