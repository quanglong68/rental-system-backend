param(
    [switch]$SkipSeed
)

# Set UTF-8 encoding for console output
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$parentDir = Split-Path -Parent $scriptDir
if (Test-Path (Join-Path $parentDir "compose.yaml")) {
    $backendDir = $parentDir
} elseif (Test-Path (Join-Path $parentDir "rental-system-backend")) {
    $backendDir = Join-Path $parentDir "rental-system-backend"
} else {
    $backendDir = $parentDir
}
$envFile = Join-Path $backendDir ".env"
$postgresContainer = "rental-postgres"

$serviceContainers = @(
    "rental-auth",
    "rental-user-notification",
    "rental-properties",
    "rental-listing-booking",
    "rental-contract-tenant",
    "rental-billing-utility",
    "rental-asset-maintenance",
    "rental-chat"
)

function Write-Step($message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Yellow
}

function Read-EnvFile($path) {
    $vars = @{}
    if (-not (Test-Path $path)) { return $vars }
    foreach ($line in Get-Content $path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
        $idx = $trimmed.IndexOf("=")
        if ($idx -lt 1) { continue }
        $key = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1).Trim()
        if ($value.Length -ge 2) {
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        $vars[$key] = $value
    }
    return $vars
}

function Assert-SafeIdentifier($value, $label) {
    if ($value -notmatch '^[A-Za-z0-9_]+$') {
        throw "$label không hợp lệ (chỉ cho phép chữ, số, dấu gạch dưới): $value"
    }
}

function Invoke-Psql($database, $sql) {
    $sql | docker exec -i $postgresContainer psql -v ON_ERROR_STOP=1 -U postgres -d $database
    if ($LASTEXITCODE -ne 0) {
        throw "psql thất bại trên database $database (exit $LASTEXITCODE)."
    }
}

function Wait-ContainerHealthy($name, [int]$timeoutSeconds = 300) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $running = docker inspect -f "{{.State.Running}}" $name 2>$null
        if ($running -ne "true") { Start-Sleep -Seconds 3; continue }
        $health = docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" $name 2>$null
        if ($health -eq "healthy" -or $health -eq "none") { return }
        Start-Sleep -Seconds 3
    }
    $last = docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" $name 2>$null
    throw "Container $name không healthy sau $timeoutSeconds giây (trạng thái: $last)."
}

function Wait-HttpReady($url, [int]$timeoutSeconds = 300) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 | Out-Null
            return
        } catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -lt 500) { return }
            Start-Sleep -Seconds 3
        }
    }
    throw "Không kết nối được $url sau $timeoutSeconds giây."
}

function Import-LocationData {
    $dataDir = Join-Path $backendDir "properties-service\src\main\resources\db\migration\data"
    $provinceCsv = Join-Path $dataDir "province-v2.csv"
    $wardCsv = Join-Path $dataDir "ward-v2.csv"
    if (-not (Test-Path $provinceCsv) -or -not (Test-Path $wardCsv)) {
        throw "Không tìm thấy dữ liệu tỉnh/phường tại $dataDir."
    }

    docker cp $provinceCsv "${postgresContainer}:/tmp/seed-province.csv" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Không copy được province-v2.csv vào postgres." }
    docker cp $wardCsv "${postgresContainer}:/tmp/seed-ward.csv" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Không copy được ward-v2.csv vào postgres." }

    $sql = @'
BEGIN;
CREATE TEMP TABLE seed_province (
    id text, code text, name text, codename text,
    division_type text, created_at text, updated_at text
) ON COMMIT DROP;
\copy seed_province FROM '/tmp/seed-province.csv' WITH (FORMAT csv, HEADER true)

CREATE TEMP TABLE seed_ward (
    ward_id text, province_id text, code text, name text, codename text,
    division_type text, created_at text, updated_at text
) ON COMMIT DROP;
\copy seed_ward FROM '/tmp/seed-ward.csv' WITH (FORMAT csv, HEADER true)

INSERT INTO properties_service.province (province_id, code, name, division_type)
SELECT row_number() OVER (ORDER BY code), code, name, division_type
  FROM seed_province;

INSERT INTO properties_service.ward (ward_id, province_id, code, name, division_type)
SELECT 100000 + row_number() OVER (ORDER BY w.code), p.province_id, w.code, w.name, w.division_type
  FROM seed_ward w
  JOIN seed_province sp ON sp.id = w.province_id
  JOIN properties_service.province p ON p.code = sp.code;

COMMIT;
'@
    Invoke-Psql "property_db" $sql | Out-Null
    Write-Host "  -> Đã nạp danh mục tỉnh/phường." -ForegroundColor Green
}

