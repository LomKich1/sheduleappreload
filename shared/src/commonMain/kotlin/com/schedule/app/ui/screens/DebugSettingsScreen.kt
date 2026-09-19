package com.schedule.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.data.parser.JsonScheduleParser
import com.schedule.app.data.prefs.AnimPrefs
import com.schedule.app.data.prefs.AppPrefs
import com.schedule.app.data.prefs.TabAnimMode
import com.schedule.app.data.repository.DebugFileStore
import com.schedule.app.ui.components.rememberSwipeDismissState
import com.schedule.app.ui.components.swipeToDismiss
import com.schedule.app.ui.theme.AppRadius
import com.schedule.app.ui.theme.AppTheme
import com.schedule.app.ui.theme.LocalAppColors
import com.schedule.app.ui.theme.ThemePreset
import com.schedule.app.util.BackHandler
import com.schedule.app.util.PickedTextFile
import com.schedule.app.util.rememberJsonFilePicker
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

// ─── DebugSettingsScreen ──────────────────────────────────────────────────────
// Отдельный экран для всех debug-only настроек (раньше жили инлайном прямо в
// SettingsScreen — по мере роста числа debug-инструментов это начало раздувать
// основной экран настроек, который обычный пользователь релиза даже не видит
// целиком). Доступен только из SettingsScreen, только когда IsDebugBuild
// (сама кнопка-переход туда тоже скрыта в релизе).
//
// Внутри — тонкая настройка анимации переключения вкладок, и тестовый
// прогон JSON-файлов расписания через JsonScheduleParser (см. JsonTestSection
// ниже) — без сети, без Я.Диска/GitHub, просто локальный файл с телефона.
//
// Живой свайп-закрытие — см. аналогичный комментарий в SettingsScreen.kt,
// тот же приём (rememberSwipeDismissState/swipeToDismiss), тот же принцип:
// под этим экраном всегда смонтированы Files/Bells, ничего специально
// готовить для "естественного" раскрытия не нужно.

