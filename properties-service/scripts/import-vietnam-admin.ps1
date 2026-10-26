param(
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$dataDirectory = Join-Path $PSScriptRoot '..\src\main\resources\db\migration\data'
$provincePath = Join-Path $dataDirectory 'province-v2.csv'
$wardPath = Join-Path $dataDirectory 'ward-v2.csv'
$manifestPath = Join-Path $dataDirectory 'vietnam-admin-v2-manifest.json'

foreach ($path in @($provincePath, $wardPath, $manifestPath)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required input file not found: $path" }
}

$provinces = @(Import-Csv -LiteralPath $provincePath -Encoding utf8)
$wards = @(Import-Csv -LiteralPath $wardPath -Encoding utf8)
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json

function Assert-Headers($rows, [string[]]$required, [string]$label) {
    if ($rows.Count -eq 0) { throw "$label CSV contains no records." }
    $headers = @($rows[0].PSObject.Properties.Name)
    foreach ($column in $required) {
        if ($column -notin $headers) { throw "$label CSV is missing required column '$column'." }
    }
}

Assert-Headers $provinces @('id', 'code', 'name', 'division_type') 'Province'
Assert-Headers $wards @('ward_id', 'province_id', 'code', 'name', 'division_type') 'Ward'

if ($provinces.Count -ne $manifest.province_count -or $wards.Count -ne $manifest.ward_count) {
    throw "CSV counts do not match manifest (provinces=$($provinces.Count)/$($manifest.province_count), wards=$($wards.Count)/$($manifest.ward_count))."
}

$duplicateProvinceCodes = @($provinces | Group-Object code | Where-Object Count -gt 1)
$duplicateWardCodes = @($wards | Group-Object code | Where-Object Count -gt 1)
$provinceIds = @{}
foreach ($province in $provinces) {
    if ([string]::IsNullOrWhiteSpace($province.id) -or [string]::IsNullOrWhiteSpace($province.code) -or [string]::IsNullOrWhiteSpace($province.name)) {
        throw 'Province CSV contains a blank required id, code, or name.'
    }
    if (-not [guid]::TryParse($province.id, [ref]([guid]::Empty))) {
        throw "Province source id is not a UUID: $($province.id)"
    }
    $provinceIds[$province.id] = $true
}
$missingParents = @($wards | Where-Object { -not $provinceIds.ContainsKey($_.province_id) })
foreach ($ward in $wards) {
    if ([string]::IsNullOrWhiteSpace($ward.ward_id) -or [string]::IsNullOrWhiteSpace($ward.province_id) -or [string]::IsNullOrWhiteSpace($ward.code) -or [string]::IsNullOrWhiteSpace($ward.name)) {
        throw 'Ward CSV contains a blank required ward_id, province_id, code, or name.'
    }
    if (-not [guid]::TryParse($ward.ward_id, [ref]([guid]::Empty))) {
        throw "Ward source id is not a UUID: $($ward.ward_id)"
    }
}

if ($duplicateProvinceCodes.Count -or $duplicateWardCodes.Count -or $missingParents.Count) {
    throw "Input validation failed: duplicate province codes=$($duplicateProvinceCodes.Count), duplicate ward codes=$($duplicateWardCodes.Count), wards with unresolved province=$($missingParents.Count)."
}

Write-Output "Validated source counts: provinces=$($provinces.Count), wards=$($wards.Count); duplicate codes=0; unresolved province links=0."

if (-not $Apply) {
    Write-Output 'Dry run only. Provinces and wards are eligible for idempotent import by code; existing names, division types, and ward-to-province relationships will be compared and conflicts will not be overwritten.'
    Write-Output 'The database must have the post-V6 schema: ward.province_id exists and ward.district_id has been removed.'
    Write-Output 'Run this script with -Apply after backing up the database to import both datasets in one transaction.'
    return
}

$dbUser = if ($env:DB_USER) { $env:DB_USER } else { 'postgres' }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { 'RoomRental' }
$schemaName = if ($env:SCHEMA_NAME) { $env:SCHEMA_NAME } else { 'properties_service' }
if ($schemaName -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { throw 'SCHEMA_NAME must be a simple PostgreSQL identifier.' }

function Quote-SqlLiteral([string]$value) {
    if ($null -eq $value) { return 'NULL' }
    return "'" + $value.Replace("'", "''") + "'"
}

$provinceValues = foreach ($province in $provinces) {
    $divisionType = if ([string]::IsNullOrWhiteSpace($province.division_type)) { 'NULL' } else { Quote-SqlLiteral $province.division_type }
    '(' + (Quote-SqlLiteral $province.id) + '::UUID, ' + (Quote-SqlLiteral $province.code) + ', ' + (Quote-SqlLiteral $province.name) + ', ' + $divisionType + ')'
}
$wardValues = foreach ($ward in $wards) {
    $divisionType = if ([string]::IsNullOrWhiteSpace($ward.division_type)) { 'NULL' } else { Quote-SqlLiteral $ward.division_type }
    '(' + (Quote-SqlLiteral $ward.ward_id) + '::UUID, ' + (Quote-SqlLiteral $ward.province_id) + '::UUID, ' + (Quote-SqlLiteral $ward.code) + ', ' + (Quote-SqlLiteral $ward.name) + ', ' + $divisionType + ')'
}

$schema = '"' + $schemaName + '"'
$sql = @"
BEGIN;
CREATE TEMP TABLE import_province (source_province_id UUID PRIMARY KEY, code VARCHAR(20) UNIQUE NOT NULL, name VARCHAR(150) NOT NULL, division_type VARCHAR(100)) ON COMMIT DROP;
INSERT INTO import_province (source_province_id, code, name, division_type) VALUES
$($provinceValues -join ",`n");
CREATE TEMP TABLE import_ward (source_ward_id UUID PRIMARY KEY, source_province_id UUID NOT NULL, code VARCHAR(20) UNIQUE NOT NULL, name VARCHAR(150) NOT NULL, division_type VARCHAR(100)) ON COMMIT DROP;
INSERT INTO import_ward (source_ward_id, source_province_id, code, name, division_type) VALUES
$($wardValues -join ",`n");
DO `$`$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = '$schemaName' AND table_name = 'ward' AND column_name = 'province_id'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = '$schemaName' AND table_name = 'ward' AND column_name = 'district_id'
    ) THEN
        RAISE EXCEPTION 'Ward schema is not migrated: expected province_id and no district_id. Apply V6 before importing.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM $schema.province current_row
        JOIN import_province incoming ON current_row.code = incoming.code
        WHERE current_row.name IS DISTINCT FROM incoming.name
           OR current_row.division_type IS DISTINCT FROM incoming.division_type
    ) THEN
        RAISE EXCEPTION 'Province import conflicts with existing rows; no data was changed.';
    END IF;
