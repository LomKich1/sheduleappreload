# ScheduleApp

Нативное Android-приложение для просмотра расписания занятий колледжа.  
Kotlin + Jetpack Compose + Material3. Без WebView, без Supabase.

## 📥 Установка готового APK

Открой вкладку **Releases** → скачай `ScheduleApp-vX.X.X-debug.apk` → установи на телефон.  
(Настройки → Безопасность → Разрешить установку из неизвестных источников)

---

## 🔨 Самостоятельная сборка

### Вариант А — GitHub Actions (рекомендуется)

1. Сделай форк или залей репозиторий на GitHub
2. Сделай любой коммит в ветку `main`
3. Перейди в **Actions** → `Build APK` → дождись завершения (~3–5 мин)
4. APK появится во вкладке **Releases → latest**

**Debug APK работает сразу** — ничего настраивать не нужно.

### Вариант Б — локально (Android Studio)

```
File → Open → выбери папку проекта
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

Готовый APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## 🔑 Подписанный Release APK (опционально)

Нужен если планируешь публиковать в Google Play или хочешь,  
чтобы обновления устанавливались поверх старой версии.

### 1. Создай keystore (один раз)

```bash
keytool -genkey -v -keystore keystore.jks -alias schedule \
  -keyalg RSA -keysize 2048 -validity 10000
```

Запомни (или запиши в менеджер паролей):
- Пароль keystore
- Алиас: `schedule`
- Пароль ключа

### 2. Закодируй keystore в base64

```bash
# macOS
base64 -i keystore.jks | pbcopy

# Linux
base64 keystore.jks | xclip -selection clipboard

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore.jks")) | clip
```

### 3. Добавь секреты в GitHub

`Settings → Secrets and variables → Actions → New repository secret`

| Имя секрета | Значение |
|-------------|----------|
| `KEYSTORE_BASE64` | содержимое keystore.jks в base64 |
| `KEYSTORE_PASSWORD` | пароль keystore |
| `KEY_ALIAS` | `schedule` |
| `KEY_PASSWORD` | пароль ключа |

После этого CI будет создавать **оба** APK: debug и подписанный release.

---

## ⚙️ Изменить версию приложения

Отредактируй `gradle.properties`:

```properties
APP_VERSION=1.1.0       # видимая версия
APP_VERSION_CODE=2      # целое число, всегда увеличивать
```

---

## 📁 Структура проекта

```
app/src/main/java/com/schedule/app/
├── MainActivity.kt
├── data/
│   ├── model/          — Schedule, ScheduleFile
│   ├── parser/         — DocParser (OLE2 .doc → расписание)
│   ├── prefs/          — AppPrefs (SharedPreferences)
│   ├── remote/         — YandexDiskApi, GitHubApi
│   └── repository/     — ScheduleRepository
└── ui/
    ├── AppScaffold.kt
    ├── components/     — FileCard, FilesHeader
    ├── navigation/     — Screen, NavigationHolder, FloatingPillNav
    ├── screens/        — FilesScreen, BellsScreen, ScheduleScreen, SettingsScreen
    └── theme/          — AppTheme, AppColors (Dark / Light / AMOLED)
```
