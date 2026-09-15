#Requires -Version 5.1
$ErrorActionPreference = "Stop"

function Get-Ok([string] $Url) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 8
        return [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw "Request failed: $Url — $($_.Exception.Message)"
    }
}

$health = Get-Ok "http://127.0.0.1:8081/actuator/health"
if ($health -ne 200) { throw "Backend health HTTP $health" }

$body = '{"username":"admin","password":"admin123"}'
try {
    $login = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8081/api/v1/auth/login" `
        -ContentType "application/json" -Body $body -TimeoutSec 15
} catch {
    throw "Login failed: $($_.Exception.Message)"
}
if (-not $login.data.token) { throw "Login response missing token" }

$ui = $null
foreach ($port in 5173, 5174, 5175) {
    try {
        $code = Get-Ok "http://127.0.0.1:$port/"
        if ($code -ge 200 -and $code -lt 500) {
            $ui = "http://localhost:$port"
            break
        }
    } catch { }
}

Write-Host "OK  backend health + admin login"
if ($ui) { Write-Host "OK  console $ui" } else { Write-Host "WARN  Vite UI not detected on 5173-5175 (start local_frontend: npm run dev)" }
Write-Host "Login: admin / admin123"
