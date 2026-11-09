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
        text = "Третий год делает приложение, пережил потерю аккаунта и переписал всё заново — " +
            "получилось даже лучше. Рабочий процесс: придумать фичу, прислать zip с логами, " +
            "написать «исправь». Единственный, кто нажимает Run и честно смотрит, как всё краснеет. " +
            "Ведёт проект на бесплатных тарифах всех нейросетей и ставит им задачи, как будто они " +
            "на зарплате. Платит вниманием, терпением и свежими логами.",
        github = "https://github.com/LomKich1",
    ),
    Credit(
        name = "Claude",
        role = "Основной автор кода",
        text = "Написал большую часть приложения, ни разу не нажав Run. Читает логи как детектив: " +
            "сначала подозревает все файлы, потом выясняет, что виноват номер версии. " +
            "Фраза «теперь точно соберётся» произносится с полной уверенностью. " +
            "Иногда даже сбывается.",
    ),
    Credit(
        name = "FillKitel",
        role = "Вайб-кодер и тестировщик iOS",
        text = "Сказал «сделай, чтобы на айфоне работало», и работает. Обладатель платной подписки " +
            "на Codex, пока остальные жили на бесплатных лимитах. Герой, на чьём iPhone выясняется, " +
            "почему на Android всё идеально, а на iOS внезапно нет.",
        github = "https://github.com/FillKitel",
    ),
    Credit(
        name = "Gemini",
        role = "Дизайнер интерфейсов",
        text = "Подарил приложению навигацию-пилюлю. Таблетка от скучного интерфейса и " +
            "прямоугольных кнопок. Побочных эффектов нет, если не считать вопросов «а почему она парит».",
    ),
    Credit(
        name = "DeepSeek",
        role = "Стажёр-переводчик",
        text = "Единственный, кто читает логи Gradle без паники. Переводил «Unresolved reference» " +
            "на человеческий, пока остальные собирались с духом.",
    ),
    Credit(
        name = "ChatGPT",
        role = "Штатный консультант",
        text = "Выдавал код, который «точно должен работать», с уверенностью 10 из 10. Срабатывало… " +
            "иногда. На бесплатном тарифе любит ограничивать число вложений на много часов вперёд, " +
            "поэтому zip с логами до него так и не доехал: ошибку приходилось пересказывать словами, " +
            "а консультировать вслепую он умеет блестяще. Песочницы там тоже нет, так что проверить " +
            "свой код он не может, только верить в него.",
    ),
    Credit(
        name = "Grok",
        role = "За смелость",
        text = "Однажды попытался помочь. Что именно он сделал, мы так и не поняли, " +
            "но участие засчитано.",
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
