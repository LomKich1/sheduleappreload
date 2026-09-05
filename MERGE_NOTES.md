# MERGE_NOTES.md — что добавлено поверх архива Ромы

База — тот же коммит `d60d4ad`, что указан в `KOLYA_INSTRUCTION.md`. Дальше
накатано:

## 1. Свайп между экранами (Files↔Bells, Ученики↔Преподаватели)

Ромы это не касалось — он эти файлы не трогал (проверено дифом от `d60d4ad`,
совпадение 1:1). Изменены/добавлены:

- `ui/AppScaffold.kt`
- `ui/screens/ScheduleHostScreen.kt`
- `ui/navigation/FloatingPillNav.kt`
- `ui/components/ScheduleModeToggle.kt`
- `ui/components/SwipableTabProgress.kt` — новый, общий хук для обоих мест

## 2. Регрессия в сетевом слое — таймауты

При переезде с OkHttp на Ktor (`HttpClient()` без конфига) исчезли явные
таймауты, которые раньше стояли у `OkHttpClient.Builder()`
(`connectTimeout`/`readTimeout`). У голого Ktor-клиента таймаутов по
умолчанию нет — запрос мог зависнуть навсегда вместо явного отказа через
12/60 (GitHub) или 12/30 (Yandex) секунд.

Добавлен `data/remote/PlatformHttpClient.kt` (expect) +
`.desktop.kt`/`.android.kt`/`.ios.kt` (actual) — фабрика клиента с портативным
плагином `HttpTimeout` (работает одинаково на всех таргетах, включая iOS).
`GitHubApi.kt`/`YandexDiskApi.kt` переведены на неё, таймауты возвращены к
исходным значениям.

## 3. Windows-ROOT trust fallback

Отдельный фикс с прошлой сессии (для проблемы со скачиванием файлов на ПК
куратора в колледже — вероятно, HTTPS-инспекция антивируса, из-за которой
JVM-truststore не доверяет сертификату, которому доверяет сама Windows).

Раньше был написан как расширение прямо на `OkHttpClient.Builder` — само API
не изменилось, но переехал в `shared/src/desktopMain/.../WindowsTrustFallback.kt`
(а не commonMain): использует `okhttp3`/`javax.net.ssl`/`java.security`
напрямую, ни один из этих пакетов не существует на Kotlin/Native — если
оставить в commonMain, сборка под iOS не скомпилируется. Подключается из
`PlatformHttpClient.desktop.kt` через `engine { config { withWindowsTrustFallback() } }`.
На Android/iOS не используется — там либо своя система доверия сертификатам
(Android), либо `Windows-ROOT` в принципе не существует.

## Не тестировано

Собрать и запустить локально (Xcode/Mac под рукой нет) — рассчитываем на
GitHub Actions macOS/iOS-раннер из workflow Ромы. Если CI красный — кидайте
лог, разберёмся.

## Синхронизация с dev (второй раунд, перед самим переносом)

Между тем, как Рома забирал `dev` (`d60d4ad`), и моментом переноса этого
архива `dev` успел уйти вперёд ещё на несколько своих коммитов — не только
версия:

- `gradle.properties` — версия подтянута до актуальной (`1.0.4`/`4`), при
  этом строка Ромы про `compose.desktop.packaging.checkJdkVendor` сохранена.
- **Фикс краша на десктопе** (`vm: XxxViewModel = viewModel()` →
  `viewModel { XxxViewModel() }`) — был сделан на `dev` уже ПОСЛЕ того, как
  Рома скопировал код себе, поэтому в его архиве отсутствовал. Возвращён в
  `FilesScreen.kt`, `ScheduleScreen.kt`, `TeacherScheduleScreen.kt` — причём
  в `FilesScreen.kt` он мирно сосуществует с Роминой правкой
  (`java.util.Calendar` → кроссплатформенный `currentLocalDateTime()`), это
  разные строки одного файла.

Сверено построчно: каждый файл, отличавшийся между `d60d4ad` и текущим
`dev`, либо побайтово совпадает с этим архивом, либо отличается ТОЛЬКО за
счёт намеренных Роминых правок под iOS — ничего не потеряно.

