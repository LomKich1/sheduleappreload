package com.schedule.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import dev.chrisbanes.haze.rememberHazeState
import com.schedule.app.data.model.ScheduleFile
import com.schedule.app.data.prefs.AnimPrefs
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.data.prefs.TabAnimMode
import com.schedule.app.ui.components.CascadeEdge
import com.schedule.app.ui.components.FlipTransitionText
import com.schedule.app.ui.components.ScheduleMode
import com.schedule.app.ui.components.rememberSwipableProgress
import com.schedule.app.ui.theme.LocalAppColors
import kotlin.math.pow

// ─── ScheduleHeaderInfo ─────────────────────────────────────────────────────
//
// Раньше каждый под-экран сам рисовал у себя в шапке заголовок/дату/кнопку
// назад, потом это унесли в хост (единый общий экземпляр на оба режима), а
// теперь, по итогам обсуждения в чате, вернули обратно в под-экраны — но не
// как раньше: каждый под-экран по-прежнему только ВЫЧИСЛЯЕТ эти данные в этот
// data class, а рисует их сам через ScheduleHeaderRow (см. ScheduleScreen.kt/
// TeacherScheduleScreen.kt) — по одному разу в слое пикера и по одному разу в
// слое PairsOverlay, вместо одного общего экземпляра в хосте. Так шапка и
// тумблер естественно закрываются/проступают вместе со своим слоем при
// свайпе Picker↔Pairs (см. комментарий у ScheduleHostScreen), без ручной
// синхронизации translateX между копиями, как было раньше.

data class ScheduleHeaderInfo(
    val title: String = "",
    val placeholder: String = "",
    val dateText: String = "",
    val isPairsScreen: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    // Раньше тут было 22.sp (студент) / 20.sp (препод, свой override) —
    // разные размеры для двух режимов ПЛЮС ещё 16.sp у самой шапки для
    // плейсхолдера (см. ниже) — итого три разных высоты строки заголовка
    // в одной и той же фиксированной шапке. Из-за этого шапка визуально
    // "прыгала" по высоте между пикером и открытым расписанием, и ещё
    // отличалась сама между Ученики/Преподаватели.
    //
    // Единый размер, взятый от AppHeader (см. AppHeader.kt — 17.sp, шапка
    // Files/Bells) — контур и цвет шапки не трогаем, выравниваем только
    // размер текста, чтобы высота строки была одинаковой всегда и
    // совпадала с остальным приложением.
    val filledFontSize: TextUnit = 17.sp,
    val onBack: () -> Unit = {},
)