function Ensure-BootstrapAdmin {    $base = "http://localhost:8080"
    $username = "e2e.admin@example.com"
    $password = "E2ePass123!"

    $registerBody = @{
        username = $username
        password = $password
        fullName = "E2E Bootstrap Admin"
        email    = $username
        phone    = "0900000000"
    } | ConvertTo-Json

    $registered = $false
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        try {
            Invoke-RestMethod -Uri "$base/api/v1/auth/register" -Method Post -Body $registerBody `
                -ContentType "application/json; charset=utf-8" -TimeoutSec 20 | Out-Null
            $registered = $true
            break
        } catch {
            $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            if ($status -eq 400 -or $status -eq 409) {
                # Tài khoản đã tồn tại (chạy lại khi chưa xóa DB) -> tiếp tục promote.
                $registered = $true
                break
            }
            if ($attempt -eq 10) { throw "Không đăng ký được bootstrap admin: $($_.Exception.Message)" }
            Start-Sleep -Seconds 3
        }
    }
    if (-not $registered) { throw "Không tạo được tài khoản bootstrap admin." }

    $promoteSql = @"
UPDATE account
   SET account_type = 'ADMIN',
       is_active = TRUE,
       failed_login_count = 0,
       locked_until = NULL,
       updated_at = now()
 WHERE username = '$username';

INSERT INTO account_role (account_id, role_id)
SELECT a.id, r.id
  FROM account a
  JOIN role r ON r.code = 'ADMIN'
 WHERE a.username = '$username'
ON CONFLICT DO NOTHING;

DELETE FROM account_role ar
 USING account a, role r
 WHERE ar.account_id = a.id
   AND ar.role_id = r.id
   AND a.username = '$username'
   AND r.code = 'CUSTOMER';
"@
    Invoke-Psql "auth_db" $promoteSql | Out-Null
    Write-Host "  -> Bootstrap admin đã sẵn sàng ($username)." -ForegroundColor Green
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  RENTAL SYSTEM - XOA SACH 8 DATABASE VA SEED LAI DATA   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 0. Tiền điều kiện
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Không tìm thấy lệnh docker trong PATH."
}
$pgRunning = docker inspect -f "{{.State.Running}}" $postgresContainer 2>$null
if ($pgRunning -ne "true") {
    throw "Container $postgresContainer chưa chạy. Hãy chạy start-fresh-system.bat hoặc 'docker compose up -d' trong rental-system-backend trước."
}

$envVars = Read-EnvFile $envFile
if (-not $envVars.ContainsKey("POSTGRES_USER") -or [string]::IsNullOrWhiteSpace($envVars["POSTGRES_USER"])) {
    $envVars["POSTGRES_USER"] = "postgres"
}

$databases = @("auth_db", "user_db", "property_db", "listing_db", "contract_db", "billing_db", "asset_db", "chat_db")
if ($envVars.ContainsKey("POSTGRES_MULTIPLE_DATABASES") -and $envVars["POSTGRES_MULTIPLE_DATABASES"]) {
    $databases = $envVars["POSTGRES_MULTIPLE_DATABASES"].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }
}

Write-Step "[1/7] Xóa và tạo lại $($databases.Count) database (Flyway sẽ dựng schema mới khi service khởi động)"
foreach ($db in $databases) {
    Assert-SafeIdentifier $db "Tên database"
    $prefix = ($db -replace "_db$", "").ToUpper()
    $userVar = "${prefix}_DB_USER"
    $dbUser = if ($envVars.ContainsKey($userVar) -and $envVars[$userVar]) { $envVars[$userVar] } else { $envVars["POSTGRES_USER"] }
    Assert-SafeIdentifier $dbUser "Tên user của $db"

    Write-Host "  - $db (owner: $dbUser)" -ForegroundColor DarkGray
    $dropSql = @"
SELECT pg_terminate_backend(pid)
  FROM pg_stat_activity
 WHERE datname = '$db' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS "$db" WITH (FORCE);
CREATE DATABASE "$db" OWNER "$dbUser";
GRANT ALL PRIVILEGES ON DATABASE "$db" TO "$dbUser";
"@
    Invoke-Psql "postgres" $dropSql | Out-Null

    if ($db -eq "property_db") {
        # Migration V1/V2 dùng CREATE EXTENSION ... IF NOT EXISTS; extension phải có sẵn
        # vì user nghiệp vụ không phải superuser.
        Invoke-Psql $db "CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public; CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;" | Out-Null
    }
    Invoke-Psql $db "ALTER SCHEMA public OWNER TO `"$dbUser`"; GRANT ALL ON SCHEMA public TO `"$dbUser`";" | Out-Null
}

Write-Step "[2/7] Khởi động lại các service nghiệp vụ để Flyway tạo schema và seed role"
foreach ($container in $serviceContainers) {
    $exists = docker inspect -f "{{.Name}}" $container 2>$null
    if ($LASTEXITCODE -eq 0 -and $exists) {
        docker restart $container | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Không restart được container $container." }
        Write-Host "  - restart $container" -ForegroundColor DarkGray
    } else {
        Write-Host "  - bỏ qua $container (chưa được tạo trong compose)" -ForegroundColor DarkYellow
    }
}

Write-Step "[3/7] Chờ service healthy, MinIO sẵn sàng và Gateway nhận đủ route Eureka"
$minioRunning = docker inspect -f "{{.State.Running}}" "rental-minio" 2>$null
if ($minioRunning -ne "true") {
    throw "Container rental-minio chưa chạy. Kiểm tra MINIO_LICENSE_TOKEN trong .env và log rental-minio."
}
$minioInitExit = docker inspect -f "{{.State.ExitCode}}" "rental-minio-init" 2>$null
$minioInitStatus = docker inspect -f "{{.State.Status}}" "rental-minio-init" 2>$null
if ($minioInitStatus -ne "exited" -or $minioInitExit -ne "0") {
    Write-Host "  - Chạy lại rental-minio-init để đảm bảo tạo đủ bucket MinIO và quyền download..." -ForegroundColor Yellow
    docker start -a rental-minio-init | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "rental-minio-init thất bại (exit $LASTEXITCODE)." }
}
Write-Host "  - rental-minio & bucket property-images sẵn sàng" -ForegroundColor Green