@Composable
fun DebugSettingsScreen(onBack: () -> Unit) {
    val c = LocalAppColors.current
    val dismissState = rememberSwipeDismissState(onDismissed = onBack)
    BackHandler { dismissState.dismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .swipeToDismiss(dismissState)
            .background(c.bg),
    ) {
        DebugSettingsHeader(onBack = { dismissState.dismiss() })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            SettingsSectionLabel("Анимация вкладок")
            SettingsCard {
                AnimDebugSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Свайп Ученики/Преподаватели")
            SettingsCard {
                ScheduleModeParallaxSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Выход с глубоких экранов")
            SettingsCard {
                TabsEntranceReplaySection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Отскок при отмене свайпа-дисмисса")
            SettingsCard {
                SwipeDismissSpringSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Анимация перехода между экранами")
            SettingsCard {
                NavAnimDebugSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Тест JSON-файла расписания")
            SettingsCard {
                JsonTestSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Отладка рендера Picker/Pairs")
            SettingsCard {
                RenderDebugSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Блюр (Haze)")
            SettingsCard {
                FullscreenBlurDebugSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Диагностика: сырой Modifier.blur() без Haze")
            SettingsCard {
                NativeBlurDiagnosticSection()
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel("Диагностика: изолированный Haze-тест")
            SettingsCard {
                IsolatedHazeDiagnosticSection()
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DebugSettingsHeader(onBack: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(c.surface2)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Назад",
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "🐞 Debug-настройки",
            color = c.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Отладка рендера Picker/Pairs ───────────────────────────────────────────
// Диагностический тумблер под конкретный баг (см. обсуждение в чате):
// свайп-закрытие экрана пар должно "обнажать" список групп/преподов под
// собой, но там видна ровная заливка — непонятно, пикер вообще не рисуется,
// или рисуется, просто фон у него того же чёрного цвета, что и у пар,
// и визуально неотличим. Прозрачный фон снимает эту неоднозначность —
// если карточки появятся, значит дело было в фоне/z-order, а не в самом
// списке. НЕ персистится (см. AppPrefs.debugTransparentOverlayBg) — чисто
// разовая диагностика, не постоянная настройка.

@Composable
private fun RenderDebugSection() {
    val c = LocalAppColors.current
    val transparentBg by AppPrefs.debugTransparentOverlayBg.collectAsState()
    val showHint by AppPrefs.debugShowPickerHint.collectAsState()

    Column {
        Text(
            text = "Делает фон экрана пикера и экрана пар прозрачным вместо чёрного — " +
                "чтобы отличить «список групп/преподов не рисуется» от «рисуется, но " +
                "за пустым чёрным фоном не видно». Применяется сразу — зайди в расписание " +
                "группы/препода и свайпни назад.",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Прозрачный фон Picker/Pairs",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = transparentBg,
                onCheckedChange = { AppPrefs.setDebugTransparentOverlayBg(it) },
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Текст \"Найдено N групп/преподавателей...\" над списком в пикере — " +
                "убран из обычного вида (шапка+капсула теперь блюрные, дублировать " +
                "смысл текстом под ними избыточно). Включи обратно, если нужно сравнить " +
                "или для отладки.",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Показывать подсказку над списком",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = showHint,
                onCheckedChange = { AppPrefs.setDebugShowPickerHint(it) },
            )
        }
    }
}

// ─── Диагностика: изолированный Haze-тест (канонический паттерн) ───────────
// Раз голый Modifier.blur() подтвердил, что RenderEffect на устройстве
// работает (см. чат — текст "ТЕСТ" пропал в блюре на Nord 5), а Haze
// в AppScaffold молчит — нужно понять: Haze вообще не заводится в этом
// проекте, или ломается именно в сложном дереве AppScaffold (шапка + свайп +
// NavHost + пилл-нав). Здесь — минимальный пример 1-в-1 по документации
// Haze: .haze(state) висит прямо на контенте (Column с текстом), а
// .hazeChild(state) — на плашке-СИБЛИНГЕ, перекрывающей НИЖНУЮ половину
// того же контента. Если Haze жива — строки текста в нижней половине должны
// стать нечитаемыми/размытыми, а в верхней остаться чёткими, в ОДНОМ кадре,
// без переключения между экранами — так сразу видно разницу без сомнений
// "мне показалось или нет".
@Composable
private fun IsolatedHazeDiagnosticSection() {
    val c = LocalAppColors.current
    var enabled by remember { mutableStateOf(false) }
    val hazeState = rememberHazeState()

    Column {
        Text(
            text = "Канонический паттерн Haze 1-в-1 по докам: .haze() на контенте, " +
                ".hazeChild() на плашке-сиблинге поверх нижней половины ТОГО ЖЕ " +
                "текста. Если верх остаётся чётким, а низ размывается — Haze жива, " +
                "проблема именно в дереве AppScaffold. Если размытия нет нигде — " +
                "Haze не заводится в проекте вообще, тогда придётся писать backdrop " +
                "вручную.",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Плашка поверх низа",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(AppRadius.card),
        ) {
            // Источник — контент, который будет виден ЧАСТИЧНО размытым.
            // .haze() висит прямо здесь, как и требует канон Haze.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF3D7F), Color(0xFF3D7FFF), Color(0xFFFFD23D)),
                        ),
                    )
                    .haze(hazeState)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(4) { i ->
                    Text(
                        text = "СТРОКА ТЕКСТА №${i + 1} ДЛЯ ПРОВЕРКИ БЛЮРА",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            // Плашка-сиблинг поверх НИЖНОЙ половины — читает ТОТ ЖЕ hazeState.
            if (enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .hazeChild(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = c.bg,
                                blurRadius = 20.dp,
                                tint = HazeTint(Color.Black.copy(alpha = 0.15f)),
                            ),
                        ),
                )
            }
        }
    }
}

// ─── Диагностика: голый Modifier.blur() (без Haze) ──────────────────────────
// Если на Nord 5 не виден даже блюр Haze — надо понять, работает ли вообще
// RenderEffect (API 31+) на этом железе/прошивке, ДО того как копаться
// дальше в потрохах Haze. Modifier.blur() — родной для Compose, тонкая
// обёртка прямо над android.graphics.RenderEffect, без сторонней либы.
// Он блюрит СВОЁ СОБСТВЕННОЕ содержимое (не backdrop, как нужно для шапки),
// но для диагностики этого достаточно: цветной паттерн внутри одного и того
// же Box — если размывается, значит RenderEffect на устройстве в принципе
// работает и проблема локализована в Haze. Если нет — проблема глубже.
// Локальный toggle, ничего в AppPrefs не пишет — чисто одноразовая проверка.

@Composable
private fun NativeBlurDiagnosticSection() {
    val c = LocalAppColors.current
    var enabled by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Размывает цветной паттерн НИЖЕ напрямую через Modifier.blur() — " +
                "минуя Haze полностью. Если на устройстве, где Haze выше молчал, тут " +
                "тоже пусто — дело не в Haze, а в том, что RenderEffect не отрабатывает " +
                "на этом железе/прошивке вообще.",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Размыть паттерн",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(AppRadius.card)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF3D7F), Color(0xFF3D7FFF), Color(0xFFFFD23D)),
                    ),
                )
                .then(if (enabled) Modifier.blur(24.dp) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ТЕСТ",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

// ─── Полноэкранный Haze-блюр — перф-тест ────────────────────────────────────
// Грубый тумблер, чтобы прямо на устройстве пощупать, тормозит ли realtime-
// блюр на скролле/свайпе, ДО того как возиться с внедрением его конкретно
// в шапку/бар (см. чат про блюр шапки Telegram — там подтверждено видео с
// реальным устройством, что эффект честный, не завязан на прогресс скролла).
// Блюрит буквально весь Box в AppScaffold разом — см. AppPrefs.
// debugFullscreenBlur. НЕ персистится, как и остальные тумблеры на этом
// экране — сбрасывается при каждом перезапуске.

@Composable
private fun FullscreenBlurDebugSection() {
    val c = LocalAppColors.current
    val blurEnabled by AppPrefs.debugFullscreenBlur.collectAsState()

    Column {
        Text(
            text = "Включает Haze realtime-блюр поверх вкладок Files/Bells (шапка + " +
                "контент) — грубый перф-щуп перед тем, как встраивать блюр именно в " +
                "шапку/бар. ВАЖНО: эффект виден на вкладках Files/Bells, а не на этом " +
                "самом Debug-экране — включи тумблер и вернись назад на список групп " +
                "или звонки.",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Полноэкранный блюр",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = blurEnabled,
                onCheckedChange = { AppPrefs.setDebugFullscreenBlur(it) },
            )
        }
    }
}

// ─── Параллакс Ученики/Преподаватели ────────────────────────────────────────
// Отдельный, независимый от общего "Анимация вкладок" переключатель (см.
// AnimPrefs.disableScheduleModeParallax) — по просьбе из чата: PARALLAX даёт
// небольшой (доли процента ширины экрана) остаточный дрейф у зафиксированной
// шапки/тумблера ScheduleHostScreen (см. counterTranslationX в
// ScheduleScreen.kt/TeacherScheduleScreen.kt), поэтому нужна возможность
// принудительно отключить именно PARALLAX здесь, не трогая общий режим
// Files/Bells выше.

@Composable
private fun ScheduleModeParallaxSection() {
    val c = LocalAppColors.current
    val disabled by AnimPrefs.disableScheduleModeParallax.collectAsState()

    Column {
        Text(
            text = "Пока включено — переключение Ученики↔Преподаватели всегда " +
                "использует Default, даже если выше выбран Parallax. Сам Parallax " +
                "для Files/Bells это не трогает.",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Отключить Parallax здесь",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = disabled,
                onCheckedChange = { AnimPrefs.setDisableScheduleModeParallax(it) },
            )
        }
    }
}

// ─── Выход с глубоких экранов (Schedule/Settings/DebugSettings) ─────────────
// По итогам обсуждения в чате: PillNav и каскад "влёта" элементов Files/Bells
// раньше были завязаны на дискретный момент popBackStack() — из-за этого пилл
// выскакивал резко, уже ПОСЛЕ того как экран сверху фактически уехал (фикс —
// см. AppScaffold.kt, PillNav теперь просто всегда смонтирован ниже NavHost
// по z-order), а каскад элементов Files/Bells переигрывался с нуля, хотя сам
// список всё это время был смонтирован и виден под уезжающим экраном — то
// есть визуально дублировал уже показанное резким скачком. Тумблер ниже
// управляет именно вторым — реплеем каскада (см. AnimPrefs.
// replayTabsEntranceOnDeepScreenExit).

@Composable
private fun TabsEntranceReplaySection() {
    val c = LocalAppColors.current
    val enabled by AnimPrefs.replayTabsEntranceOnDeepScreenExit.collectAsState()

    Column {
        Text(
            text = "По умолчанию выключено: Files/Bells и так остаются " +
                "смонтированными под Schedule/Settings/DebugSettings, повторно " +
                "\"влетать\" им незачем. Включи, если всё же хочется вернуть " +
                "старый эффект.",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Реплей каскада при выходе",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = { AnimPrefs.setReplayTabsEntranceOnDeepScreenExit(it) },
            )
        }
    }
}

// ─── Отскок при отмене свайпа-дисмисса ──────────────────────────────────────
// По просьбе из чата: если свайп-дисмисс (PairsOverlay/SettingsScreen/
// DebugSettingsScreen — все три делят один SwipeDismissState.settleBack, см.
// SwipeDismiss.kt) отпустить, не доведя до конца, экран заметно пружинит
// обратно. По умолчанию выключено — тот же некруглый Default-характер, что и
// у переключения вкладок (переиспользует ту же длительность из "Анимация
// вкладок" выше, отдельного слайдера для этого не заводили).

@Composable
private fun SwipeDismissSpringSection() {
    val c = LocalAppColors.current
    val spring by AnimPrefs.swipeDismissSpring.collectAsState()

    Column {
        Text(
            text = "Действует сразу на расписание группы/препода, настройки и " +
                "этот дебаг-экран — у них общий механизм свайпа-дисмисса.",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Пружинить при отмене (Spring)",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = spring,
                onCheckedChange = { AnimPrefs.setSwipeDismissSpring(it) },
            )
        }
    }
}

// ─── Анимация вкладок ─────────────────────────────────────────────────────────

@Composable
private fun AnimDebugSection() {
    val c = LocalAppColors.current

    val mode        by AnimPrefs.mode.collectAsState()
    val durationMs  by AnimPrefs.durationMs.collectAsState()
    val damping     by AnimPrefs.springDamping.collectAsState()
    val stiffness   by AnimPrefs.springStiffness.collectAsState()
    val parallaxPow by AnimPrefs.parallaxPower.collectAsState()

    Column {
        Text(
            text = "Меняется сразу — переключись на вкладки Расписание/Звонки чтобы проверить",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // ── Выбор режима: три чипа ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimModeChip("Default", mode == TabAnimMode.DEFAULT, Modifier.weight(1f)) { AnimPrefs.setMode(TabAnimMode.DEFAULT) }
            AnimModeChip("Spring", mode == TabAnimMode.SPRING, Modifier.weight(1f)) { AnimPrefs.setMode(TabAnimMode.SPRING) }
            AnimModeChip("Parallax", mode == TabAnimMode.PARALLAX, Modifier.weight(1f)) { AnimPrefs.setMode(TabAnimMode.PARALLAX) }
        }

        Spacer(Modifier.height(16.dp))

        // ── Крутилки: разные для разных режимов ─────────────────────────────
        AnimatedVisibility(visible = mode == TabAnimMode.DEFAULT) {
            LabeledSlider(
                label     = "Длительность (таб: Файлы↔Звонки, Ученики↔Преподы)",
                valueText = "${durationMs}мс",
                value     = durationMs.toFloat(),
                range     = 100f..600f,
                onChange  = { AnimPrefs.setDurationMs(it.toInt()) },
            )
        }

        AnimatedVisibility(visible = mode == TabAnimMode.SPRING || mode == TabAnimMode.PARALLAX) {
            Column {
                LabeledSlider(
                    label     = "Damping (упругость)",
                    valueText = fixed(damping, 2),
                    value     = damping,
                    range     = 0.3f..1.5f,
                    onChange  = { AnimPrefs.setSpringDamping(it) },
                )
                Spacer(Modifier.height(10.dp))
                LabeledSlider(
                    label     = "Stiffness (жёсткость)",
                    valueText = fixed(stiffness, 0),
                    value     = stiffness,
                    range     = 50f..1500f,
                    onChange  = { AnimPrefs.setSpringStiffness(it) },
                )
                if (mode == TabAnimMode.PARALLAX) {
                    Spacer(Modifier.height(10.dp))
                    LabeledSlider(
                        label     = "Parallax power",
                        valueText = fixed(parallaxPow, 2),
                        value     = parallaxPow,
                        range     = 1f..3f,
                        onChange  = { AnimPrefs.setParallaxPower(it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Сбросить к дефолтам",
            color = c.accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { AnimPrefs.resetToDefaults() }
                .padding(vertical = 4.dp),
        )
    }
}

// ─── Переход между экранами (NavHost) ──────────────────────────────────────────
// Отдельный механизм от табов выше: Files/Bells → Schedule/Settings/Debug —
// это push/pop в NavHost, а не offset внутри одного экрана. Раньше был
// захардкожен константой NAV_ANIM_MS в AppScaffold.kt, теперь настраивается
// так же, как таб-анимация.

@Composable
private fun NavAnimDebugSection() {
    val c = LocalAppColors.current
    val navDurationMs by AnimPrefs.navDurationMs.collectAsState()

    Column {
        Text(
            text = "Меняется сразу — открой Настройки или тапни файл, чтобы проверить",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LabeledSlider(
            label     = "Длительность перехода",
            valueText = "${navDurationMs}мс",
            value     = navDurationMs.toFloat(),
            range     = 100f..700f,
            onChange  = { AnimPrefs.setNavDurationMs(it.toInt()) },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Сбросить к дефолту",
            color = c.accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { AnimPrefs.setNavDurationMs(AnimPrefs.DEFAULT_NAV_DURATION_MS) }
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun AnimModeChip(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(
        targetValue   = if (isSelected) c.accent else c.surface2,
        animationSpec = tween(150),
        label         = "animChipBg",
    )
    val fg by animateColorAsState(
        targetValue   = if (isSelected) c.onAccent else c.textSub,
        animationSpec = tween(150),
        label         = "animChipFg",
    )
    Box(
        modifier = modifier
            .clip(AppRadius.capsule)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val c = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(text = valueText, color = c.textSub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor         = c.accent,
                activeTrackColor   = c.accent,
                inactiveTrackColor = c.surface3,
            ),
        )
    }
}

// ─── Тест JSON-файла ──────────────────────────────────────────────────────────
// Кнопка выбора локального .json-файла с телефона/диска + мгновенный
// предпросмотр того, что из него распарсилось (группы/преподы) — не нужно
// заливать тестовый файл на Я.Диск/GitHub каждый раз, пока JSON-конструктора
// ещё не существует и файлы приходится писать руками для проверки схемы.

private sealed class JsonTestState {
    object Idle : JsonTestState()
    data class Error(val message: String) : JsonTestState()
    data class Success(val file: PickedTextFile, val groups: List<String>, val teachers: List<String>) : JsonTestState()
    data class Saved(val fileName: String) : JsonTestState()
}

@Composable
private fun JsonTestSection() {
    val c = LocalAppColors.current
    var state by remember { mutableStateOf<JsonTestState>(JsonTestState.Idle) }

    val pickFile = rememberJsonFilePicker { picked ->
        state = when {
            picked == null -> JsonTestState.Idle // отмена выбора — молча ничего не меняем
            else -> runCatching {
                val groups   = JsonScheduleParser.detectGroups(picked.content)
                val teachers = JsonScheduleParser.detectTeachers(picked.content)
                JsonTestState.Success(picked, groups, teachers)
            }.getOrElse { err ->
                JsonTestState.Error(err.message ?: "Не удалось разобрать файл как JSON")
            }
        }
    }

    Column {
        Text(
            text = "Выбери .json-файл дня — покажем, что из него нашлось, без сети и без ScheduleViewModel",
            color = c.textSub,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppRadius.capsule)
                .background(c.accent)
                .clickable { pickFile() }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("📄 Выбрать JSON-файл", color = c.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        when (val s = state) {
            is JsonTestState.Idle -> Unit

            is JsonTestState.Error -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "❌ ${s.message}",
                    color = c.text,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            is JsonTestState.Saved -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "✅ Сохранено как «${s.fileName}» — открой вкладку Файлы, он там сверху списка",
                    color = c.text,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            is JsonTestState.Success -> {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Найдено групп: ${s.groups.size}",
                    color = c.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (s.groups.isNotEmpty()) {
                    Text(
                        text = s.groups.joinToString(", "),
                        color = c.textSub,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                }
                Text(
                    text = "Найдено преподавателей: ${s.teachers.size}",
                    color = c.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (s.teachers.isNotEmpty()) {
                    Text(
                        text = s.teachers.joinToString(", "),
                        color = c.textSub,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Сохранение — держит файл в оперативной памяти (DebugFileStore),
                // ровно как обычный кеш файлов с Я.Диска. Пропадёт при
                // перезапуске приложения — это ожидаемо, не баг.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppRadius.capsule)
                            .background(c.surface2)
                            .clickable { state = JsonTestState.Idle }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Не сохранять", color = c.textSub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppRadius.capsule)
                            .background(c.accent)
                            .clickable {
                                val saved = DebugFileStore.save(s.file.name, s.file.content)
                                state = if (saved != null) {
                                    AppPrefs.requestFilesRefresh()
                                    JsonTestState.Saved(saved.name)
                                } else {
                                    JsonTestState.Error(
                                        "Имя файла «${s.file.name}» не похоже на день расписания " +
                                            "(нужен формат dd_MM_yyyy_ДЕНЬ.json)"
                                    )
                                }
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Сохранить", color = c.onAccent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun fixed(value: Float, decimals: Int): String {
    val factor = when (decimals) {
        0 -> 1
        1 -> 10
        2 -> 100
        else -> error("Unsupported precision: $decimals")
    }
    val rounded = (value * factor).roundToInt()
    if (decimals == 0) return rounded.toString()
    return "${rounded / factor}.${(rounded % factor).toString().padStart(decimals, '0')}"
}

// ─── Preview ────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
private fun PreviewDebugSettingsDark() = AppTheme(ThemePreset.DARK) { DebugSettingsScreen(onBack = {}) }
