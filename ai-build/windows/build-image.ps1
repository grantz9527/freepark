#Requires -Version 5.1
# Build freepark-local:0.0.1 (console + API + drivers)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Missing command: docker"
}

Write-Host "Building freepark-local:0.0.1 (frontend + local_server)..."
& docker build -f docker/local/Dockerfile -t freepark-local:0.0.1 .
if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

Write-Host "OK  Image freepark-local:0.0.1"
Write-Host "Run stack: docker compose -f docker/local/docker-compose.yml up -d"
Write-Host "Console:   http://127.0.0.1:8081  (admin / admin123)"
