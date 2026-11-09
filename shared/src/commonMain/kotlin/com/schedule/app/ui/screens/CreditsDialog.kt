package com.schedule.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

// ─── Пасхалка: «Над приложением трудились» ────────────────────────────────────
//
//  Открывается долгим нажатием (≈2 с) на кнопку «Сохранить» в настройках
//  (см. SettingsScreen). Чисто commonMain: ссылки открываются через
//  LocalUriHandler (Android / Desktop / iOS), платформенный код не нужен.
//  Цвета — только из MaterialTheme.colorScheme, поэтому окно следует теме.

private data class Credit(
    val name: String,
    val role: String,
    val text: String,
    val github: String? = null,
)

private val credits = listOf(
    Credit(
        name = "LomKich",
        role = "CEO и идейный вдохновитель",
        text = "Ему не понравился Я.Диск, и он решил сделать своё приложение.",
        github = "https://github.com/LomKich1",
    ),
    Credit(
        name = "Claude",
        role = "Основной автор кода",
        text = "Главный наставник и единственный разработчик в проекте. " +
            "Сделал приложение по просьбе LomKich. Огромное ему спасибо за весь проект. " +
            "Пишет код вслепую: собрать и запустить приложение не может, поэтому о красных " +
            "ошибках узнаёт только из логов, которые присылает LomKich.",
        github = "https://github.com/claude",
    ),
    Credit(
        name = "FillKitel",
        role = "Вайб-кодер и тестировщик iOS",
        text = "Второй живой человек, участвовавший в разработке приложения, " +
            "а также лучший друг создателя приложения. " +
            "Сделал порт для Mac и iOS, за что отдельное спасибо.",
        github = "https://github.com/FillKitel",
    ),
    Credit(
        name = "ps1blvdes",
        role = "Senior QA-инженер",
        text = "Главный дизайнер, которого слушали пару раз. " +
            "Написал баг-репорт на 13 пунктов, что помогло развитию приложения.",
    ),
    Credit(
        name = "ArtikHakuya",
        role = "Главный охотник за багами и пасхалками",
        text = "Каждый час тестировал приложение на наличие багов и пасхалок. " +
            "Отличный маркетолог. Спасибо за наибольшую заинтересованность в проекте.",
    ),
    Credit(
        name = "OniSun",
        role = "Архитектурный критик и поставщик кринжа",
        text = "Главный критик приложения. Был уверен, что позиционирование элементов " +
            "интерфейса не совпадает с ГОСТом. Одним из первых узнал о приложении " +
            "и предлагал ужасные идеи, которые не соответствовали ГОСТу.",
    ),
    Credit(
        name = "Hikikomori_eyes",
        role = "Полевой тестировщик и голос разума",
        text = "Один из первых, кто понял, что прошлая версия приложения была солянкой. " +
            "Кидал баг-репорты не реже остальных. Весёлый пацан.",
    ),
    Credit(
        name = "Одногруппники",
        role = "Отдел полевого тестирования",
        text = "Герои, которые терпели обновления каждый день. Смогли дождаться стабильных " +
            "версий. Прошли через весь ад, связанный с багами интерфейса и лагами " +
            "приложения. Спасибо вам огромное.",
    ),
    Credit(
        name = "Gemini",
        role = "Дизайнер интерфейсов",
        text = "Сделал то, чего никто не смог. Помог с настройкой той самой " +
            "навигационной пилюли, которую вы видите на главном экране.",
    ),
    Credit(
        name = "DeepSeek",
        role = "Стажёр-переводчик",
        text = "Часто участвовал в разработке, когда заканчивались лимиты в Claude. " +
            "Кроме чтения логов особо ничем не занимался. " +
            "Спасибо за помощь в решении ошибок компиляции.",
    ),
    Credit(
        name = "ChatGPT",
        role = "Штатный консультант",
        text = "Выдавал код, в котором был уверен на все 100%, даже если после этого " +
            "код мог сломать половину проекта.",
    ),
)

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = scheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = 640.dp),
        ) {
            Column {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                    Text(
                        text = "Над приложением трудились",
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "в порядке появления в чате, а не по полезности",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Сноска: большая часть багов и мемов ниже относится к легендарной " +
                            "старой версии приложения, тому самому «Франкенштейну».",
                        style = MaterialTheme.typography.labelMedium,
                        fontStyle = FontStyle.Italic,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    credits.forEachIndexed { index, credit ->
                        Reveal(index) { CreditCard(credit, index) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Все баги, кроме найденных вами, — задуманные фичи.",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
}

/** Карточки появляются по очереди: лёгкий сдвиг снизу + fade. */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80L + index * 70L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(320)) +
            slideInVertically(animationSpec = tween(320), initialOffsetY = { it / 5 }),
    ) {
        content()
    }
}

@Composable
private fun CreditCard(credit: Credit, index: Int) {
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    // Аватары чередуют три M3-контейнера: primary → secondary → tertiary.
    val (avatarBg, avatarFg) = when (index % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(avatarBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = credit.name.first().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = avatarFg,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = credit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text = credit.role,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = credit.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (credit.github != null) {
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { runCatching { uriHandler.openUri(credit.github) } },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("GitHub")
                    }
                }
            }
        }
    }
}
