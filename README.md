# GeoIP Chameleon & Multi-Modal Stealth Network

> Enterprise-Grade Geo-Spoofing, Multi-Hop Dynamic Proxy Routing, Velocity Interpolation & 5-Layer Anti-Tracking Fortress for Windows & Android.

---

## 🌟 Key Features

1. **Custom Cadence & True-Random Global Pool:**
   - Instead of 5 static locations, rotate across **30+ international exit nodes** (Zurich, Tokyo, Reykjavik, New York, Singapore, Berlin, London, Paris, Amsterdam, Stockholm, Oslo, Helsinki, Toronto, Sydney, Seoul, Dubai, São Paulo, Vienna, Madrid, Rome, Dublin, Warsaw, Prague, Bucharest, Taipei, Mumbai, Los Angeles, Frankfurt).
   - **Custom Cadence**: Configure the rotation interval dynamically (1 min, 3 min, 5 min, 10 min, 30 min) or trigger instant random hops.

2. **Multi-Modal Route Simulation & Velocity Engine:**
   - Define an **Origin** and **Destination** coordinate or location.
   - Choose travel velocity:
     - 🚶 **On Foot**: ~5 km/h (1.4 m/s)
     - 🚲 **Bicycle**: ~18 km/h (5.0 m/s)
     - 🚗 **Vehicle**: ~60 km/h (16.7 m/s)
     - ✈️ **Flight**: ~500 km/h (140.0 m/s)
   - Real-time bearing angle calculations and micro-jitter insertion prevents heuristic anomaly detection by apps like Google Maps, Uber, Tinder, and life360.

3. **1-Tap Phone Sync & QR Code Connect:**
   - Click **"Scan to Sync Phone"** on the dashboard to scan the live encrypted Cloud Tunnel URL.
   - Download the native signed APK (`OmniRush-GPS.apk`) or manage the spoofer over the air.

4. **5-Layer Privacy Fortress:**
   - **Layer 1 (Physical Hardware)**: Automated Wi-Fi MAC address randomization across all Windows profiles.
   - **Layer 2 (OS Identity)**: Windows Advertising ID (MAID) zeroed and telemetry heartbeat terminated.
   - **Layer 3 (Network)**: Tor multi-hop circuit with zero-downtime smart failover and strict DNS leak protection.
   - **Layer 4 (Browser Fingerprint)**: Dynamic Canvas pixel noise, WebGL GPU model spoofing, and AudioContext frequency buffer jitter.
   - **Layer 5 (Behavioral/Location)**: Continuous system-level MockLocationProvider injection across Android `fused`, `gps`, and `network` layers.

---

## 🚀 Quick Start Guide

### 1. Launch Control Hub (Laptop)
```powershell
node c:\Users\rahul\omnirush.ai\desktop-app\server.js
```
- Open browser at `http://127.0.0.1:3300` or Cloud Tunnel: `https://northwest-stuart-ruling-switch.trycloudflare.com`

### 2. Connect Your Phone
- Scan the on-screen QR Code with your phone camera.
- In Android **Developer Options**, set **"Select mock location app"** to ** GPS**.
- All location queries on your device now follow the laptop's rotation schedule or active travel route.

---

## 🛠️ Architecture Overview

```
[ Laptop Chrome ] <--- (CDP Geolocation Override) ---+
                                                     |
[ Android Phone ] <--- (Cloud Tunnel / Fused Mock) ---+---> [ OmniRush Control Hub ] ---> [ Tor Multi-Hop Daemon ]
                                                     |      (server.js : 3300)             (tor.exe : 9055)
[ Smart Proxy ]  <--- (Failover Port 8899) ----------+
```

## 🛠️ Architecture Overview

```
[ Laptop Chrome ] <--- (CDP Geolocation Override) ---+
                                                     |
[ Android Phone ] <--- (Cloud Tunnel / Fused Mock) ---+---> [ OmniRush Control Hub ] ---> [ Tor Multi-Hop Daemon ]
                                                     |      (server.js : 3300)             (tor.exe : 9055)
[ Smart Proxy ]  <--- (Failover Port 8899) ----------+
```