END
`$`$;

INSERT INTO $schema.province (code, name, division_type)
SELECT incoming.code, incoming.name, incoming.division_type
FROM import_province incoming
ON CONFLICT (code) DO NOTHING;

DO `$`$
BEGIN
    IF EXISTS (
        SELECT 1 FROM $schema.ward current_row
        JOIN import_ward incoming ON current_row.code = incoming.code
        JOIN import_province source_province ON source_province.source_province_id = incoming.source_province_id
        JOIN $schema.province parent_province ON parent_province.code = source_province.code
        WHERE current_row.name IS DISTINCT FROM incoming.name
           OR current_row.division_type IS DISTINCT FROM incoming.division_type
           OR current_row.province_id IS DISTINCT FROM parent_province.province_id
    ) THEN
        RAISE EXCEPTION 'Ward import conflicts with existing rows; no data was changed.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_ward incoming
        JOIN import_province source_province ON source_province.source_province_id = incoming.source_province_id
        LEFT JOIN $schema.province parent_province ON parent_province.code = source_province.code
        WHERE parent_province.province_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Some source wards do not have a matching province row; no data was changed.';
    END IF;
END
`$`$;

WITH inserted AS (
    INSERT INTO $schema.ward (code, name, division_type, province_id)
    SELECT incoming.code, incoming.name, incoming.division_type, parent_province.province_id
    FROM import_ward incoming
    JOIN import_province source_province ON source_province.source_province_id = incoming.source_province_id
    JOIN $schema.province parent_province ON parent_province.code = source_province.code
    ON CONFLICT (code) DO NOTHING
    RETURNING code
)
SELECT (SELECT count(*) FROM inserted) AS inserted,
       (SELECT count(*) FROM import_ward incoming JOIN $schema.ward current_row ON current_row.code = incoming.code) - (SELECT count(*) FROM inserted) AS already_present;
COMMIT;
"@

$tempSql = Join-Path ([System.IO.Path]::GetTempPath()) ("properties-admin-import-" + [guid]::NewGuid().ToString('N') + '.sql')
$containerSql = '/tmp/' + [System.IO.Path]::GetFileName($tempSql)
try {
    [System.IO.File]::WriteAllText($tempSql, $sql, [System.Text.UTF8Encoding]::new($false))
    & docker cp $tempSql "properties-postgres:$containerSql"
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy import SQL to properties-postgres.' }
    & docker exec properties-postgres psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f $containerSql
    if ($LASTEXITCODE -ne 0) { throw 'Administrative import failed; PostgreSQL transaction was rolled back.' }
}
finally {
    Remove-Item -LiteralPath $tempSql -Force -ErrorAction SilentlyContinue
    & docker exec properties-postgres rm -f $containerSql 2>$null | Out-Null
}
