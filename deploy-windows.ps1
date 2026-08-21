param(
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")]
    [string]$ImageTag = "latest"
)

$ErrorActionPreference = "Stop"

$projectName = "android-local-first-task-todo-doing"
$composeFile = Join-Path $PSScriptRoot "docker-compose.release.yml"
$envFile = "C:\Deploy\ToDo\.env"
$versionFile = "C:\Deploy\ToDo\deployed-version.txt"

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Deployment environment file not found: $envFile"
}

$backendPort = 8080
$portLine = Get-Content -LiteralPath $envFile |
    Where-Object { $_ -match "^\s*BACKEND_PORT=(\d+)\s*$" } |
    Select-Object -First 1

if ($portLine -match "^\s*BACKEND_PORT=(\d+)\s*$") {
    $backendPort = [int]$Matches[1]
}

$previousImageTag = $env:BACKEND_IMAGE_TAG
$env:BACKEND_IMAGE_TAG = $ImageTag

try {
    Write-Host "1. Pull backend image tag: $ImageTag"

    & docker compose `
        -p $projectName `
        --env-file $envFile `
        -f $composeFile `
        pull backend

    if ($LASTEXITCODE -ne 0) {
        throw "docker compose pull failed with exit code $LASTEXITCODE"
    }

    Write-Host "2. Update containers and wait for health checks"

    & docker compose `
        -p $projectName `
        --env-file $envFile `
        -f $composeFile `
        up -d --wait --wait-timeout 120

    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed with exit code $LASTEXITCODE"
    }

    Write-Host "3. Verify Backend health"

    $healthUri = "http://localhost:$backendPort/actuator/health"
    $health = Invoke-RestMethod -Uri $healthUri -TimeoutSec 10

    if ($health.status -ne "UP") {
        throw "Backend health check returned: $($health.status)"
    }

    Set-Content `
        -LiteralPath $versionFile `
        -Value $ImageTag `
        -Encoding UTF8

    Write-Host "Deployment succeeded: $ImageTag"
}
finally {
    if ($null -eq $previousImageTag) {
        Remove-Item Env:BACKEND_IMAGE_TAG -ErrorAction SilentlyContinue
    }
    else {
        $env:BACKEND_IMAGE_TAG = $previousImageTag
    }
}