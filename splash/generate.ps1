# Generates FitApp splash-screen concept art through the text-to-image skill.
#
#   pwsh splash/generate.ps1 v1-ring-on-paper
#   pwsh splash/generate.ps1 -All
#
# Every variant is "<name>.prompt.txt" here; the shared style layer lives in
# "_style.txt" and the negative prompt in "_negative.txt". All three are read as
# UTF-8 on purpose: Windows PowerShell 5.1 decodes files as ANSI by default and
# turns Cyrillic into mojibake before it ever reaches the model.
#
# This file stays ASCII-only so that PS 5.1 parses it correctly without a BOM.

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Names,
    [switch] $All
)

$ErrorActionPreference = 'Stop'

$here = $PSScriptRoot
$style = (Get-Content -Raw -Encoding UTF8 (Join-Path $here '_style.txt')).Trim()
$negative = (Get-Content -Raw -Encoding UTF8 (Join-Path $here '_negative.txt')).Trim()

$generator = Join-Path $env:USERPROFILE '.dsh\skills\text-to-image\scripts\generate-image.mjs'
if (-not (Test-Path $generator)) { throw "generator not found: $generator" }

$variants = @(Get-ChildItem (Join-Path $here '*.prompt.txt') | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{ Name = $_.BaseName -replace '\.prompt$', ''; Path = $_.FullName }
})
$selected = if ($All -or $Names.Count -eq 0) { $variants } else {
    $Names | ForEach-Object {
        $wanted = $_
        $match = $variants | Where-Object { $_.Name -eq $wanted }
        if (-not $match) { throw "unknown variant: $wanted" }
        $match
    }
}

foreach ($variant in $selected) {
    $name = $variant.Name
    $body = (Get-Content -Raw -Encoding UTF8 $variant.Path).Trim()
    Write-Host "[$name] generating..."

    $raw = & node $generator --prompt "$style`n`n$body" --size '1080*1920' `
        --negative $negative --out $here
    if ($LASTEXITCODE -ne 0) { throw "$name : generator failed" }

    $result = $raw | Out-String | ConvertFrom-Json
    if (-not $result.ok) { throw "$name : $($result | ConvertTo-Json -Depth 5)" }

    $target = Join-Path $here "$name.png"
    Move-Item -Force $result.files[0] $target
    Write-Host "[$name] -> $target"
}