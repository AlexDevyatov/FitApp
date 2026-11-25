#!/usr/bin/env pwsh
# Скрипт для генерации keystore и подготовки secrets для GitHub Actions
# Запуск: .\scripts\generate-keystore.ps1

param(
    [string]$KeystoreName = "release.keystore",
    [string]$Alias = "fitapp",
    [int]$ValidityDays = 10000
)

$ErrorActionPreference = "Stop"

Write-Host "=== Генерация keystore для FitApp ===" -ForegroundColor Cyan
Write-Host ""

# Запрос паролей
$storePassword = Read-Host "Введите пароль для keystore (минимум 6 символов)" -AsSecureString
if ($storePassword.Length -lt 6) {
    Write-Error "Пароль должен быть минимум 6 символов"
    exit 1
}

$keyPassword = Read-Host "Введите пароль для ключа (или Enter для того же, что keystore)" -AsSecureString
if ($keyPassword.Length -eq 0) {
    $keyPassword = $storePassword
}

# Преобразование SecureString в plain text для keytool
$storePassPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePassword)
)
$keyPassPlain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($keyPassword)
)

# Проверка наличия keytool
try {
    $null = Get-Command keytool -ErrorAction Stop
} catch {
    Write-Error "keytool не найден. Установите JDK и добавьте в PATH."
    exit 1
}

# Генерация keystore
Write-Host ""
Write-Host "Генерация keystore..." -ForegroundColor Yellow

$keystorePath = Join-Path $PSScriptRoot ".." $KeystoreName

keytool -genkey -v `
    -keystore $keystorePath `
    -alias $Alias `
    -keyalg RSA -keysize 2048 `
    -validity $ValidityDays `
    -storepass $storePassPlain `
    -keypass $keyPassPlain `
    -dname "CN=FitApp, OU=Development, O=FitApp, L=Unknown, ST=Unknown, C=RU"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Ошибка при генерации keystore"
    exit 1
}

Write-Host "✓ Keystore создан: $keystorePath" -ForegroundColor Green
Write-Host ""

# Кодирование в Base64
Write-Host "Кодирование в Base64..." -ForegroundColor Yellow
$base64Content = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
$base64Path = Join-Path $PSScriptRoot ".." "keystore_base64.txt"
$base64Content | Out-File -Encoding ASCII $base64Path

Write-Host "✓ Base64 сохранён: $base64Path" -ForegroundColor Green
Write-Host ""

# Вывод инструкций
Write-Host "=== Следующие шаги ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Добавьте secrets в GitHub:" -ForegroundColor White
Write-Host "   Settings → Secrets and variables → Actions" -ForegroundColor Gray
Write-Host ""
Write-Host "   KEYSTORE_BASE64 = содержимое файла keystore_base64.txt" -ForegroundColor Yellow
Write-Host "   KEYSTORE_PASSWORD = $storePassPlain" -ForegroundColor Yellow
Write-Host "   KEY_ALIAS = $Alias" -ForegroundColor Yellow
Write-Host "   KEY_PASSWORD = $keyPassPlain" -ForegroundColor Yellow
Write-Host ""
Write-Host "2. Для создания релиза:" -ForegroundColor White
Write-Host "   git tag v1.0.0" -ForegroundColor Gray
Write-Host "   git push origin v1.0.0" -ForegroundColor Gray
Write-Host ""
Write-Host "⚠ ВАЖНО: Сохраните keystore в безопасном месте!" -ForegroundColor Red
Write-Host "   Файл: $keystorePath" -ForegroundColor Red
Write-Host "   Без него вы не сможете обновлять приложение." -ForegroundColor Red
Write-Host ""

# Очистка паролей из памяти
$storePassPlain = $null
$keyPassPlain = $null
[GC]::Collect()

Write-Host "✓ Готово!" -ForegroundColor Green
