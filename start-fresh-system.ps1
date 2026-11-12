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

# 1. Xác định thư mục Backend và Frontend
if (Test-Path (Join-Path $baseDir "compose.yaml")) {
    $backendDir = $baseDir
    $repoRoot = Split-Path -Parent $baseDir
    $frontendDir = Join-Path $repoRoot "rental-system-frontend"
} elseif (Test-Path (Join-Path $baseDir "rental-system-backend")) {
    $backendDir = Join-Path $baseDir "rental-system-backend"
    $repoRoot = $baseDir
    $frontendDir = Join-Path $baseDir "rental-system-frontend"
} else {
    $backendDir = $baseDir
    $repoRoot = $baseDir
    $frontendDir = Join-Path $baseDir "rental-system-frontend"
}

Write-Host ""
Write-Host "[BƯỚC 0/3] Kiểm tra điều kiện tiên quyết (Pre-flight Checks)..." -ForegroundColor Yellow

# Kiểm tra Node.js
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
$nodeExe = if ($nodeCmd) { "node" } elseif (Test-Path "C:\Program Files\nodejs\node.exe") { "C:\Program Files\nodejs\node.exe" } else { $null }

if (-not $nodeExe) {
    Write-Host ""
    Write-Host "❌ [LỖI TIÊN QUYẾT] Không tìm thấy Node.js trên máy tính!" -ForegroundColor Red
    Write-Host "   - Script seed data và giao diện Frontend bắt buộc cần Node.js." -ForegroundColor Red
    Write-Host "   👉 Vui lòng tải và cài đặt Node.js LTS (khuyến nghị v20+) tại: https://nodejs.org/" -ForegroundColor Cyan
    Write-Host "   👉 Sau khi cài xong, mở lại cửa sổ cmd/terminal mới và chạy lại lệnh này." -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

$nodeVer = & $nodeExe -v 2>$null
$majorVer = [int]($nodeVer -replace '^v(\d+)\..*$', '$1')
if ($majorVer -lt 18) {
    Write-Host ""
    Write-Host "❌ [LỖI TIÊN QUYẾT] Phiên bản Node.js hiện tại ($nodeVer) quá cũ!" -ForegroundColor Red
    Write-Host "   - Hệ thống yêu cầu Node.js >= 18 (khuyến nghị v20+ LTS) vì dùng Fetch API & FormData chuẩn." -ForegroundColor Red
    Write-Host "   👉 Vui lòng cập nhật Node.js tại: https://nodejs.org/" -ForegroundColor Cyan
    Write-Host ""
    exit 1
}
Write-Host "  ✔ Node.js: $nodeVer (hợp lệ)" -ForegroundColor Green

# Kiểm tra Docker CLI
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host ""
    Write-Host "❌ [LỖI TIÊN QUYẾT] Không tìm thấy lệnh 'docker' trong hệ thống!" -ForegroundColor Red
    Write-Host "   - Hệ thống backend microservices chạy trên Docker containers." -ForegroundColor Red
    Write-Host "   👉 Vui lòng tải và cài đặt Docker Desktop tại: https://www.docker.com/products/docker-desktop/" -ForegroundColor Cyan
    Write-Host ""
    exit 1
}

# Kiểm tra Docker Desktop Engine đã bật chưa
$dockerRunning = $false
try {
    $null = docker info 2>&1
    if ($LASTEXITCODE -eq 0) { $dockerRunning = $true }
} catch {}

if (-not $dockerRunning) {
    Write-Host ""
    Write-Host "❌ [LỖI TIÊN QUYẾT] Docker Desktop chưa được bật (Docker daemon is not running)!" -ForegroundColor Red
    Write-Host "   👉 HÃY BẬT DOCKER DESKTOP TRÊN MÁY TÍNH CỦA BẠN." -ForegroundColor Yellow
    Write-Host "   👉 Chờ biểu tượng con cá voi ở góc dưới bên phải chuyển sang màu XANH LÁ (Engine running)." -ForegroundColor Yellow
    Write-Host "   👉 Sau đó chạy lại file start-fresh-system.bat này." -ForegroundColor Cyan
    Write-Host ""
    exit 1
}
Write-Host "  ✔ Docker Desktop: Đang hoạt động (Engine running)" -ForegroundColor Green