// ─── ScheduleHostScreen ─────────────────────────────────────────────────────
//
// Экран, открывающийся по тапу на файл. Оба вида (ScheduleScreen и
// TeacherScheduleScreen) монтируются СРАЗУ и ОБА — у каждого свой ViewModel
// и своё независимое состояние (выбранная группа / преподаватель не
// сбрасываются друг от друга при переключении).
//
// Слайд-переключение здесь одно время было выпилено (голый alpha 0/1 без
// анимации) — не понравилось, что тумблер режима визуально путался с
// переходом на другой экран. На деле это была не ошибка механизма, а просто
// не подкрученные параметры; теперь переключение работает через тот же
// AnimPrefs, что и Files↔Bells в AppScaffold — один источник правды на всё
// приложение (DEFAULT/SPRING/PARALLAX, крутится из debug-панели в Settings).
// STUDENT — фоновый слой (progress 0), TEACHER — передний (progress 1),
// та же схема offset/scale/alpha, что и у вкладок. Карточки пикера
// группы/преподавателя внутри активного вида по-прежнему анимированно
// "влетают" отдельно (см. revealTrigger/revealEdge, не менялось).
//
// Шапка, тумблер и полоса загрузки живут внутри каждого под-экрана (см.
// ScheduleHeaderInfo/ScheduleHeaderRow) — хост их не рисует и не дублирует.
@Composable
fun ScheduleHostScreen(file: ScheduleFile, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val defaultMode by AppPrefs.defaultScheduleMode.collectAsState()
    // ОДИН HazeState на оба экрана — иначе при переключении/свайпе блюр шапки
    // берёт контент только из «своего» списка, который уехал вместе со слоем,
    // и блюр покрывает шапку не на всю ширину (см. hazeState в ScheduleScreen).
    val hazeState = rememberHazeState()

    // Стартовый режим берём из настроек ровно один раз при открытии ЭТОГО
    // файла (rememberSaveable(file.name) пересоздаст состояние для другого
    // файла) — дальнейшие переключения тумблером внутри одного открытия не
    // должны "прыгать" обратно на дефолт при рекомпозициях.
    var mode by rememberSaveable(file.name) { mutableStateOf(defaultMode) }

    // Каскадная анимация карточек пикера при переключении тумблером — тот же
    // приём, что и раньше, но теперь триггер РАЗДЕЛЁН на два независимых —
    // отдельно для student-пикера, отдельно для teacher-пикера.
    //
    // Было — один общий toggleTrigger/toggleEdge на оба экрана, а реплей
    // внутри ScheduleScreen/TeacherScheduleScreen гейтился через "if (active)"
    // (см. историю). Баг: onDragTowardPage стреляет в САМОМ НАЧАЛЕ жеста
    // (направление определилось), а mode (и, соответственно, active у обоих
    // под-экранов) переключается только на ЗАВЕРШЕНИИ свайпа — через onSwitch
    // ниже. То есть в момент срабатывания триггера active ещё указывает на
    // СТАРЫЙ (source) экран. С общим триггером "if (active)" ловил именно
    // source — он и переигрывал каскад, а не destination, куда пользователь
    // реально свайпает (ровно наоборот тому, что нужно). Плюс у destination
    // анимация вообще терялась: его LaunchedEffect(revealTrigger) отрабатывал
    // с active=false, lastRevealApplied обновлялся, и когда active чуть позже
    // всё-таки становился true — переигрывать уже было нечему, revealTrigger
    // с тех пор не менялся.
    //
    // Раздельные триггеры убирают саму эту гонку: сюда прилетает НАПРЯМУЮ,
    // какой экран является destination (idx/newMode уже это несут), без
    // необходимости сверяться с ещё не обновившимся active.
    var studentRevealTrigger by remember { mutableStateOf(0) }
    var teacherRevealTrigger by remember { mutableStateOf(0) }
    var studentRevealEdge by remember { mutableStateOf(CascadeEdge.LEFT) }
    var teacherRevealEdge by remember { mutableStateOf(CascadeEdge.RIGHT) }

    // animateReveal=false — для свайпа: пикер уже был виден на экране всё
    // время перетаскивания, повторный каскадный "влёт" карточек в конце
    // будет лишним (тот же принцип, что и filesEntranceTrigger в AppScaffold
    // при свайпе Files↔Bells — см. комментарий там).
    fun switchMode(newMode: ScheduleMode, animateReveal: Boolean) {
        if (newMode == mode) return
        if (animateReveal) {
            if (newMode == ScheduleMode.TEACHER) {
                teacherRevealEdge = CascadeEdge.RIGHT
                teacherRevealTrigger++
            } else {
                studentRevealEdge = CascadeEdge.LEFT
                studentRevealTrigger++
            }
        }
        mode = newMode
    }

    val onModeSelect: (ScheduleMode) -> Unit = { newMode -> switchMode(newMode, animateReveal = true) }

    // Прогресс Ученики↔Преподаватели поднят сюда (а не внутрь Box с
    // контентом ниже) — он нужен ещё и ScheduleModeToggle, чтобы индикатор
    // тумблера синхронно ехал за пальцем во время свайпа (та же причина, что
    // и с swipable/FloatingPillNav в AppScaffold — см. комментарий там).
    var widthPx by remember { mutableStateOf(0f) }

    val rawAnimMode by AnimPrefs.mode.collectAsState()
    val disableParallaxHere by AnimPrefs.disableScheduleModeParallax.collectAsState()
    // Отдельный debug-тумблер (см. AnimPrefs.disableScheduleModeParallax) —
    // позволяет отключить PARALLAX конкретно для свайпа/тумблера Ученики↔
    // Преподаватели, не трогая общий AnimPrefs.mode, которым по-прежнему
    // управляются Files/Bells в AppScaffold. При включённом флаге здесь
    // всегда используется DEFAULT, даже если глобально выбран PARALLAX.
    val animMode = if (disableParallaxHere) TabAnimMode.DEFAULT else rawAnimMode
    val tweenDurationMs by AnimPrefs.durationMs.collectAsState()
    val springDamping by AnimPrefs.springDamping.collectAsState()
    val springStiffness by AnimPrefs.springStiffness.collectAsState()
    val parallaxPower by AnimPrefs.parallaxPower.collectAsState()

    // STUDENT — фоновый слой (индекс 0), TEACHER — передний (индекс 1),
    // 1:1 та же схема, что у Files (0) / Bells (1) в AppScaffold — включая
    // свайп в обе стороны через тот же rememberSwipableProgress.
    val activeIndex = if (mode == ScheduleMode.STUDENT) 0 else 1

    // Флаг "открыт ли сейчас экран пар" для каждого из двух видов — нужен
    // ТОЛЬКО чтобы блокировать сам жест свайпа Ученики↔Преподаватели, пока
    // пользователь смотрит расписание конкретной группы/препода (см.
    // dragEnabled ниже). Раньше сюда же поднималась ПОЛНАЯ информация о
    // шапке/тумблере, чтобы рисовать их здесь, в хосте, одним общим
    // экземпляром на оба режима — по итогам обсуждения в чате от этого
    // отказались: шапка и тумблер теперь физически живут внутри
    // ScheduleScreen/TeacherScheduleScreen (в слое пикера, zIndex 0), и
    // просто естественно закрываются/проступают вместе с этим слоем при
    // свайпе Picker↔Pairs — без отдельной синхронизации на хосте.
    var studentPairsOpen by remember { mutableStateOf(false) }
    var teacherPairsOpen by remember { mutableStateOf(false) }
    val activePairsOpen = if (mode == ScheduleMode.STUDENT) studentPairsOpen else teacherPairsOpen

    val swipable = rememberSwipableProgress(
        activeIndex = activeIndex,
        onSwitch = { idx -> switchMode(if (idx == 0) ScheduleMode.STUDENT else ScheduleMode.TEACHER, animateReveal = false) },
        widthPx = widthPx,
        animMode = animMode,
        tweenDurationMs = tweenDurationMs,
        springDamping = springDamping,
        springStiffness = springStiffness,
        dragEnabled = !activePairsOpen,
        // Оверскролл-дисмисс — только на экране групп (index 0, "слева"),
        // не на преподах: см. подробный комментарий у dismissEnabled в
        // rememberSwipableProgress. onBack — тот же коллбэк, что и у кнопки
        // "назад"/системного back в шапке пикера (см. ScheduleScreen.kt).
        dismissEnabled = true,
        onDismiss = onBack,
        // Сброс каскада карточек пикера — на старте направления жеста, не на
        // завершении свайпа (см. подробный комментарий в rememberSwipableProgress
        // и аналогичное подключение в AppScaffold для Files/Bells). idx здесь —
        // именно destination (страница, КУДА ведёт жест), поэтому просто бьём
        // в триггер нужного экрана напрямую, без гадания через active.
        onDragTowardPage = { idx ->
            if (idx == 1) {
                teacherRevealEdge = CascadeEdge.RIGHT
                teacherRevealTrigger++
            } else {
                studentRevealEdge = CascadeEdge.LEFT
                studentRevealTrigger++
            }
        },
    )

    // Раньше .background(c.bg) стоял прямо на этом Column — теперь он должен
    // ехать ВМЕСТЕ с оверскролл-дисмиссом (см. dismissEnabled/graphicsLayer
    // ниже на содержимом), иначе ровно тот же баг, что чинили в самом начале
    // (см. историю правок PairsOverlay в ScheduleScreen.kt): фон остаётся на
    // месте, контент едет — и Files/Bells под ним не проступают, потому что
    // непрозрачный фон-то как раз никуда не делся. Поэтому background теперь
    // внутри Box ниже, ПОСЛЕ graphicsLayer, а не тут.
    Column(modifier = Modifier.fillMaxSize()) {

        // ── Шапка и тумблер "Ученики/Преподаватели" ─────────────────────────
        // Раньше рисовались здесь, в хосте, единым общим экземпляром на оба
        // режима (см. историю выше и в комментариях ScheduleScreen.kt) — по
        // итогам обсуждения в чате перенесены физически внутрь
        // ScheduleScreen/TeacherScheduleScreen: шапка пикера + тумблер живут
        // в слое пикера (zIndex 0), своя шапка пар — в слое PairsOverlay
        // (zIndex 1). Хосту для этого ничего специально рисовать не нужно —
        // естественный z-order уже даёт нужное поведение: пока пары открыты,
        // их непрозрачный слой просто физически лежит поверх пикера (вместе
        // с его шапкой и тумблером), а во время свайпа-закрытия PairsOverlay
        // уезжает и всё это естественно проступает, без отдельной ручной
        // синхронизации translateX/alpha, как было раньше.
        //
        // Единственное, что хосту всё ещё нужно от каждого под-экрана — это
        // simple boolean "открыт ли сейчас экран пар" (studentPairsOpen /
        // teacherPairsOpen выше), чтобы правильно выставлять dragEnabled у
        // свайпа Ученики↔Преподаватели.


        // ── Содержимое: оба вида смонтированы всегда, слайд между ними
        // управляется единым AnimPrefs (см. комментарий выше).
        //
        // zIndex ОБЯЗАТЕЛЕН: без него порядок хит-тестинга тапов определяется
        // порядком в коде (кто добавлен позже — тот и "сверху"). Из-за этого
        // раньше преподавательский экран, всегда идущий вторым, перехватывал
        // все тапы и скроллы, даже когда был неактивен — из-за чего казалось,
        // что "группы сломались". Блокировка тапов у неактивного вида — по
        // целевому режиму сразу в момент тапа по тумблеру, не дожидаясь
        // конца анимации (так же ведёт себя zIndex у Files/Bells).
        // Раньше здесь стоял BoxWithConstraints ради maxWidth — теперь ширина
        // нужна ДО входа в scope (для rememberSwipableProgress и dragModifier
        // на самом контейнере), поэтому обычный Box + onSizeChanged, как и в
        // AppScaffold (см. комментарий там). Сам swipable объявлен выше —
        // нужен ещё и тумблеру, см. комментарий там же.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .clipToBounds()
                .onSizeChanged { widthPx = it.width.toFloat() }
                .then(swipable.dragModifier)
                // Живой оверскролл-дисмисс с экрана групп (см. dismissEnabled
                // выше) — тащит ВЕСЬ этот Box (обе половины Ученики/Преподы
                // разом, они внутри) вслед за пальцем, поверх уже
                // смонтированных Files/Bells под этим NavHost-экраном (тот же
                // принцип, что у swipeToDismiss в SettingsScreen.kt).
                .graphicsLayer { translationX = widthPx * swipable.dismissProgress }
                // background — ПОСЛЕ graphicsLayer (внутри translate), иначе
                // фон остаётся на месте, а едет только контент — см.
                // комментарий у Column выше.
                .background(c.bg),
        ) {
            val studentActive = mode == ScheduleMode.STUDENT
            val progress = swipable.progress

            // Student — фоновый слой.
            val studentOffset = -widthPx * progress
            val studentScale = if (animMode == TabAnimMode.PARALLAX) lerp(1f, 0.94f, progress) else 1f
            val studentAlpha = if (animMode == TabAnimMode.PARALLAX) lerp(1f, 0.55f, progress) else 1f

            // Teacher — передний слой.
            val teacherProgress =
                if (animMode == TabAnimMode.PARALLAX) 1f - (1f - progress).pow(parallaxPower) else progress
            val teacherOffset = widthPx * (1f - teacherProgress)
            val teacherScale = if (animMode == TabAnimMode.PARALLAX) lerp(0.96f, 1f, teacherProgress) else 1f
            val teacherAlpha = if (animMode == TabAnimMode.PARALLAX) lerp(0.6f, 1f, teacherProgress) else 1f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Горизонтальный сдвиг слоя — через layout-фазовый offset{}, а НЕ
                    // через graphicsLayer.translationX. Haze узнаёт позицию источника
                    // (списка) через onGloballyPositioned, а тот НЕ вызывается, когда
                    // меняется только transform слоя: позиция списка соседнего экрана
                    // «застревала» на значении с середины анимации/недосвайпа, и его
                    // контент (например, имя преподавателя) просвечивал сквозь блюр
                    // шапки не там, где должен (в правом углу вместо левого). Тот же
                    // приём, что и у counterTranslationX в шапке (см. ScheduleScreen).
                    // Scale/alpha (PARALLAX) остаются в graphicsLayer — на позицию
                    // левого верхнего угла они не влияют.
                    .zIndex(if (studentActive) 1f else 0f)
                    .offset { IntOffset(x = studentOffset.roundToInt(), y = 0) }
                    .graphicsLayer {
                        scaleX = studentScale
                        scaleY = studentScale
                        alpha = studentAlpha
                    }
                    .blockTouchesIfInactive(!studentActive),
            ) {
                ScheduleScreen(
                    file               = file,
                    onBack             = onBack,
                    active             = studentActive,
                    revealTrigger      = studentRevealTrigger,
                    revealEdge         = studentRevealEdge,
                    mode               = mode,
                    onModeSelect       = onModeSelect,
                    modeSwipeProgress  = swipable.progress,
                    onPairsOpenChanged = { studentPairsOpen = it },
                    counterTranslationX = studentOffset,
                    hazeState          = hazeState,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (!studentActive) 1f else 0f)
                    // Сдвиг через offset{} — см. комментарий у слоя Student выше.
                    .offset { IntOffset(x = teacherOffset.roundToInt(), y = 0) }
                    .graphicsLayer {
                        scaleX = teacherScale
                        scaleY = teacherScale
                        alpha = teacherAlpha
                    }
                    .blockTouchesIfInactive(studentActive),
            ) {
                TeacherScheduleScreen(
                    file               = file,
                    onBack             = onBack,
                    active             = !studentActive,
                    revealTrigger      = teacherRevealTrigger,
                    revealEdge         = teacherRevealEdge,
                    mode               = mode,
                    onModeSelect       = onModeSelect,
                    modeSwipeProgress  = swipable.progress,
                    onPairsOpenChanged = { teacherPairsOpen = it },
                    counterTranslationX = teacherOffset,
                    hazeState          = hazeState,
                )
            }
        }
    }
}

