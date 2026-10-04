# GlucoTwin AI local development setup script (PowerShell)
# Run from the workspace root: .\scripts\local-setup.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent

Write-Host "=== GlucoTwin AI Local Setup ===" -ForegroundColor Cyan

# 1. Copy .env.example if .env doesn't exist
$envFile = Join-Path $Root "backend\.env.example"
$targetEnv = Join-Path $Root "backend\.env"
if (-not (Test-Path $targetEnv)) {
    Copy-Item $envFile $targetEnv
    Write-Host "Created backend\.env from .env.example — edit secrets before running!" -ForegroundColor Yellow
} else {
    Write-Host "backend\.env already exists, skipping copy" -ForegroundColor Green
}

# 2. Pull Docker images
Write-Host "Pulling Docker images..." -ForegroundColor Cyan
docker compose pull

# 3. Start postgres and redis only (for Flyway)
Write-Host "Starting postgres and redis..." -ForegroundColor Cyan
docker compose up -d postgres redis

# Wait for postgres
Write-Host "Waiting for postgres to be ready..." -ForegroundColor Cyan
$retries = 0
do {
    Start-Sleep -Seconds 2
    $retries++
    $ready = docker compose exec postgres pg_isready -U glucotwin 2>&1
} while ($LASTEXITCODE -ne 0 -and $retries -lt 15)

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: PostgreSQL did not become ready in time" -ForegroundColor Red
    exit 1
}

# 4. Seed synthetic data
Write-Host "Seeding synthetic data..." -ForegroundColor Cyan
Get-Content (Join-Path $Root "data\synthetic\seed_patients.sql") | docker compose exec -T postgres psql -U glucotwin glucotwin
Get-Content (Join-Path $Root "data\synthetic\seed_wearable_events.sql") | docker compose exec -T postgres psql -U glucotwin glucotwin

Write-Host ""
Write-Host "=== Setup complete! ===" -ForegroundColor Green
Write-Host "Start the full stack with: docker compose up" -ForegroundColor Cyan
Write-Host "Backend API: http://localhost:8080/api/docs" -ForegroundColor Cyan
Write-Host "ML Service:  http://localhost:8000/docs" -ForegroundColor Cyan
