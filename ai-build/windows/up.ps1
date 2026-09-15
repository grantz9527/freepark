#Requires -Version 5.1
# Local Docker: MySQL 8.4.11 + HyperLPR3 0.1.3 + Frigate 0.17.2
# Legacy -WithLpr / -WithFrigate are ignored (always started).
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

Write-Host "Starting Docker services (MySQL 8.4.11, HyperLPR3 0.1.3, Frigate 0.17.2)..."
& docker compose -f ai-build/docker-compose.yml up -d --build
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
Wait-Healthy "freepark-hyperlpr3" 180
Wait-Healthy "frigate" 180 -AllowRunning

Write-Host "OK  MySQL :3307  (8.4.11)"
Write-Host "OK  HyperLPR3 :8715  (0.1.3)"
Write-Host "OK  Frigate :5000  go2rtc :1984  (0.17.2)"
Write-Host "Next: powershell -File ai-build/windows/install.ps1"
Write-Host "Then start local_server (8081) and local_frontend (5173)."
