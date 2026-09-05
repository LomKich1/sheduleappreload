# Инструкция для Коли: macOS и iPhone

Архив подготовлен на основе ветки `dev`, коммит
`d60d4adf108694773d9d2f99e21f307243310f73`. На момент упаковки удалённая
ветка `dev` всё ещё указывала на этот коммит.

## Что находится в архиве

- исходный проект ScheduleApp без `.git`, кэшей и результатов сборки;
- поддержка macOS через существующий Compose Desktop-модуль;
- iOS-цели `iosArm64`, `iosSimulatorArm64` и `iosX64`;
- Xcode-проект `iosApp/ScheduleApp.xcodeproj`;
- iOS-реализации настроек, логирования и платформенных функций;
- сетевой и JSON-код, перенесённый на кроссплатформенные библиотеки;
- GitHub Actions для Android, Windows, Linux, macOS и проверки iOS;
- `script/build_and_run.sh` для локального запуска на Mac.

## Как положить изменения в GitHub

Работать лучше через Git, а не через кнопку Upload files: в проекте есть
скрытые каталоги `.github` и `.codex`, а также бинарный Gradle Wrapper JAR.

1. Обнови свою копию репозитория:

   ```bash
   git switch dev
   git pull origin dev
   git rev-parse --short HEAD
   ```

   Ожидаемый коммит перед копированием: `d60d4ad`. Если показан другой коммит,
   в `dev` уже появились новые изменения — сначала объедини их с этой версией
   или свяжись с Романом, чтобы ничего не затереть.

2. Распакуй переданный архив в отдельную папку. Скопируй всё содержимое
   распакованной папки поверх своей локальной копии репозитория. На macOS/Linux
   это можно сделать так:

   ```bash
   rsync -a /путь/к/ScheduleApp-Apple-ready-for-Kolya/ /путь/к/sheduleappreload/
   ```

3. В терминале из папки `sheduleappreload` проверь и отправь изменения:

   ```bash
   git status
   git add -A
   git update-index --chmod=+x gradlew script/build_and_run.sh
   git commit -m "Add macOS and iOS support"
   git push origin dev
   ```

4. Открой вкладку Actions на GitHub. После пуша в `dev` должны запуститься:

   - Android APK;
   - Windows MSI, Linux DEB и macOS DMG;
   - iOS frameworks и приложение для iPhone Simulator.

   Публикация GitHub Release на ветке `dev` намеренно пропускается. Она
   выполняется только после слияния в `main` или `master`.

## Проверка на Mac

Нужны JDK 17 и macOS. Проверка сборки:

```bash
./script/build_and_run.sh --verify
```

Сборка и запуск:

```bash
./script/build_and_run.sh
```

Создание DMG:

```bash
./gradlew :desktopApp:packageDmg
```

DMG появится в `desktopApp/build/compose/binaries/main/dmg/`.

Текущий `.app` имеет локальную ad-hoc подпись и подходит для разработки. Для
публичной раздачи без предупреждений Gatekeeper нужны учётная запись Apple
Developer, сертификат Developer ID Application и нотарификация. После настройки
подписи проверь пакет:

```bash
codesign --verify --deep --strict --verbose=2 ScheduleApp.app
spctl -a -t exec -vv ScheduleApp.app
xcrun notarytool submit ScheduleApp.dmg --keychain-profile "ScheduleApp" --wait
xcrun stapler staple ScheduleApp.dmg
```

Нотарификация не нужна для обычного локального запуска во время разработки.

## Запуск на iPhone

1. Установи JDK 17 и актуальный Xcode.
2. Открой `iosApp/ScheduleApp.xcodeproj`.
3. Выбери схему `ScheduleApp` и iPhone Simulator — для него подпись не нужна.
4. Для физического iPhone открой target `ScheduleApp` → Signing & Capabilities.
5. Выбери свою Apple Developer Team и замени `com.schedule.app.ios` на
   уникальный Bundle Identifier, который принадлежит этой Team.
6. Подключи iPhone, выбери его как Run Destination и нажми Run.

Минимальная версия системы — iOS 17.2. Для публикации в App Store нужна платная
Apple Developer Program: в Xcode выбери Product → Archive, затем в Organizer —
Distribute App. Подписанный `.ipa` в репозиторий коммитить не нужно.

## Что уже проверено

- macOS `.app` успешно компилируется и создаётся;
- iPhone Simulator `.app` успешно компилируется;
- Release `.app` для настоящего iPhone arm64 успешно компилируется без подписи;
- framework `ScheduleShared` для iPhone arm64 успешно линкуется;
- Xcode-проект и `Info.plist` проходят проверку;
- workflow GitHub Actions имеет корректный YAML-синтаксис.

Фактический запуск на конкретном iPhone и публикация в App Store остаются за
владельцем Apple Developer Team, потому что эти действия требуют его сертификата
и профиля подписи.
