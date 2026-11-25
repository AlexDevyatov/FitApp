#!/usr/bin/env pwsh
# Установка GitHub Actions secrets для FitApp через GitHub CLI.
# Требует: gh CLI, авторизованный (gh auth login) или переменная GH_TOKEN.
#
# Использование:
#   .\scripts\setup-github-secrets.ps1 -RepoOwner "AlexDevyatov" -RepoName "FitApp"
#   .\scripts\setup-github-secrets.ps1 -RepoOwner "AlexDevyatov" -RepoName "FitApp" -KeystorePath "release.keystore"

param(
    [Parameter(Mandatory = $true)][string]$RepoOwner,
    [Parameter(Mandatory = $true)][string]$RepoName,
    [string]$KeystorePath = "release.keystore",
    [string]$Alias = "fitapp",
    [int]$MaxAttempts = 10
)

$ErrorActionPreference = "Stop"
$repo = "$RepoOwner/$RepoName"

# --- проверки окружения -----------------------------------------------------
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    $ghFallback = "C:\Program Files\GitHub CLI\gh.exe"
    if (Test-Path $ghFallback) {
        function gh { & $ghFallback @args }
    } else {
        Write-Error "gh CLI не найден. Установите: winget install --id GitHub.cli --exact"
        exit 1
    }
}

& gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error "gh не авторизован. Выполните: gh auth login  (или задайте `$env:GH_TOKEN)"
    exit 1
}

if (-not (Test-Path $KeystorePath)) {
    Write-Error "Keystore не найден: $KeystorePath. Сначала запустите scripts/generate-keystore.ps1"
    exit 1
}

# --- ввод паролей -----------------------------------------------------------
$storeSecure = Read-Host "Пароль keystore" -AsSecureString
$storePass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($storeSecure))

$keySecure = Read-Host "Пароль ключа (Enter — совпадает с keystore)"
if ($keySecure.Length -eq 0) { $keyPass = $storePass }
else {
    $keyPass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($keySecure))
}

# --- установка с повторами (API GitHub периодически отдаёт 500) -------------
function Set-Secret {
    param([string]$Name, [string]$Value)
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        $Value | & gh secret set $Name --repo $repo *> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Host ("  [OK]    {0}" -f $Name) -ForegroundColor Green
            return $true
        }
        Write-Host ("  [retry] {0} ({1}/{2})" -f $Name, $i, $MaxAttempts) -ForegroundColor Yellow
        Start-Sleep -Seconds (5 * $i)
    }
    Write-Host ("  [FAIL]  {0}" -f $Name) -ForegroundColor Red
    return $false
}

Write-Host ""
Write-Host "Установка secrets в $repo" -ForegroundColor Cyan
$ok = $true
$ok = (Set-Secret -Name "KEYSTORE_BASE64"   -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $KeystorePath))))) -and $ok
$ok = (Set-Secret -Name "KEYSTORE_PASSWORD" -Value $storePass) -and $ok
$ok = (Set-Secret -Name "KEY_ALIAS"         -Value $Alias)     -and $ok
$ok = (Set-Secret -Name "KEY_PASSWORD"      -Value $keyPass)   -and $ok

Write-Host ""
& gh secret list --repo $repo

if ($ok) {
    Write-Host ""
    Write-Host "Готово. Создайте релиз: git tag v1.0.0 && git push origin v1.0.0" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Часть secrets не установилась. Проверьте статус GitHub: https://www.githubstatus.com" -ForegroundColor Yellow
    exit 1
}