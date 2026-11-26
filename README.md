# FitApp

Android-приложение для учёта питания и тренировок с ИИ-анализом блюд по фото.

[![CI](https://github.com/AlexDevyatov/FitApp/actions/workflows/ci.yml/badge.svg)](https://github.com/AlexDevyatov/FitApp/actions/workflows/ci.yml)

## Стек

- Kotlin + Jetpack Compose (Material 3), Navigation Compose
- Room (KSP), WorkManager, DataStore + EncryptedSharedPreferences
- CameraX, Coil
- OkHttp + kotlinx.serialization
- AGP 8.11.1, Kotlin 2.1.20, Gradle 8.13, JDK 21, compileSdk 36 / minSdk 26

## Сборка

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit-тесты
./gradlew lintDebug              # lint
./gradlew assembleRelease        # release (нужна настройка подписи)
```

Для сборки и тестов нужен JDK 21 (`JAVA_HOME`): на JDK 17 тестовый процесс падает
с `UnsupportedClassVersionError`, потому что `jvmTarget = 21`. Если путь к проекту
содержит кириллицу, `testDebugUnitTest` может не найти классы тестов — запускайте
из каталога с латинским путём.

## CI/CD

GitHub Actions собирает, тестирует и линтит каждый push в `main` и каждый pull request; по тегу `v*` выпускает подписанные APK и AAB.

Настройка секретов и выпуск релиза — в [CI_SETUP.md](CI_SETUP.md).

## Структура

```
app/src/main/java/ru/fitapp/
├── ai/       # анализ блюд (Qwen, mock), схемы, генерация отчётов
├── data/     # Room: сущности, DAO
├── domain/   # модели, расчёты (TDEE, отчёты)
└── ui/       # Compose-экраны, ViewModel'ы
```

Прочее: [PLAN.md](PLAN.md), [ANDROID_SETUP.md](ANDROID_SETUP.md), [LOGGING.md](LOGGING.md).
