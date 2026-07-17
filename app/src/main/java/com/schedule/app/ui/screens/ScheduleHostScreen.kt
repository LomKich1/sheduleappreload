package com.schedule.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.components.ScheduleModeToggle

// Та же длительность, что и TAB_ANIM_MS в AppScaffold для Files ↔ Bells —
// переключение Ученики/Преподаватели визуально относится к тому же семейству
// анимаций ("переключение вкладки", а не "переход на глубокий экран").
private const val HOST_ANIM_MS = 250

// ─── ScheduleHostScreen ─────────────────────────────────────────────────────
//
// Экран, открывающийся по тапу на файл. Раньше тумблер "Ученики/Преподаватели"
// жил на главном экране и решал, КАКОЙ из двух экранов открыть дальше —
// теперь он переехал сюда: оба экрана (ScheduleScreen и TeacherScheduleScreen)
// монтируются СРАЗУ и ОБА, ровно как Files/Bells в AppScaffold — переключение
// это просто сдвиг по X без пересоздания композиции, у каждого свой
// ViewModel и своё независимое состояние (выбранная группа / преподаватель
// не сбрасываются друг от друга). Тумблер один на двоих (тот же composable
// передаётся в оба слота), но реально виден только у активного — см.
// modeToggle-слот в ScheduleScreen/TeacherScheduleScreen, который прячет его
// на экране пар.
//
// Да, это значит, что расписание группы И список преподавателей парсятся и
// рендерятся одновременно при открытии файла, а не по требованию — сознательный
// компромисс: элементы интерфейса лёгкие, поэтому на производительность это
// почти не влияет, а взамен переключение между видами внутри уже открытого
// файла становится мгновенным (ничего не перезапрашивается и не пересоздаётся).

@Composable
fun ScheduleHostScreen(file: ScheduleFile, onBack: () -> Unit) {
    val defaultMode by AppPrefs.defaultScheduleMode.collectAsState()

    // Стартовый режим берём из настроек ровно один раз при открытии ЭТОГО
    // файла (rememberSaveable(file.name) пересоздаст состояние для другого
    // файла) — дальнейшие переключения тумблером внутри одного открытия не
    // должны "прыгать" обратно на дефолт при рекомпозициях.
    var mode by rememberSaveable(file.name) { mutableStateOf(defaultMode) }

    // Каскадная анимация элементов пикера при переключении тумблером — тот же
    // приём, что раньше жил в FilesScreen (modeToggleTrigger/modeToggleEdge),
    // просто теперь управляет карточками пикера группы/преподавателя, а не
    // карточками файлов.
    var toggleTrigger by remember { mutableStateOf(0) }
    var toggleEdge by remember { mutableStateOf(CascadeEdge.LEFT) }

    val onModeSelect: (ScheduleMode) -> Unit = { newMode ->
        if (newMode != mode) {
            toggleEdge = if (newMode == ScheduleMode.TEACHER) CascadeEdge.RIGHT else CascadeEdge.LEFT
            toggleTrigger++
        }
        mode = newMode
    }

    // Один и тот же тумблер передаётся в оба экрана — контролируемый компонент,
    // конфликта состояния нет, реально отрисовывается заметно только тот
    // экземпляр, что сейчас на виду (см. modeToggle-слот внутри каждого экрана).
    val toggle: @Composable () -> Unit = {
        ScheduleModeToggle(
            selected = mode,
            onSelect = onModeSelect,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(), // чтобы неактивный вид не вылезал за край
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }

        // Студенческий вид стоит в 0, преподавательский сдвинут на +width
        // (ждёт справа) — offset двигает оба разом.
        val targetOffset = if (mode == ScheduleMode.STUDENT) 0f else -widthPx
        val offset by animateFloatAsState(
            targetValue   = targetOffset,
            animationSpec = tween(HOST_ANIM_MS, easing = FastOutSlowInEasing),
            label         = "scheduleModeSlide",
        )

        Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = offset }) {
            ScheduleScreen(
                file          = file,
                onBack        = onBack,
                active        = mode == ScheduleMode.STUDENT,
                modeToggle    = toggle,
                revealTrigger = toggleTrigger,
                revealEdge    = toggleEdge,
            )
        }

        Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = offset + widthPx }) {
            TeacherScheduleScreen(
                file          = file,
                onBack        = onBack,
                active        = mode == ScheduleMode.TEACHER,
                modeToggle    = toggle,
                revealTrigger = toggleTrigger,
                revealEdge    = toggleEdge,
            )
        }
    }
}
