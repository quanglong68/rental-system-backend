$ErrorActionPreference = 'Stop'

$serviceRoot = Split-Path $PSScriptRoot -Parent
$repositoryRoot = Split-Path $serviceRoot -Parent
$containerName = "listing-service-test-postgres-$PID"
$image = if ($env:LISTING_TEST_POSTGRES_IMAGE) { $env:LISTING_TEST_POSTGRES_IMAGE } else { 'postgis/postgis:16-3.4' }
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()

$oldPassword = $env:LISTING_DB_PASSWORD
$oldPostgresPassword = $env:POSTGRES_PASSWORD
$oldUrl = $env:LISTING_TEST_DB_URL
$oldUser = $env:LISTING_TEST_DB_USER
$bytes = [byte[]]::new(24)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:POSTGRES_PASSWORD = [Convert]::ToHexString($bytes).ToLowerInvariant()
$env:LISTING_DB_PASSWORD = $env:POSTGRES_PASSWORD
$env:LISTING_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$port/listing_test_db"
$env:LISTING_TEST_DB_USER = 'listing_test_svc'

try {
    docker run --detach --rm --name $containerName `
        --publish "127.0.0.1:${port}:5432" `
        --env POSTGRES_DB=listing_test_db `
        --env POSTGRES_USER=listing_test_svc `
        --env POSTGRES_PASSWORD `
        $image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the isolated PostgreSQL test container.' }

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $containerName pg_isready -U listing_test_svc -d listing_test_db *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Isolated PostgreSQL did not become ready within 60 seconds.' }

    Push-Location $repositoryRoot
    try {
        mvn -o -f pom.xml -pl listing-service -am test
        if ($LASTEXITCODE -ne 0) { throw "Maven tests failed with exit code $LASTEXITCODE." }
    }
    finally {
        Pop-Location
    }
}
finally {
    docker rm --force $containerName *> $null
    if ($null -eq $oldPassword) { Remove-Item Env:LISTING_DB_PASSWORD -ErrorAction SilentlyContinue } else { $env:LISTING_DB_PASSWORD = $oldPassword }
    if ($null -eq $oldUrl) { Remove-Item Env:LISTING_TEST_DB_URL -ErrorAction SilentlyContinue } else { $env:LISTING_TEST_DB_URL = $oldUrl }
    if ($null -eq $oldUser) { Remove-Item Env:LISTING_TEST_DB_USER -ErrorAction SilentlyContinue } else { $env:LISTING_TEST_DB_USER = $oldUser }
    if ($null -eq $oldPostgresPassword) { Remove-Item Env:POSTGRES_PASSWORD -ErrorAction SilentlyContinue } else { $env:POSTGRES_PASSWORD = $oldPostgresPassword }
}
