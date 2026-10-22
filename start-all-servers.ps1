# Start All Phantom GPS Background Servers
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "       👻 STARTING PHANTOM GPS FULL SERVER STACK      " -ForegroundColor Green
Write-Host "=====================================================" -ForegroundColor Cyan

$WorkspaceRoot = "c:\Users\rahul\omnirush.ai"

# 1. Start Main Node Server (UI + Proxy + OSRM + Global Cities)
Write-Host "`n[1/3] Launching Phantom GPS Core Hub (Port 3300 & Proxy 8899)..." -ForegroundColor Yellow
$serverProcess = Start-Process -FilePath "node" -ArgumentList "$WorkspaceRoot\desktop-app\server.js" -WorkingDirectory "$WorkspaceRoot\desktop-app" -PassThru -WindowStyle Hidden

Start-Sleep -Seconds 2

# 2. Start Cloudflare Remote Sync Tunnel
Write-Host "[2/3] Launching Cloudflare Mobile Sync Tunnel..." -ForegroundColor Yellow
$tunnelProcess = Start-Process -FilePath "powershell" -ArgumentList "-Command npx --yes cloudflared tunnel --url http://127.0.0.1:3300" -WorkingDirectory $WorkspaceRoot -PassThru -WindowStyle Hidden

# 3. Check ADB Connected Android Phone
Write-Host "[3/3] Checking connected Android devices..." -ForegroundColor Yellow
$adbDevices = & adb devices
if ($adbDevices -match "device\b") {
    Write-Host "  -> Android device detected. Launching Phantom GPS..." -ForegroundColor Green
    & adb shell am start -n ai.omnirush.chameleon/.MainActivity | Out-Null
} else {
    Write-Host "  -> No USB Android phone attached (connect via USB or run over Wi-Fi)." -ForegroundColor DarkGray
}

Write-Host "`n=====================================================" -ForegroundColor Cyan
Write-Host "  ✅ ALL SERVERS ONLINE & OPERATIONAL!" -ForegroundColor Green
Write-Host "  - Local Dashboard:  http://127.0.0.1:3300" -ForegroundColor White
Write-Host "  - LAN Mobile Sync:  http://192.168.1.70:3300" -ForegroundColor White
Write-Host "  - Stop Servers:     Run .\stop-all-servers.ps1" -ForegroundColor Yellow
Write-Host "=====================================================`n" -ForegroundColor Cyan