# Luôn biên dịch lại backend trước khi dựng container: jar cũ trong target/ rất dễ lệch
# so với source mới (đặc biệt sau khi pull code), gây lỗi khó hiểu khi test.
$expectedJars = @(
    "discovery-server\target\discovery-server.jar",
    "api-gateway\target\api-gateway.jar",
    "auth-service\target\auth-service.jar",
    "user-notification-service\target\user-notification-service.jar",
    "properties-service\target\properties-service.jar",
    "listing-booking-service\target\listing-booking-service.jar",
    "contract-tenant-service\target\contract-tenant-service.jar",
    "billing-utility-service\target\billing-utility-service.jar",
    "asset-maintenance-service\target\asset-maintenance-service.jar",
    "chat-service\target\chat-service.jar"
)
Write-Host ""
Write-Host "ℹ [BUILD BẮT BUỘC] Biên dịch lại 10 microservices từ source hiện tại..." -ForegroundColor Yellow
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnCmd) {
    Write-Host "  -> Đang dùng Maven trên máy (mvn clean package -DskipTests)..." -ForegroundColor Cyan
    Push-Location $backendDir
    try {
        & mvn clean package -DskipTests
    } finally {
        Pop-Location
    }
} else {
    Write-Host "  -> Máy chưa cài Maven, dùng Docker (maven:3.9-eclipse-temurin-21) để biên dịch..." -ForegroundColor Cyan
    docker run --rm -v "${backendDir}:/app" -w /app maven:3.9-eclipse-temurin-21 mvn clean package -DskipTests
}
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ [LỖI] Biên dịch backend thất bại (exit $LASTEXITCODE)." -ForegroundColor Red
    Write-Host "   👉 Chạy thủ công 'mvn clean package -DskipTests' trong $backendDir để xem log chi tiết." -ForegroundColor Yellow
    exit 1
}
$missingJars = $expectedJars | Where-Object { -not (Test-Path (Join-Path $backendDir $_)) }
if ($missingJars.Count -gt 0) {
    Write-Host ""
    Write-Host "❌ [LỖI] Sau khi build vẫn thiếu: $($missingJars -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host "  ✔ Biên dịch 10 microservices ra file .jar thành công!" -ForegroundColor Green

# Tự động chuẩn bị file .env cho Backend nếu chưa có
$beEnv = Join-Path $backendDir ".env"
$beEnvEx = Join-Path $backendDir ".env.example"
if (-not (Test-Path $beEnv)) {
    if (Test-Path $beEnvEx) {
        Write-Host "  ℹ Chưa tìm thấy file .env ở Backend -> Đang tự động tạo từ .env.example..." -ForegroundColor Yellow
        Copy-Item $beEnvEx $beEnv
        Write-Host "  ✔ Đã tự động tạo file $beEnv" -ForegroundColor Green
    } else {
        Write-Host "❌ [LỖI] Không tìm thấy file .env hoặc .env.example trong $backendDir!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "  ✔ File .env Backend: Đã có sẵn" -ForegroundColor Green
}

$licenseLine = Get-Content $beEnv | Where-Object { $_ -match '^\s*MINIO_LICENSE_TOKEN=' } | Select-Object -First 1
$licenseVal = if ($licenseLine) { ($licenseLine -split '=', 2)[1].Trim() } else { "" }
if ([string]::IsNullOrWhiteSpace($licenseVal)) {
    Write-Host ""
    Write-Host "❌ [THIẾU MINIO_LICENSE_TOKEN] File $beEnv chưa có giá trị MINIO_LICENSE_TOKEN!" -ForegroundColor Red
    Write-Host "   - Container MinIO AIStor bắt buộc cần MINIO_LICENSE_TOKEN để khởi động và lưu ảnh." -ForegroundColor Yellow
    Write-Host "   👉 Hãy copy file .env từ trưởng nhóm đè vào $beEnv, hoặc đăng ký token miễn phí tại subnet.min.io rồi điền vào dòng MINIO_LICENSE_TOKEN=..." -ForegroundColor Cyan
    exit 1
}

# Tự động chuẩn bị file .env cho Frontend nếu chưa có
if (Test-Path $frontendDir) {
    $feEnv = Join-Path $frontendDir ".env"
    $feEnvEx = Join-Path $frontendDir ".env.example"
    if (-not (Test-Path $feEnv)) {
        if (Test-Path $feEnvEx) {
            Copy-Item $feEnvEx $feEnv
            Write-Host "  ✔ Đã tự động tạo file .env Frontend từ .env.example" -ForegroundColor Green
        } else {
            Set-Content -Path $feEnv -Value "VITE_API_URL=http://localhost:8080`n" -Encoding UTF8
            Write-Host "  ✔ Đã tạo file .env Frontend (VITE_API_URL=http://localhost:8080)" -ForegroundColor Green
        }
    } else {
        Write-Host "  ✔ File .env Frontend: Đã có sẵn" -ForegroundColor Green
    }
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

# 1. Khoi dong Docker Backend
Write-Host ""
Write-Host "[BƯỚC 1/3] Khởi động các container Backend (PostgreSQL, Kafka, MinIO, Discovery, Microservices)..." -ForegroundColor Yellow
Write-Host "  -> Đang khởi động các container qua 'docker compose up -d --build'..." -ForegroundColor Yellow
Push-Location $backendDir
try {
    docker compose up -d --build
    if ($LASTEXITCODE -ne 0) { throw "docker compose up -d --build thất bại (exit $LASTEXITCODE)." }
} finally {
    Pop-Location
}
Write-Host "  -> Chờ PostgreSQL healthy..." -ForegroundColor Yellow
Wait-ContainerHealthy $postgresContainer
Write-Host "  -> PostgreSQL đã sẵn sàng." -ForegroundColor Green

# 2. Xoa sach DB + restart service + seed
Write-Host ""
Write-Host "[BƯỚC 2/3] Xóa sạch các database, dựng lại schema và nạp dữ liệu kiểm thử..." -ForegroundColor Yellow

# Ưu tiên bản seed nằm trong rental-system-backend: bản ở thư mục gốc repo là bản
# sao cũ, dễ khiến script chạy nhầm dữ liệu lỗi thời.
$seedScript = if (Test-Path (Join-Path $backendDir "seed\reset-and-seed.ps1")) {
    Join-Path $backendDir "seed\reset-and-seed.ps1"
} elseif (Test-Path (Join-Path $baseDir "seed\reset-and-seed.ps1")) {
    Join-Path $baseDir "seed\reset-and-seed.ps1"
} else {
    throw "Không tìm thấy file reset-and-seed.ps1"
}

if ($SkipSeed) {
    & $seedScript -SkipSeed
} else {
    & $seedScript
}
if ($LASTEXITCODE -ne 0) {
    throw "Reset & seed thất bại (exit $LASTEXITCODE)."
}

# 3. Ket qua & Huong dan Frontend
Write-Host ""
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "   HE THONG BACKEND DA DUOC KHOI DONG VA SEED DATA TEST THANH CONG!   " -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  📌 TÀI KHOẢN ĐĂNG NHẬP KIỂM THỬ (Mật khẩu: Test@12345):" -ForegroundColor Cyan
Write-Host "     - Admin Bootstrap : e2e.admin@example.com / E2ePass123!" -ForegroundColor White
Write-Host "     - Admin Seed      : seed.admin@test.local / Test@12345" -ForegroundColor White
Write-Host "     - Quản lý Tòa A/B : seed.manager.a@test.local | seed.manager.b@test.local" -ForegroundColor White
Write-Host "     - Sale / Kỹ thuật : seed.sale.a@test.local | seed.tech.a@test.local" -ForegroundColor White
Write-Host "     - Khách đang thuê : seed.active@test.local (hợp đồng & hóa đơn mẫu)" -ForegroundColor White
Write-Host "     - Khách tìm phòng : seed.new@test.local | seed.appt@test.local" -ForegroundColor White
Write-Host ""
Write-Host "  🚀 BƯỚC TIẾP THEO: KHỞI ĐỘNG FRONTEND" -ForegroundColor Cyan
Write-Host "     1. Mở một cửa sổ Terminal/PowerShell mới." -ForegroundColor White
Write-Host "     2. Gõ lệnh: cd rental-system-frontend" -ForegroundColor White
Write-Host "     3. Gõ lệnh: npm install" -ForegroundColor White
Write-Host "     4. Gõ lệnh: npm run dev" -ForegroundColor White
Write-Host "     5. Truy cập trình duyệt tại: http://localhost:8443" -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
