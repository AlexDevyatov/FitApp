# Android SDK и эмулятор — установка

Установлено локально в домашний каталог (без root/sudo).

## Что установлено

| Компонент | Версия | Путь |
|---|---|---|
| JDK (Temurin) | 21.0.13.1 LTS | `~/.local/opt/jdk21` |
| Android SDK | API 36, 37.0 | `~/Android/Sdk` |
| Command-line tools | 23.0.0 | `~/Android/Sdk/cmdline-tools/latest` |
| Platform-tools (adb) | 37.0.3 | `~/Android/Sdk/platform-tools` |
| Emulator | 37.3.10 | `~/Android/Sdk/emulator` |
| Build-tools | 36.0.0, 37.0.0 | `~/Android/Sdk/build-tools` |
| Системный образ | Android 17 (API 37), Google APIs, x86_64 | `~/Android/Sdk/system-images/android-37.0/google_apis/x86_64` |
| AVD | `FitApp_API37` (Pixel 9, Android 17) | `~/.android/avd/FitApp_API37.avd` |

## Окружение

Переменные добавлены в `~/.bashrc` и `~/.profile`, в новом терминале доступны сразу:

```bash
JAVA_HOME=~/.local/opt/jdk21
ANDROID_HOME=~/Android/Sdk
```

Проверка:

```bash
adb devices          # должен быть emulator-5554  device
emulator -list-avds  # FitApp_API37
```

## Запуск и остановка эмулятора

```bash
avd-start            # запустить FitApp_API37 в фоне и дождаться загрузки
avd-stop             # погасить эмулятор
```

Вручную:

```bash
emulator -avd FitApp_API37 -gpu swiftshader_indirect -no-snapshot -no-boot-anim -no-audio &
adb wait-for-device
adb shell getprop sys.boot_completed   # 1 — загрузился
```

Эмулятор использует KVM (`emulator -accel-check` → KVM usable), окно выводится на дисплей `:1`.

## Установка приложения

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p <package.name> -c android.intent.category.LAUNCHER 1
adb logcat | grep -i <tag>
```

## Полезное

```bash
sdkmanager --list                                   # доступные пакеты
sdkmanager "system-images;android-36;google_apis;x86_64"
avdmanager create avd -n Test_API36 -k "system-images;android-36;google_apis;x86_64" -d pixel_9
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
```

Замечания:

- `sdkmanager` помечен как deprecated в пользу `android sdk`, но работает.
- JDK 21 установлен локально, потому что системный Java 8 не подходит для sdkmanager.
- Лицензии SDK приняты, все компоненты скачаны в `~/Android/Sdk` (≈7 ГБ).
- Скриншот загруженного эмулятора: `~/Android/emulator-booted.png`.