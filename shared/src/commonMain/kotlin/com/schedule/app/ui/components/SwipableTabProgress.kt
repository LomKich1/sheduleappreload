package com.schedule.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.schedule.app.data.prefs.TabAnimMode
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Дефолтный visibilityThreshold у spring() — Spring.DefaultDisplacementThreshold
 * = 0.01f. Он рассчитан на типичные Compose-анимации, где Float — это что-то
 * вроде dp/пикселей (там 0.01 единицы правда незаметно). У НАС Float — это
 * progress на шкале 0.0..1.0 на всю ширину экрана: те же 0.01 — это уже 1%
 * от всей дистанции, на широком экране легко 5-10px. Пружина считала себя
 * "доехавшей" и завершала симуляцию заметно НЕ доезжая до финала — а
 * animateTo() при успешном завершении гарантированно доставляет .value
 * ровно до targetValue, так что оставшиеся проценты долетали одним
 * мгновенным скачком в последнем кадре — то самое дёрганье в конце
 * анимации переключения по пиллу/кнопке.
 *
 * Порог занижен на два порядка относительно дефолта — под нашу реальную
 * шкалу величин, а не дефолт, рассчитанный на dp.
 */
private const val PROGRESS_VISIBILITY_THRESHOLD = 0.0001f

/**
 * Переводит "длительность в мс" (интуитивный параметр tween, каким его
 * настраивали в дебаг-панели) в stiffness критически задемпфированного
 * spring — та же модель осциллятора, что использует сам Compose внутри
 * spring() (масса=1, damping=1 → критическое затухание, без овершута).
 *
 * ω = 2π / T (T — период в секундах), stiffness = ω² — стандартная формула
 * для натуральной частоты осциллятора. Не претендует на физическую точность
 * до последнего кадра (Compose всё равно сходится по своему
 * visibilityThreshold, не по чистому периоду), но даёт интуитивно верное
 * направление: больше мс → меньше stiffness → мягче и медленнее, как и
 * ожидается от слайдера "длительность".
 */
