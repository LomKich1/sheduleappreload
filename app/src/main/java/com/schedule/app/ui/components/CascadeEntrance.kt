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

enum class CascadeEdge { LEFT, RIGHT }

private const val START_OFFSET_PX  = 72f
private const val STAGGER_MS       = 35L
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

    val startX = if (edge == CascadeEdge.LEFT) -START_OFFSET_PX else START_OFFSET_PX

    // remember(triggerKey) — при смене triggerKey создаётся новый Animatable,
    // то есть элемент откатывается за край и проигрывает анимацию заново.
    val offsetX = remember(triggerKey) { Animatable(startX) }
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
            alpha.animateTo(1f, animationSpec = tween(220))
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            translationX = offsetX.value
            this.alpha   = alpha.value
        },
    ) {
        content()
    }
}
