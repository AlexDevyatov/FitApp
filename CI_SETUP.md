# CI/CD — FitApp

GitHub Actions: сборка, тесты, lint и подписанные релизы.

## Что настроено

| Workflow | Файл | Триггер | Что делает |
|----------|------|---------|------------|
| CI | `.github/workflows/ci.yml` | push в `main`, pull request | `assembleDebug`, `testDebugUnitTest`, `lintDebug` + артефакты |
| Release | `.github/workflows/release.yml` | тег `v*`, ручной запуск | подписанные APK и AAB, GitHub Release |

Конкурентные запуски в CI отменяются (`concurrency`), поэтому устаревшие сборки не тратят минуты.

## Секреты репозитория

`Settings → Secrets and variables → Actions`:

| Secret | Значение |
|--------|----------|
| `KEYSTORE_BASE64` | keystore в Base64 |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_ALIAS` | alias ключа (в FitApp — `fitapp`) |
| `KEY_PASSWORD` | пароль ключа. **Для PKCS12 совпадает с `KEYSTORE_PASSWORD`** |

`KEY_PASSWORD` обязан равняться `KEYSTORE_PASSWORD`: `keytool` создаёт PKCS12-хранилища, где отдельный пароль ключа игнорируется.

## Как выпустить релиз

```bash
git tag v1.0.0
git push origin v1.0.0
```

Workflow соберёт `app-release.apk` и `app-release.aab`, проверит подпись через `apksigner` и создаст GitHub Release.

Номер версии берётся из `app/build.gradle.kts` (`versionCode` / `versionName`) — **поднимайте их вручную перед тегом**, иначе Google Play отклонит повторный `versionCode`.

Ручной запуск без тега: `Actions → Release Build → Run workflow` (артефакты будут, GitHub Release — нет).

## Генерация keystore и загрузка секретов

```powershell
# 1. Создать keystore + показать значения для секретов
.\scripts\generate-keystore.ps1

# 2. Загрузить секреты в GitHub (нужен установленный и авторизованный gh)
.\scripts\setup-github-secrets.ps1 -RepoOwner "AlexDevyatov" -RepoName "FitApp"
```

Linux/macOS: `./scripts/generate-keystore.sh`.

Скрипты требуют [GitHub CLI](https://cli.github.com/): `winget install --id GitHub.cli --exact`, затем `gh auth login`.

## Локальная сборка с подписью

```powershell
$env:KEYSTORE_FILE = "$PWD\release.keystore"
$env:KEYSTORE_PASSWORD = "пароль"
$env:KEY_ALIAS = "fitapp"
$env:KEY_PASSWORD = "пароль"
.\gradlew.bat assembleRelease
```

`KEYSTORE_FILE` в `app/build.gradle.kts` разрешается относительно модуля `app`, поэтому в CI указан `../release.keystore`. Без переменной `KEYSTORE_FILE` release собирается неподписанным — локальная разработка не ломается.

## Ограничения и известные особенности

**Юнит-тесты не запускаются на кириллическом пути.** В этом проекте (`D:\Алексей\Проекты\FitApp`) `testDebugUnitTest` падает с `ClassNotFoundException` для всех тест-классов, хотя классы скомпилированы. На ASCII-пути те же тесты проходят. Обходной путь для локального прогона:

```powershell
# junction на ASCII-путь
New-Item -ItemType Junction -Path C:\work\fitapp -Target "D:\Алексей\Проекты\FitApp"
cd C:\work\fitapp; .\gradlew.bat testDebugUnitTest
```

В CI проблемы нет — runner использует `/home/runner/work/FitApp/FitApp`.

**`android.overridePathCheck=true`** лежит в глобальном `~/.gradle/gradle.properties` — это обход проверки AGP на не-ASCII путь. Без него сборка на этой машине не стартует.

## Текущее состояние

Настроено и проверено на `AlexDevyatov/FitApp`:

- CI-прогон `push в main` — зелёный (~4 мин)
- Release-прогон `workflow_dispatch` — зелёный: секреты прочитаны, keystore декодирован, `apksigner verify` подтвердил подпись
- Релиз [v0.1.0](https://github.com/AlexDevyatov/FitApp/releases/tag/v0.1.0) опубликован с `app-release.apk` и `app-release.aab`

Секреты `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` уже добавлены в репозиторий.

## Проверка статуса

- CI: вкладка **Actions** → workflow **CI**
- Релизы: вкладка **Releases**
- Состояние GitHub: https://www.githubstatus.com — при инцидентах запись секретов отвечает `HTTP 500`