foreach ($container in $serviceContainers) {
    $exists = docker inspect -f "{{.Name}}" $container 2>$null
    if ($LASTEXITCODE -eq 0 -and $exists) {
        Wait-ContainerHealthy $container
        Write-Host "  - $container healthy" -ForegroundColor Green
    }
}
Wait-HttpReady "http://localhost:8080/api/v1/auth/login"
Wait-HttpReady "http://localhost:8080/api/v1/buildings/nearby?latitude=10.8&longitude=106.7"
Wait-HttpReady "http://localhost:8080/api/v1/listings"
Write-Host "  - API Gateway đã nhận đủ route Auth, Property và Listing" -ForegroundColor Green

Write-Step "[4/7] Nạp danh mục tỉnh/phường và tạo tài khoản bootstrap admin"
Import-LocationData
Ensure-BootstrapAdmin

if ($SkipSeed) {
    Write-Host ""
    Write-Host "Đã bỏ qua bước seed theo yêu cầu (-SkipSeed)." -ForegroundColor Yellow
    return
}

Write-Step "[5/7] Chạy seed dữ liệu kiểm thử"
$stateFile = Join-Path $scriptDir "state.json"
$reportFile = Join-Path $scriptDir "seed-report.json"
if (Test-Path $stateFile) { Remove-Item $stateFile -Force }
if (Test-Path $reportFile) { Remove-Item $reportFile -Force }

$nodeExe = if (Get-Command node -ErrorAction SilentlyContinue) { "node" } elseif (Test-Path "C:\Program Files\nodejs\node.exe") { "C:\Program Files\nodejs\node.exe" } else { throw "Không tìm thấy Node.js." }
$seedMjs = Join-Path $scriptDir "manual-test-seed.mjs"
$env:SEED_BASE_URL = "http://localhost:8080"
& $nodeExe $seedMjs
if ($LASTEXITCODE -ne 0) {
    throw "Seed dữ liệu thất bại (exit $LASTEXITCODE). Xem log phía trên."
}

Write-Step "[6/7] Kiểm tra lại dữ liệu seed (verify-seed)"
$verifyMjs = Join-Path $scriptDir "verify-seed.mjs"
& $nodeExe $verifyMjs
if ($LASTEXITCODE -ne 0) {
    throw "Kiểm tra dữ liệu seed thất bại (exit $LASTEXITCODE). Xem chi tiết trong seed/verify-report.json."
}

Write-Step "[7/7] Upload ảnh phòng và tòa nhà từ thư mục pictures"
$imageMjs = Join-Path $scriptDir "upload-missing-images.mjs"
& $nodeExe $imageMjs
if ($LASTEXITCODE -ne 0) {
    throw "Upload ảnh phòng/tòa nhà thất bại (exit $LASTEXITCODE). Kiểm tra thư mục pictures và log phía trên."
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  RESET 8 DATABASE + SEED HOAN TAT                       " -ForegroundColor Green
Write-Host "  Bootstrap admin : e2e.admin@example.com / E2ePass123!  " -ForegroundColor Green
Write-Host "  Tài khoản seed  : seed.*@test.local / Test@12345       " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
