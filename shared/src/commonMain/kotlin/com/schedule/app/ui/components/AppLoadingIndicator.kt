package com.schedule.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// ─── Индикатор загрузки в стиле Material 3 Expressive ─────────────────────────
//
//  Это самописный аналог androidx.compose.material3.LoadingIndicator: фигура
//  непрерывно "перетекает" из одной формы в другую (пятиугольник → звезда →
//  клевер → овал → ...) и медленно вращается. Цвет — primary темы.
//
//  Почему не готовый LoadingIndicator: он появился в material3 1.4 (Compose
//  Multiplatform 1.9+), а проект на CMP 1.8.2 — там его нет. После апгрейда
//  CMP достаточно заменить тело AppLoadingIndicator на вызов
//  LoadingIndicator(modifier.size(size), color = color).
//
//  Реализация без внешних библиотек: каждая форма — функция радиуса от угла
//  r(θ). Морфинг — поточечная интерполяция двух таких функций, контур
//  рисуется Path по 96 точкам. Анимация читается ТОЛЬКО в фазе рисования
//  (value внутри Canvas) — перекомпозиций во время анимации нет.

private const val POINTS = 96
private val TWO_PI = (2.0 * PI).toFloat()

// Скруглённый правильный n-угольник: смесь многоугольника и окружности.
private fun roundedPolygon(n: Int, rounding: Float): (Float) -> Float = { theta ->
    val sector = TWO_PI / n
    val phi = (theta % sector) - sector / 2f
    val poly = cos(PI.toFloat() / n) / cos(phi)
    rounding * 0.96f + (1f - rounding) * poly
}

// "Печенье"/звезда со скруглёнными лепестками: r = base + amp·cos(nθ).
private fun cookie(n: Int, amp: Float): (Float) -> Float = { theta ->
    (1f - amp) + amp * cos(n * theta)
}

private fun ellipse(b: Float): (Float) -> Float = { theta ->
    val c = cos(theta)
    val s = sin(theta)
    b / sqrt((b * c) * (b * c) + s * s)
}

// Набор форм, по которым гоняем морфинг (замкнутый цикл).
private val SHAPES: List<(Float) -> Float> = listOf(
    roundedPolygon(5, 0.55f),   // пятиугольник
    cookie(8, 0.14f),           // "солнышко"
    cookie(4, 0.24f),           // клевер
    ellipse(0.64f),             // овал
    cookie(12, 0.10f),          // звезда-зубчатка
    roundedPolygon(3, 0.60f),   // мягкий треугольник
    cookie(9, 0.08f),           // волнистый круг
)

private fun smooth(t: Float): Float = FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))

@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "loadingIndicator")

    // Фаза морфинга: 0 → SHAPES.size, затем по кругу (форма i → i+1).
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = SHAPES.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHAPES.size * 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    // Поворот — отдельной анимацией, чтобы на стыке цикла не было скачка.
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Canvas(modifier = modifier.size(size)) {
        val v = phase.value
        val i = floor(v).toInt() % SHAPES.size
        val j = (i + 1) % SHAPES.size
        // Первые ~30% времени фигура "держит" форму, остальное — перетекает.
        val t = smooth(((v - floor(v)) - 0.3f) / 0.7f)
        val from = SHAPES[i]
        val to = SHAPES[j]

        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f * 0.92f

        val path = Path()
        for (k in 0..POINTS) {
            val theta = TWO_PI * k / POINTS
            val r = from(theta) * (1f - t) + to(theta) * t
            val x = center.x + radius * r * cos(theta)
            val y = center.y + radius * r * sin(theta)
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        rotate(degrees = rotation.value, pivot = center) {
            drawPath(path = path, color = color)
        }
    }
}

/** Индикатор по центру выделенной области — для экранов в состоянии Loading. */
@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AppLoadingIndicator()
    }
}
