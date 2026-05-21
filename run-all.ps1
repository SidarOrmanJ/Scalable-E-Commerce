# run-all.ps1
# Starts all microservices in separate, titled PowerShell windows.
# This avoids interleaving logs and allows you to easily stop/restart individual services.

# --- Load .env file ---
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Write-Host "Loading environment variables from .env file..." -ForegroundColor DarkCyan
    Get-Content $envFile | ForEach-Object {
        # Skip blank lines and comments
        if ($_ -match '^\s*$' -or $_ -match '^\s*#') { return }
        # Parse KEY=VALUE pairs (strip surrounding quotes from value if present)
        if ($_ -match '^([^=]+)=(.*)$') {
            $key   = $Matches[1].Trim()
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
            Write-Host "  Set: $key" -ForegroundColor DarkGray
        }
    }
    Write-Host "Environment variables loaded." -ForegroundColor DarkCyan
} else {
    Write-Host "WARNING: .env file not found. Run 'cp .env.example .env' and fill in your secrets." -ForegroundColor Red
    Write-Host "         Services requiring JWT_SECRET (user-service, order-service) will fail to start." -ForegroundColor Red
}

$services = @(
    @{ name = "gateway-service"; path = "gateway-service" },
    @{ name = "user-service"; path = "user-service" },
    @{ name = "stock-service"; path = "stock-service" },
    @{ name = "order-service"; path = "order-service" },
    @{ name = "payment-service"; path = "payment-service" },
    @{ name = "notification-service"; path = "notification-service" }
)

Write-Host "Starting backing infrastructure (Docker Compose)..." -ForegroundColor Yellow
docker compose up -d

Write-Host "Waiting 5 seconds for infrastructure to warm up..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

Write-Host "Starting all 6 microservices..." -ForegroundColor Green

# Build the env-var forwarding block so child windows inherit secrets
$envBlock = ""
foreach ($key in @("JWT_SECRET", "DB_PASSWORD", "AWS_ACCESS_KEY", "AWS_SECRET_KEY")) {
    $val = [System.Environment]::GetEnvironmentVariable($key, 'Process')
    if ($val) {
        $envBlock += "`$env:$key='$val'; "
    }
}

foreach ($service in $services) {
    Write-Host "Launching $($service.name)..." -ForegroundColor Cyan
    # Spawn a new PowerShell window, inject env vars, set title, and run bootRun
    Start-Process powershell -WorkingDirectory $PSScriptRoot -ArgumentList `
        "-NoExit", "-Command", `
        "$envBlock `$Host.UI.RawUI.WindowTitle='$($service.name)'; .\gradlew :$($service.path):bootRun"
}

Write-Host "Launching frontend-service..." -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory $PSScriptRoot -ArgumentList `
    "-NoExit", "-Command", `
    "`$Host.UI.RawUI.WindowTitle='frontend-service'; node frontend/server.js"

Write-Host ""
Write-Host "All microservices and the web frontend have been launched in separate terminal windows." -ForegroundColor Green
Write-Host "To stop them, simply close the respective terminal windows." -ForegroundColor Green
Write-Host "To stop Docker infrastructure: docker compose down" -ForegroundColor DarkGray
