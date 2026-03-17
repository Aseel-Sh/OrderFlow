param(
    [switch]$StartInfra,
    [switch]$SkipBuild,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Invoke-RepoCommand {
    param([string]$Command)

    if ($DryRun) {
        Write-Host "[DRY RUN] $Command"
        return
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -Command $Command
}

$stopCommand = "Set-Location '$root'; powershell -ExecutionPolicy Bypass -File .\scripts\stop-services.ps1"
Invoke-RepoCommand -Command $stopCommand

if (-not $DryRun) {
    Start-Sleep -Seconds 2
}

if ($StartInfra) {
    $infraCommand = "Set-Location '$root'; docker compose up -d"
    Invoke-RepoCommand -Command $infraCommand
}

if (-not $SkipBuild) {
    $buildCommand = "Set-Location '$root'; .\\mvnw.cmd -DskipTests install"
    Invoke-RepoCommand -Command $buildCommand
}

$serviceModules = @(
    "services/order-service",
    "services/inventory-service",
    "services/payment-service",
    "services/notification-service"
)

foreach ($module in $serviceModules) {
    $command = "Set-Location '$root'; .\\mvnw.cmd -pl $module spring-boot:run"

    if ($DryRun) {
        Write-Host "[DRY RUN] Start window for $module -> $command"
        continue
    }

    Start-Process powershell.exe -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        $command
    ) | Out-Null
}

if (-not $DryRun) {
    Write-Host "Started service windows for all modules."
}


