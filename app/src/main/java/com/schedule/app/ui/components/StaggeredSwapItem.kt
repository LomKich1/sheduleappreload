package com.schedule.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically

private const val SWAP_STAGGER_MS    = 45L
private const val SWAP_EXIT_MS       = 180
private const val SWAP_ENTER_MS      = 220
private const val SWAP_EXIT_SLIDE_PX = 28

@Composable
fun <T : Any> StaggeredSwapItem(
    index: Int,
    targetState: T,
    enabled: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    if (!enabled) {
        content(targetState)
        return
    }

    // "displayed" отстаёт от targetState на свою индивидуальную задержку —
    // отсюда и эффект "по одной карточке".
    var displayed by remember { mutableStateOf(targetState) }

    LaunchedEffect(targetState) {
        if (targetState != displayed) {
            delay(SWAP_STAGGER_MS * index.coerceAtMost(10))
            displayed = targetState
        }
    }

    AnimatedContent(
        targetState = displayed,
        transitionSpec = {
            fadeIn(tween(SWAP_ENTER_MS)) togetherWith
                    (fadeOut(tween(SWAP_EXIT_MS)) + slideOutVertically(
                        animationSpec = tween(SWAP_EXIT_MS),
                    ) { SWAP_EXIT_SLIDE_PX })
        },
        label = "staggeredSwap",
    ) { state ->
        content(state)
    }
}