# Stop All Phantom GPS Servers and restore system state
Write-Host "Stopping all Phantom GPS servers..." -ForegroundColor Yellow

Stop-Process -Name node, cloudflared, tor -ErrorAction SilentlyContinue

# Disable Windows system proxy
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f | Out-Null

Write-Host "✅ All background servers stopped and system proxy cleaned." -ForegroundColor Green

