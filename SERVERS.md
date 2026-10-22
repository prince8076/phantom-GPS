# 🚀 Phantom GPS - System Run & Server Architecture Guide

This guide contains the exact commands and scripts to start all background services, web dashboards, cloud tunnels, and mobile spoofing daemons.

---

## ⚡ 1-Click Launch (All Servers Simultaneously)

We provide a dedicated Windows script that boots up the entire Phantom GPS stack (UI Dashboard, Smart Proxy, Tor Circuit Daemon, 171,000+ City Roaming Engine, and Cloudflare Mobile Tunnel) in one command:

### Option A: Via PowerShell
```powershell
cd c:\Users\rahul\omnirush.ai
powershell -ExecutionPolicy Bypass -File .\start-all-servers.ps1
```

### Option B: Via Windows Command Prompt / Double-Click Batch
```bat
cd c:\Users\rahul\omnirush.ai
start-all-servers.bat
```

*(You can also simply double-click `start-all-servers.bat` in Windows File Explorer).*

---

## 🛠️ Individual Service Commands

If you ever wish to run or inspect individual servers separately in their own terminal windows, use the commands below:

### 1. Main Core Server (Dashboard + Road Route Engine + Global Cities)
* **Port:** `3300` (Web UI & APIs)
* **Proxy Port:** `8899` (Stealth HTTP/HTTPS Tunnel)
* **Tor Port:** `9055` (Multi-Circuit SOCKS5)
```powershell
cd c:\Users\rahul\omnirush.ai\desktop-app
node server.js
```
* **Dashboard URLs:**
  - **Laptop Local:** `http://127.0.0.1:3300`
  - **Local LAN:** `http://192.168.1.70:3300`

---

### 2. Cloudflare Cloud Tunnel (For Mobile Remote Sync)
Creates a secure public HTTPS endpoint so your Android phone can sync across Wi-Fi or 4G/5G:
```powershell
npx --yes cloudflared tunnel --url http://127.0.0.1:3300
```

---

### 3. Android Mock GPS Spoofer Service (ADB Direct Start)
To start, stop, or re-install the Android spoofer service directly on your connected Samsung phone:

#### Install / Update APK on Phone:
```powershell
adb install -r -g c:\Users\rahul\omnirush.ai\desktop-app\public\OmniRush-GPS.apk
adb shell "appops set ai.omnirush.chameleon MOCK_LOCATION allow"
```

#### Launch Phantom GPS Mobile App:
```powershell
adb shell am start -n ai.omnirush.chameleon/.MainActivity
```

#### Start Foreground Mock Spoofer Daemon:
```powershell
adb shell am start-foreground-service -n ai.omnirush.chameleon/.MockLocationService --es serverUrl "http://192.168.1.70:3300"
```

#### Stop Mock Spoofer Daemon:
```powershell
adb shell am force-stop ai.omnirush.chameleon
```

---

## 🌐 Server Architecture & Port Map

| Component | Port / Interface | Description |
| :--- | :--- | :--- |
| **Phantom Dashboard** | `http://127.0.0.1:3300` | Real-time map radar, turn-by-turn road simulator, and telemetry hub |
| **Smart Proxy Engine** | `127.0.0.1:8899` | PAC-aware HTTP/HTTPS proxy with NCSI/Windows Update bypass |
| **Tor Core Circuit** | `127.0.0.1:9055` | Country-specific multi-hop SOCKS5 privacy exit nodes |
| **OpenStreetMap OSRM** | `router.project-osrm.org` | Turn-by-turn road geometry & real street routing engine |
| **Global City Engine** | Internal DB | 171,094 authentic cities across 246 countries and territories |
| **Mobile Service** | Android Background | Simultaneous spoofing of `fused`, `gps`, and `network` location providers |

---

## 🛑 How to Stop All Servers

To safely terminate all running servers and restore original system proxy settings:

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-all-servers.ps1
```
Or kill by process names:
```powershell
Stop-Process -Name node, cloudflared, tor -ErrorAction SilentlyContinue
```