private fun durationMsToStiffness(durationMs: Int): Float {
    val seconds = durationMs.coerceAtLeast(1) / 1000f
    val omega = (2f * kotlin.math.PI.toFloat()) / seconds
    return omega * omega
}

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
        // DEFAULT раньше был на tween() — а tween() физически не умеет
        // работать с initialVelocity (чистая time+easing интерполяция,
        // скорости пальца там просто некуда деться). Из-за этого "импульс"
        // свайпа ощущался только в SPRING/PARALLAX (они и так были на
        // spring()), а тут — деревянно, независимо от резкости флика.
        //
        // Переводим DEFAULT тоже на spring(): DampingRatioNoBouncy даёт
        // похожий на tween характер "без отскока", а stiffness выводим из
        // старого tweenDurationMs по стандартной формуле критически
        // задемпфированного осциллятора — слайдер длительности в
        // дебаг-панели продолжает работать интуитивно (больше мс → мягче),
        // просто теперь это честный spring с поддержкой скорости.
        TabAnimMode.DEFAULT -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = durationMsToStiffness(tweenDurationMs),
            visibilityThreshold = PROGRESS_VISIBILITY_THRESHOLD,
        )
        TabAnimMode.SPRING,
        TabAnimMode.PARALLAX -> spring(
            dampingRatio = springDamping,
            stiffness = springStiffness,
            visibilityThreshold = PROGRESS_VISIBILITY_THRESHOLD,
        )
    }

    // Тап-триггер — тот же контракт, что был у animateFloatAsState: изменился
    // activeIndex (или сами параметры кривой в debug-панели) — едем к цели,
    // подхватывая скорость флика, если она есть (см. pendingVelocity).
    LaunchedEffect(activeIndex, animMode, tweenDurationMs, springDamping, springStiffness) {
        val v = pendingVelocity
        pendingVelocity = 0f
        animatable.animateTo(targetProgress, animSpec, initialVelocity = v)
    }

    // ─── Драг-модификатор ───────────────────────────────────────────────────
    //
    // ВАЖНО (баг: "во время свайпа палец повело по вертикали — жест
    // сбросился"): раньше здесь стоял обычный detectHorizontalDragGestures —
    // удобная обёртка, но она слушает события на PointerEventPass.Main.
    // Main-pass идёт по дереву СНИЗУ ВВЕРХ (сперва дети, потом родители).
    // Пикер группы/препода — Column + verticalScroll, лежит ВНУТРИ этой же
    // свайпаемой области, то есть он ребёнок по отношению к этому модификатору.
    // Если во время уже идущего горизонтального драга палец уходит по
    // вертикали, внутренний verticalScroll на СВОЁМ Main-pass успевает
    // среагировать раньше нас (мы родитель — наш Main-pass выполняется позже)
    // и помечает change как consumed. detectHorizontalDragGestures, увидев
    // "чужое" потребление, считает жест отменённым — onDragCancel, свайп
    // визуально сбрасывается, хотя палец всё ещё зажат и явно тянет по
    // горизонтали. Побеждал не тот, кто раньше начал жест, а тот, кто ниже
    // в дереве.
    //
    // Фикс — переехать на PointerEventPass.Initial. Initial-pass идёт СВЕРХУ
    // ВНИЗ (родители раньше детей), так что наш обработчик, будучи предком
    // verticalScroll-пикера, физически получает каждое сырое событие раньше
    // него. Мы сами вручную определяем доминирующую ось по touch slop:
    //  — если победила горизонталь — "запираемся" (locked=true) и с этого
    //    кадра принудительно consume()'им событие на Initial-pass ДО того,
    //    как Main-pass дочернего списка вообще успеет его увидеть — списку
    //    физически нечего consume'ить, скролла не будет, и наш жест уже
    //    не может быть отменён его конкуренцией, вне зависимости от того,
    //    насколько сильно потом гуляет вертикальная составляющая;
    //  — если победила вертикаль — просто выходим, ничего не consume'им —
    //    список получает событие нетронутым на своём обычном Main-pass и
    //    скроллится как обычно. Обычный вертикальный скролл списка групп
    //    этим фиксом не задет.
    val dragModifier =
        if (!dragEnabled || widthPx <= 0f) {
            Modifier
        } else {
            Modifier.pointerInput(widthPx, activeIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val pointerId = down.id

                    scope.launch { animatable.stop() }

                    val velocityTracker = VelocityTracker()
                    // Сигналим направление не больше одного раза за жест —
                    // тот же контракт, что был у directionSignaled раньше.
                    var directionSignaled = false
                    // Определились ли уже с осью вообще (locked — горизонталь
                    // "наша", settled — вертикаль "не наша", дальше не лезем).
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
                                if (abs(totalDx) > abs(totalDy)) {
                                    locked = true
                                    velocityTracker.resetTracking()
                                } else {
                                    // Вертикаль — отдаём жест списку/скроллу,
                                    // дальше этот gesture-loop нас не касается.
                                    settled = true
                                }
                            }
                        }

                        if (locked) {
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            // Микро-порог тот же, что и раньше — ловим
                            // направление максимально рано, задолго до того
                            // как соседняя страница физически появится в кадре.
                            //
                            // towardPage считается ЧИСТО по знаку — если
                            // дёрнули в сторону, где и так уже progress=0/1,
                            // towardPage совпадёт с activeIndex — проверка
                            // ниже отсекает этот случай (дрожание пальца на
                            // месте не должно сбрасывать каскад уже видимого,
                            // активного экрана).
                            if (!directionSignaled && abs(dx) > 0.5f) {
                                directionSignaled = true
                                val towardPage = if (dx < 0) 1 else 0
                                if (towardPage != activeIndex) {
                                    onDragTowardPage?.invoke(towardPage)
                                }
                            }

                            val delta = dx / widthPx
                            val newValue = (animatable.value - delta).coerceIn(0f, 1f)
                            scope.launch { animatable.snapTo(newValue) }
                        }
                    }

                    // Финализация — только если реально были "нашим" жестом
                    // (locked). Если жест оказался вертикальным (settled) или
                    // палец поднялся, не успев пересечь slop ни в одну из
                    // осей — прогресс никуда не двигался, доезжать некуда.
                    if (locked) {
                        val velocityPxPerSec = velocityTracker.calculateVelocity().x
                        // Скорость в единицах прогресса/сек (а не px/сек) —
                        // чтобы передать её как initialVelocity в animateTo
                        // ниже. Знак инвертирован по той же причине, что и
                        // delta выше: палец влево (velocity < 0) должен
                        // ДОБАВЛЯТЬ к прогрессу.
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

                        // Доезд/откат — В ЛЮБОМ случае с реальной скоростью
                        // пальца на выходе, а не с нуля.
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
                    } else {
                        // Жест оказался вертикальным (settled) или палец
                        // поднялся раньше, чем определилась ось (обычный тап).
                        // stop() выше мог оборвать ещё не доехавшую анимацию
                        // доезда/отката от предыдущего переключения — без
                        // locked=true доездом уже некому заняться, поэтому
                        // досчитываем её сами, иначе прогресс так и останется
                        // висеть на промежуточном значении.
                        scope.launch { animatable.animateTo(targetProgress, animSpec) }
                    }
                }
            }
        }

    return SwipableProgressState(progress = animatable.value, dragModifier = dragModifier)
}

data class SwipableProgressState(
    val progress: Float,
    val dragModifier: Modifier,
)
