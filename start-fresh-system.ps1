param(
    [switch]$SkipSeed
)

# Set UTF-8 encoding for console output
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   RENTAL SYSTEM - KHOI DONG HE THONG VA RESET & SEED DATA TEST MOI    " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Check if we are running inside rental-system-backend or in root
if (Test-Path (Join-Path $baseDir "compose.yaml")) {
    $backendDir = $baseDir
} elseif (Test-Path (Join-Path $baseDir "rental-system-backend")) {
    $backendDir = Join-Path $baseDir "rental-system-backend"
} else {
    $backendDir = $baseDir
}

$postgresContainer = "rental-postgres"

function Wait-ContainerHealthy($name, [int]$timeoutSeconds = 240) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $running = docker inspect -f "{{.State.Running}}" $name 2>$null
        if ($running -eq "true") {
            $health = docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" $name 2>$null
            if ($health -eq "healthy" -or $health -eq "none") { return }
        }
        Start-Sleep -Seconds 3
    }
    throw "Container $name không healthy sau $timeoutSeconds giây."
}

# 1. Khoi dong Docker Backend (neu chua chay)
Write-Host ""
Write-Host "[1/3] Kiểm tra Docker Backend..." -ForegroundColor Yellow
$stackRunning = $false
try {
    $pg = docker inspect -f "{{.State.Running}}" $postgresContainer 2>$null
    if ($pg -eq "true") { $stackRunning = $true }
} catch {}

if (-not $stackRunning) {
    Write-Host "  -> Chưa chạy. Đang khởi động toàn bộ stack (postgres, kafka, minio, discovery, services)..." -ForegroundColor Yellow
    Push-Location $backendDir
    try {
        docker compose up -d
        if ($LASTEXITCODE -ne 0) { throw "docker compose up -d thất bại (exit $LASTEXITCODE)." }
    } finally {
        Pop-Location
    }
    Write-Host "  -> Chờ postgres healthy..." -ForegroundColor Yellow
    Wait-ContainerHealthy $postgresContainer
    Write-Host "  -> postgres đã sẵn sàng." -ForegroundColor Green
} else {
    Write-Host "  -> Stack đang chạy. Tiến hành reset & seed." -ForegroundColor Green
}

# 2. Xoa sach 7 DB + restart service + seed
Write-Host ""
Write-Host "[2/3] Xóa sạch 7 database, dựng lại schema và nạp dữ liệu kiểm thử..." -ForegroundColor Yellow

$seedScript = if (Test-Path (Join-Path $backendDir "seed\reset-and-seed.ps1")) {
    Join-Path $backendDir "seed\reset-and-seed.ps1"
} else {
    Join-Path $baseDir "seed\reset-and-seed.ps1"
}

if (-not (Test-Path $seedScript)) {
    throw "Không tìm thấy $seedScript"
}

if ($SkipSeed) {
    & $seedScript -SkipSeed
} else {
    & $seedScript
}
if ($LASTEXITCODE -ne 0) {
    throw "Reset & seed thất bại (exit $LASTEXITCODE)."
}

# 3. Ket qua
Write-Host ""
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "   HE THONG DA DUOC RESET VA SEED DATA MOI SACH SE 100%!              " -ForegroundColor Green
Write-Host "   Bootstrap admin : e2e.admin@example.com / E2ePass123!               " -ForegroundColor Green
Write-Host "   Tai khoan seed  : seed.*@test.local / Test@12345                    " -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
