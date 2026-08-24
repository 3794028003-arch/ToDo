param(
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")]
    [string]$ImageTag = "latest"
)

$ErrorActionPreference = "Stop"

$projectName = "android-local-first-task-todo-doing"
$composeFile = Join-Path $PSScriptRoot "docker-compose.release.yml"
$envFile = "C:\Deploy\ToDo\.env"
$versionFile = "C:\Deploy\ToDo\deployed-version.txt"
$previousVersionFile = "C:\Deploy\ToDo\previous-deployed-version.txt"
$imageRepository = "ghcr.io/3794028003-arch/todo-backend"
$rollbackTag = "rollback-local"

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

$previousDeployedVersion = $null
if (Test-Path -LiteralPath $versionFile) {
    $previousDeployedVersion = (Get-Content -LiteralPath $versionFile -Raw).Trim()
}

function Invoke-ComposeCommand {
    param(
        [Parameter(Mandatory)]
        [string[]]$CommandArguments
    )

    & docker compose `
        -p $projectName `
        --env-file $envFile `
        -f $composeFile `
        @CommandArguments

    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($CommandArguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Assert-BackendHealthy {
    $healthUri = "http://localhost:$backendPort/actuator/health"
    $health = Invoke-RestMethod -Uri $healthUri -TimeoutSec 10

    if ($health.status -ne "UP") {
        throw "Backend health check returned: $($health.status)"
    }
}

$rollbackAvailable = $false
$backendContainerIds = & docker compose `
        -p $projectName `
        --env-file $envFile `
        -f $composeFile `
        ps -q backend
$composePsExitCode = $LASTEXITCODE

if ($composePsExitCode -ne 0) {
    throw "Unable to inspect the currently deployed backend container."
}

$backendContainerId = $backendContainerIds | Select-Object -First 1

if ($backendContainerId) {
    $backendContainerId = $backendContainerId.Trim()
    $backendImageIds = & docker inspect --format "{{.Image}}" $backendContainerId
    $dockerInspectExitCode = $LASTEXITCODE
    $backendImageId = $backendImageIds | Select-Object -First 1

    if ($dockerInspectExitCode -ne 0 -or -not $backendImageId) {
        throw "Unable to inspect the currently deployed backend image."
    }

    $backendImageId = $backendImageId.Trim()
    & docker image tag $backendImageId "${imageRepository}:$rollbackTag"
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to preserve the currently deployed backend image for rollback."
    }

    $rollbackAvailable = $true
    Write-Host "Saved the current backend image as ${imageRepository}:$rollbackTag"
}

$previousImageTag = $env:BACKEND_IMAGE_TAG
$env:BACKEND_IMAGE_TAG = $ImageTag

try {
    Write-Host "1. Pull backend image tag: $ImageTag"
    Invoke-ComposeCommand -CommandArguments @("pull", "backend")

    Write-Host "2. Update containers and wait for health checks"
    Invoke-ComposeCommand -CommandArguments @(
        "up",
        "-d",
        "--wait",
        "--wait-timeout",
        "120"
    )

    Write-Host "3. Verify Backend health"
    Assert-BackendHealthy

    if ($previousDeployedVersion) {
        Set-Content `
            -LiteralPath $previousVersionFile `
            -Value $previousDeployedVersion `
            -Encoding UTF8
    }

    Set-Content `
        -LiteralPath $versionFile `
        -Value $ImageTag `
        -Encoding UTF8

    Write-Host "Deployment succeeded: $ImageTag"
}
catch {
    $deploymentError = $_

    if (-not $rollbackAvailable) {
        throw
    }

    Write-Warning "Deployment failed. Restoring the previously running backend image."
    $env:BACKEND_IMAGE_TAG = $rollbackTag

    try {
        Invoke-ComposeCommand -CommandArguments @(
            "up",
            "-d",
            "--pull",
            "never",
            "--wait",
            "--wait-timeout",
            "120"
        )
        Assert-BackendHealthy

        $rollbackVersion = if ($previousDeployedVersion) {
            $previousDeployedVersion
        }
        else {
            "previous-local-image"
        }

        Write-Host "Automatic rollback succeeded: $rollbackVersion"
    }
    catch {
        $rollbackError = $_
        throw "Deployment failed: $deploymentError Automatic rollback also failed: $rollbackError"
    }

    throw "Deployment of '$ImageTag' failed. Automatic rollback succeeded. Original error: $deploymentError"
}
finally {
    if ($null -eq $previousImageTag) {
        Remove-Item Env:BACKEND_IMAGE_TAG -ErrorAction SilentlyContinue
    }
    else {
        $env:BACKEND_IMAGE_TAG = $previousImageTag
    }
}
