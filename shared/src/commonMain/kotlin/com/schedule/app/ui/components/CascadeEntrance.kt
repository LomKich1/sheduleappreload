package com.schedule.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

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

// Окно после открытия экрана, в течение которого попадание карточки в кадр
// считается "первым экраном" (видна сразу, без скролла) и получает
// стаггер-задержку по index — см. подробный комментарий у mountMark ниже.
private val MOUNT_WINDOW = 300.milliseconds

// Общий спринг-спек для offsetX/offsetY — раньше дублировался в двух местах
// (обычный въезд и повторный при возврате в кадр), вынесен один раз.
private val entranceSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness    = Spring.StiffnessLow,
)

@Composable
fun CascadeEntranceItem(
    index: Int,
    triggerKey: Any?,
    enabled: Boolean,
    edge: CascadeEdge,
    // null (по умолчанию) — старое поведение: анимация стартует сразу при
    // появлении в композиции, без оглядки на скролл. Годится для списков,
    // целиком помещающихся на экране (пары дня, скелетоны загрузки) — там
    // "видимость" и "появление в композиции" — одно и то же.
    //
    // non-null — анимация стартует ТОЛЬКО когда элемент реально попадает в
    // видимую часть viewport'а. Нужно для длинных Column+verticalScroll
    // списков (пикер группы/преподавателя, см. GroupPickerScreen) — раз все
    // карточки строятся сразу при первом появлении экрана (см. история ниже
    // про отказ от LazyColumn), без этого гейта анимация проигрывалась бы
    // сразу у ВСЕХ карточек одновременно, включая те, что ещё физически ниже
    // экрана и появятся только через полминуты скролла — то есть "каскад"
    // был бы не по месту прокрутки, а по факту загрузки списка.
    //
    // Лямбда, а не голый Rect — читается изнутри snapshotFlow (см. ниже),
    // чтобы отслеживать движение viewport'а/самой карточки БЕЗ пересоздания
    // всего LaunchedEffect на каждый кадр скролла (см. подробности там же).
    viewportBoundsPx: (() -> Rect?)? = null,
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
    // То же самое происходит и БЕЗ смены triggerKey, если сам элемент целиком
    // пересоздаётся — например, вышёл из зоны композиции LazyColumn при
    // прокрутке и вернулся обратно. Раньше от этого "случайно" переигрывался
    // повторный вход при скролле у пикеров группы/преподавателя — решалось
    // отдельным ScrollCascadeState (см. историю ниже), но с переходом этих
    // пикеров на обычный Column+verticalScroll пересоздания при скролле
    // физически больше не происходит, так что для НИХ проблема снята сама
    // собой. Если где-то ещё в будущем понадобится CascadeEntranceItem внутри
    // настоящего LazyColumn — этот сценарий (повторный вход при скролле)
    // снова станет актуален, и придётся либо восстановить похожий механизм,
    // либо сознательно с ним смириться.
    val offsetX = remember(triggerKey) { Animatable(startX) }
    val offsetY = remember(triggerKey) { Animatable(startY) }
    val alpha   = remember(triggerKey) { Animatable(0f) }

    // Собственные координаты карточки в окне — обновляются на каждый layout-
    // проход (в т.ч. каждый кадр скролла). Именно поэтому ниже читаются через
    // snapshotFlow, а не как ключ LaunchedEffect — ключ пересоздавал бы весь
    // эффект (и отменял бы уже идущий delay/animateTo) на каждый такой кадр.
    var itemBoundsPx by remember(triggerKey) { mutableStateOf<Rect?>(null) }

    LaunchedEffect(triggerKey) {
        val getViewportBounds = viewportBoundsPx
        if (getViewportBounds == null) {
            // Старое поведение — без гейта по видимости, один раз при
            // появлении в композиции.
            delay(STAGGER_MS * index.coerceAtMost(MAX_STAGGER_ITEMS))
            launch { offsetX.animateTo(0f, entranceSpringSpec) }
            launch { offsetY.animateTo(0f, entranceSpringSpec) }
            launch { alpha.animateTo(1f, animationSpec = tween(220)) }
            return@LaunchedEffect
        }

        // Гейт по видимости — но теперь не "первый раз и забыли" (first{}),
        // а живое отслеживание входа/выхода из кадра (collect{}), пока жив
        // сам triggerKey. Пролистал вниз, элемент ушёл за край — тихо
        // откатываем его в стартовое положение (он всё равно не виден, тут
        // анимировать нечего). Вернулся в кадр (хоть сверху, хоть снизу) —
        // снова проигрываем въезд, как в самый первый раз.
        //
        // mountMark/MOUNT_WINDOW — БАГ, который был здесь раньше: стаггер-
        // задержка (STAGGER_MS * index) должна работать только для "волны"
        // первого экрана (карточки, видимые сразу при открытии, БЕЗ
        // скролла) — они действительно должны въезжать одна за другой. Но
        // условие "hasAnimatedOnce" защищало только от ПОВТОРНОГО триггера
        // (ушёл скроллом — вернулся), а не от ПЕРВОГО — то есть у карточки
        // с, скажем, index=30 задержка всё равно считалась как
        // STAGGER_MS * index.coerceAtMost(MAX_STAGGER_ITEMS) = 60×10 = 600мс
        // — и это ПОСЛЕ того, как она уже реально попала в кадр при
        // скролле. Результат — карточка визуально появляется с заметным
        // лагом относительно самого скролла, а не сразу.
        //
        // Фикс — стаггер применяется только если карточка попала в кадр в
        // первые MOUNT_WINDOW после открытия экрана (это и есть "первый
        // экран", виден без скролла). Всё, что показалось в кадре позже —
        // хоть при первом скролле, хоть при повторном — появляется СРАЗУ,
        // без искусственной задержки: сам жест скролла уже раскрывает
        // карточки по одной, добавочная задержка тут читалась бы только
        // как лишний лаг.
        val mountMark = TimeSource.Monotonic.markNow()
        var wasVisible = false
        snapshotFlow { itemBoundsPx to getViewportBounds.invoke() }
            .collect { (item, viewport) ->
                val isVisible = item != null && viewport != null &&
                    item.top < viewport.bottom && item.bottom > viewport.top

                if (isVisible && !wasVisible) {
                    val isInitialWave = mountMark.elapsedNow() < MOUNT_WINDOW
                    launch {
                        if (isInitialWave) {
                            delay(STAGGER_MS * index.coerceAtMost(MAX_STAGGER_ITEMS))
                        }
                        launch { offsetX.animateTo(0f, entranceSpringSpec) }
                        launch { offsetY.animateTo(0f, entranceSpringSpec) }
                        launch { alpha.animateTo(1f, animationSpec = tween(220)) }
                    }
                } else if (!isVisible && wasVisible) {
                    // Ушёл из кадра — откат БЕЗ анимации (snapTo, не
                    // animateTo): его всё равно никто не видит, анимировать
                    // отступление за экран незачем, только тратить кадры.
                    offsetX.snapTo(startX)
                    offsetY.snapTo(startY)
                    alpha.snapTo(0f)
                }
                wasVisible = isVisible
            }
    }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                if (viewportBoundsPx != null) {
                    itemBoundsPx = coords.boundsInWindow()
                }
            }
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                this.alpha   = alpha.value
            },
    ) {
        content()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  История: раньше тут жил ScrollCascadeState — костыль под LazyColumn в
//  пикерах группы/преподавателя (ScheduleScreen.kt/TeacherScheduleScreen.kt).
//  Он различал "первое появление карточки" (вход на экран) от "пересоздания
//  при прокрутке" (LazyColumn уничтожает и заново создаёт композицию
//  элементов, ушедших за пределы экрана), чтобы карточка не переигрывала
//  анимацию входа заново каждый раз, когда снова попадала в вьюпорт.
//
//  Удалено вместе с самим переходом этих двух пикеров с LazyColumn на
//  обычный Column+verticalScroll (список групп/преподавателей конечный и
//  небольшой — виртуализация обходилась дороже, чем просто держать все
//  карточки в памяти, и была прямой причиной подтормаживаний при скролле).
//  Без пересоздания композиции при скролле сам класс не нужен — все
//  использования были только в этих двух местах.
//
//  НО у Column+verticalScroll оказался СВОЙ побочный эффект (другая сторона
//  той же монеты): раз все карточки строятся сразу при первом появлении
//  экрана, а не лениво по мере скролла — LaunchedEffect(triggerKey) тоже
//  запускался у ВСЕХ карточек сразу, включая те, что физически ниже экрана.
//  Анимация "въезда" проигрывалась по факту загрузки списка, а не по факту
//  попадания в кадр — до карточек в середине/конце длинного списка долистать
//  успевал уже после того, как их анимация давно отыграла впустую.
//  Решение — не возвращать виртуализацию, а просто ГЕЙТИТЬ старт анимации
//  видимостью: см. параметр viewportBoundsPx выше.
// ══════════════════════════════════════════════════════════════════════════════
