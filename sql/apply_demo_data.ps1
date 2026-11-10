param(
    [string]$Container = 'procuremind-mysql-1',
    [string]$Database = 'procuremind',
    [string]$RootPassword = 'root123456'
)

$ErrorActionPreference = 'Stop'
$sqlFile = Join-Path $PSScriptRoot 'demo_business_data.sql'
if (-not (Test-Path $sqlFile)) { throw "SQL file not found: $sqlFile" }

Write-Host "Applying demo business data: $sqlFile"
$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
Get-Content $sqlFile -Raw -Encoding UTF8 |
    docker exec -i $Container mysql -uroot "-p$RootPassword" --default-character-set=utf8mb4 $Database
$code = $LASTEXITCODE
$ErrorActionPreference = $previous
if ($code -ne 0) { throw "demo SQL failed with exit code $code" }

Write-Host 'Demo business data applied.'
