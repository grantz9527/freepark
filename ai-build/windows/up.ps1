#Requires -Version 5.1
param(
    [switch] $WithLpr,
    [switch] $WithFrigate
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root

function Require-Cmd($Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name"
    }
}

Require-Cmd docker

$cfgDir = Join-Path $Root "docker\mosquitto\config"
$dataDir = Join-Path $Root "docker\mosquitto\data"
$logDir = Join-Path $Root "docker\mosquitto\log"
New-Item -ItemType Directory -Force -Path $cfgDir, $dataDir, $logDir | Out-Null

$pwfile = Join-Path $cfgDir "pwfile"
if (-not (Test-Path $pwfile)) {
    Write-Host "Generating docker/mosquitto/config/pwfile (freepark / freepark)"
    $cfgUnix = ($cfgDir -replace "\\", "/")
    docker run --rm -v "${cfgUnix}:/mosquitto/config" eclipse-mosquitto:2 `
        mosquitto_passwd -c -b /mosquitto/config/pwfile freepark freepark
    if ($LASTEXITCODE -ne 0) { throw "mosquitto_passwd failed" }
}

$dc = @("compose", "-f", "ai-build/docker-compose.yml")
if ($WithLpr) { $dc += @("--profile", "lpr") }
if ($WithFrigate) { $dc += @("--profile", "frigate") }
$dc += @("up", "-d")
if ($WithLpr) { $dc += "--build" }

Write-Host "Starting Docker services..."
& docker @dc
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }

function Wait-Healthy([string] $Container, [int] $Seconds = 120, [switch] $AllowRunning) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    $status = ""
    while ((Get-Date) -lt $deadline) {
        $status = docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $Container 2>$null
        if ($status -eq "healthy") { return }
        if ($AllowRunning -and $status -eq "running") { return }
        Start-Sleep -Seconds 3
    }
    docker logs --tail 40 $Container
    throw "$Container not healthy within ${Seconds}s (last status: $status)"
}

Wait-Healthy "freepark-local-mysql" 90
Wait-Healthy "freepark-mosquitto" 90
if ($WithLpr) { Wait-Healthy "freepark-hyperlpr3" 180 }
if ($WithFrigate) { Wait-Healthy "frigate" 180 -AllowRunning }

Write-Host "OK  MySQL :3307  MQTT :1883"
if ($WithLpr) { Write-Host "OK  HyperLPR3 :8715" }
if ($WithFrigate) { Write-Host "OK  Frigate :5000  go2rtc :1984" }
Write-Host "Next: powershell -File ai-build/windows/install.ps1"
