#Requires -Version 5.1
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root

$mvnw = Join-Path $Root "local_server\mvnw.cmd"
if (-not (Test-Path $mvnw)) { throw "Missing $mvnw" }

Write-Host "Installing driver-api..."
& $mvnw -f (Join-Path $Root "driver-api\pom.xml") -DskipTests install
if ($LASTEXITCODE -ne 0) { throw "driver-api install failed" }

Write-Host "Installing zhensi-driver..."
& $mvnw -f (Join-Path $Root "zhensi-driver\pom.xml") -DskipTests install
if ($LASTEXITCODE -ne 0) { throw "zhensi-driver install failed" }

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "Missing command: npm (need Node.js 22+)"
}

Write-Host "npm install (local_frontend)..."
Push-Location (Join-Path $Root "local_frontend")
try {
    npm install
    if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
} finally {
    Pop-Location
}

Write-Host "OK  Next: start backend then frontend (see ai-build/AGENTS.md §3–4)"
