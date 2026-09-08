package com.schedule.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
// ══════════════════════════════════════════════════════════════════════════════
