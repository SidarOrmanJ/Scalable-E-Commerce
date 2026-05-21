# run-all.ps1
# Starts all microservices in separate, titled PowerShell windows.
# This avoids interleaving logs and allows you to easily stop/restart individual services.

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

foreach ($service in $services) {
    Write-Host "Launching $($service.name)..." -ForegroundColor Cyan
    # Spawns a new PowerShell window, sets its title, and runs bootRun in the current directory
    Start-Process powershell -WorkingDirectory $PSScriptRoot -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle='$($service.name)'; .\gradlew :$($service.path):bootRun"
}

Write-Host "Launching frontend-service..." -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory $PSScriptRoot -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle='frontend-service'; node frontend/server.js"

Write-Host "All microservices and the web frontend have been launched in separate terminal windows." -ForegroundColor Green
Write-Host "To stop them, simply close the respective terminal windows." -ForegroundColor Green
