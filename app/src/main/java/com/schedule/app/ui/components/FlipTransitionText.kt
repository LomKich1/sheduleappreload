package com.schedule.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

// ─── FlipTransitionText ─────────────────────────────────────────────────────
//
// Замена простому fade/slide для заголовков вроде "Выберите группу" ↔
// "ИВТ-21" (см. ScheduleHostScreen) — каждая буква переворачивается вокруг
// горизонтальной оси ПО ОТДЕЛЬНОСТИ, со сдвигом по времени от позиции символа:
// эффект как на сплит-флап табло (вокзальное расписание), а не банальный
// crossfade.
//
// Строки разной длины: недостающие "слоты" справа заполняются пробелом.
// Количество слотов растёт СИНХРОННО, прямо во время композиции (а не в
// LaunchedEffect) — иначе рост слотов под новые буквы отставал бы на кадр от
// момента нажатия тумблера, и было заметно, что анимация стартует с
// небольшой задержкой. Сжимается количество слотов, наоборот, только ПОСЛЕ
// того, как переворот гарантированно закончился у всех символов — иначе
// "хвост" пропадал бы прямо посреди анимации.

private const val FLIP_STAGGER_MS = 16
private const val FLIP_OUT_MS = 140
private const val FLIP_IN_MS = 180

@Composable
fun FlipTransitionText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
) {
    var slotCount by remember { mutableStateOf(text.length) }

    // Сколько слотов было в САМЫЙ первый раз, когда этот заголовок вообще
    // появился на экране (открыли файл) — символы в их пределах не должны
    // "переворачиваться из пустоты" при самом первом появлении экрана, это
    // выглядело бы как лишняя анимация на ровном месте. А вот всё, что
    // добавится ПОЗЖЕ (текст стал длиннее при переключении тумблера) —
    // должно появляться именно переворотом, а не просто выскакивать.
    val initialSlotCount = remember { text.length }

    // Рост — сразу, без ожидания кадра.
    if (text.length > slotCount) {
        slotCount = text.length
    }

    // Сжатие — с задержкой, чтобы не обрубить переворот последних букв.
    LaunchedEffect(text, slotCount) {
        if (text.length < slotCount) {
            val totalMs = FLIP_OUT_MS + FLIP_IN_MS + slotCount * FLIP_STAGGER_MS
            delay(totalMs.toLong())
            if (text.length < slotCount) slotCount = text.length
        }
    }

    val padded = text.padEnd(slotCount, ' ')

    Row(modifier = modifier) {
        padded.forEachIndexed { index, ch ->
            key(index) {
                FlipChar(
                    targetChar     = ch,
                    delayMs        = index * FLIP_STAGGER_MS,
                    color          = color,
                    fontSize       = fontSize,
                    fontWeight     = fontWeight,
                    animateOnMount = index >= initialSlotCount,
                )
            }
        }
    }
}

@Composable
private fun FlipChar(
    targetChar: Char,
    delayMs: Int,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    animateOnMount: Boolean,
) {
    // Слот, появившийся ПОСЛЕ самого первого рендера заголовка (см.
    // initialSlotCount выше), стартует с пробела — так его самое первое
    // появление тоже переворачивается, а не просто выскакивает целиком.
    // Слоты, бывшие в тексте с самого начала, стартуют сразу с нужной буквы —
    // на первом появлении экрана анимировать нечего, старой буквы не было.
    var shown by remember { mutableStateOf(if (animateOnMount) ' ' else targetChar) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(targetChar) {
        if (targetChar != shown) {
            delay(delayMs.toLong())
            // Первая половина: буква "складывается" по горизонтали до ребра (90°).
            rotation.animateTo(90f, tween(FLIP_OUT_MS, easing = FastOutLinearInEasing))
            shown = targetChar
            // Мгновенно ставим зеркальный старт (-90°) и докручиваем до 0° —
            // так переворот выглядит одним непрерывным движением, а не
            // "перескоком" на новую букву на середине.
            rotation.snapTo(-90f)
            rotation.animateTo(0f, tween(FLIP_IN_MS, easing = LinearOutSlowInEasing))
        }
    }

    Text(
        text = shown.toString(),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = Modifier.graphicsLayer {
            rotationX = rotation.value
            cameraDistance = 10f * density
        },
    )
}
