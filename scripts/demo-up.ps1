param(
    [switch]$Down,
    [switch]$Build,
    [switch]$Agent
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$override = Join-Path $root '.demo-override.yml'

function Invoke-Docker {
    param([string[]]$DockerArgs)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & docker @DockerArgs
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previous
    return $code
}

function Enable-MinioOverride {
    if (-not (Test-Path $override)) {
        @'
services:
  minio:
    ports: !override
      - "127.0.0.1:9100:9000"
      - "127.0.0.1:9101:9001"
  backend:
    environment:
      MINIO_PUBLIC_ENDPOINT: http://localhost:9100
'@ | Set-Content -Path $override -Encoding ASCII
    }
    $script:files = @('-f', 'compose.yml', '-f', $override)
}

function Test-Port9000Busy {
    try { return [bool](Get-NetTCPConnection -LocalPort 9000 -State Listen -ErrorAction SilentlyContinue) }
    catch { return $false }
}

$files = @('-f', 'compose.yml')
if (Test-Path $override) { $files += @('-f', $override) }

if ($Down) {
    exit (Invoke-Docker (@('compose') + $files + @('down')))
}

if (-not (Test-Path (Join-Path $root '.env'))) {
    Copy-Item (Join-Path $root '.env.example') (Join-Path $root '.env')
    Write-Host 'Created .env from .env.example. Review the secrets, then run this script again.'
}

if (Test-Port9000Busy) {
    Enable-MinioOverride
    Write-Host 'Port 9000 is busy (usually WSL); MinIO will be published on 9100/9101.'
}

$composeArgs = @('compose') + $files
if ($Agent) { $composeArgs += @('--profile', 'agent') }
$composeArgs += @('up', '-d')
if ($Build) { $composeArgs += '--build' }
$code = Invoke-Docker $composeArgs

if ($code -ne 0 -and -not (Test-Path $override)) {
    Write-Host 'Compose failed, retrying with MinIO on 9100/9101...'
    Enable-MinioOverride
    $composeArgs = @('compose') + $files
    if ($Agent) { $composeArgs += @('--profile', 'agent') }
    $composeArgs += @('up', '-d')
    if ($Build) { $composeArgs += '--build' }
    $code = Invoke-Docker $composeArgs
}

if ($code -ne 0) { exit $code }

Write-Host ''
Write-Host 'ProcureMind is starting:'
Write-Host '  Web      http://localhost:3000'
Write-Host '  Swagger  http://localhost:8080/swagger-ui.html'
Write-Host '  Health   http://localhost:8080/actuator/health'
if ($files -contains $override) {
    Write-Host '  MinIO    http://localhost:9101'
} else {
    Write-Host '  MinIO    http://localhost:9001'
}
if ($Agent) {
    Write-Host '  Agent    http://localhost:8000/health'
    Write-Host '  MCP      http://localhost:8001/sse'
    Write-Host '  Qdrant   http://localhost:6333/dashboard'
}
Write-Host ''
Write-Host 'Stop with: .\scripts\demo-up.ps1 -Down'
