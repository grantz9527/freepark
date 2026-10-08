#Requires -Version 5.1
# Save MySQL + Frigate + HyperLPR3 + freepark-local into one tar (offline load).
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root

$dist = Join-Path $Root "ai-build\dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$tar = Join-Path $dist "freepark-images.tar"

$images = @(
    "mysql:8.4.11",
    "ghcr.io/blakeblackshear/frigate:0.17.2",
    "freepark-hyperlpr3:0.1.3",
    "freepark-local:0.0.1"
)

foreach ($img in $images) {
    docker image inspect $img | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Missing image $img. Build/start the stack first (docker compose -f docker/local/docker-compose.yml up -d --build)."
    }
}

Write-Host "Saving $($images -join ', ') -> $tar"
& docker save -o $tar @images
if ($LASTEXITCODE -ne 0) { throw "docker save failed" }
Write-Host "OK  $tar"
Write-Host "Load: docker load -i ai-build/dist/freepark-images.tar"