// Один слой шапки — теперь используется В ТРЁХ местах: в слое пикера и в
// слое PairsOverlay КАЖДОГО из ScheduleScreen.kt/TeacherScheduleScreen.kt (не
// private — тот же package, импорт не нужен). Раньше рисовался здесь же, в
// хосте, ОДНИМ общим экземпляром на оба состояния сразу с ручной
// синхронизацией translateX между ними во время свайпа Picker↔Pairs — по
// итогам обсуждения в чате от этого отказались в пользу того, чтобы шапка
// была просто частью соответствующего слоя (пикер/пары) и естественно
// закрывалась/проступала вместе с ним по z-order, без всякой лишней
// синхронизации. Каждый вызов обязан быть непрозрачным (свой
// .background(c.surface)) — иначе при наложении был бы виден слой снизу
// сквозь едущий верхний.
//
// hazeModifier/opaqueBackground — добавлено для frosted-glass шапки ПИКЕРА
// (см. чат про блюр шапки Telegram). Дефолты (opaqueBackground = true,
// hazeModifier = Modifier) сохраняют СТАРОЕ поведение один-в-один — оба
// вызова из PairsOverlay (ScheduleScreen.kt/TeacherScheduleScreen.kt) НЕ
// трогаются и остаются полностью непрозрачными, как и требует комментарий
// выше про наложение слоёв при свайпе. Блюр включают ТОЛЬКО вызовы из
// пикера, передавая opaqueBackground = false + свой .hazeChild(...).
@Composable
fun ScheduleHeaderRow(
    header: ScheduleHeaderInfo,
    modifier: Modifier = Modifier,
    hazeModifier: Modifier = Modifier,
    opaqueBackground: Boolean = true,
) {
    val c = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (opaqueBackground) Modifier.background(c.surface) else Modifier)
            .then(hazeModifier)
            // statusBarsPadding — ПОСЛЕ фона и hazeModifier, поэтому и c.surface
            // (opaqueBackground), и hazeChild-блюр занимают ВСЮ высоту шапки
            // вместе с зоной статус-бара/камеры, а сам контент шапки сдвинут
            // ниже неё. Корень AppScaffold верхний инсет больше не применяет
            // (см. комментарий там), экраны расписания рисуются от y=0.
            .statusBarsPadding()
            // vertical = 12.dp — как в AppHeader (было 14.dp): вместе с фикс.
            // размером шрифта ниже это выравнивает высоту "чистой" шапки (без
            // подстрочника даты) с шапкой Files/Bells.
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.surface2)
                .clickable(onClick = header.onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Назад",
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            val displayTitle = header.title.ifBlank { header.placeholder }
            FlipTransitionText(
                text     = displayTitle,
                color    = if (header.title.isBlank()) c.textSub else c.accent,
                fontSize = header.filledFontSize,
            )
            Text(
                text = header.dateText,
                color = c.textSub,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// Блокирует все тач-события для невидимого (неактивного) вида — иначе он,
// будучи смонтированным и занимающим весь экран под активным, мог бы первым
// перехватывать часть тапов, несмотря на alpha = 0.
private fun Modifier.blockTouchesIfInactive(inactive: Boolean): Modifier =
    if (inactive) {
        this.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else this
