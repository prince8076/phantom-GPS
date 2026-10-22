# 🖥️ Phantom GPS — Server Start Guide

How to start the control hub, verify it's working, reach it from your phone,
and get the Android app + Chrome extension running against it.

> Last updated: 2026-10-22. Tested against `desktop-app/server.js`
> (Express, `omnirush-desktop@1.0.0`).

---

## 1. Start the control hub

**Prerequisites:** Node.js 18+ (`node --version` to check).

```powershell
cd phantom-GPS\desktop-app
npm install   # first time only — installs express
npm start     # runs: node server.js
```

Leave that terminal window open — closing it stops the server.
Open **`http://localhost:3300`** in your browser.

> `npm start` is defined in `desktop-app/package.json` as `node server.js`.
> If `npm start` ever complains, `node server.js` directly is equivalent.

### Verify it's up

In a second terminal:

```powershell
curl http://localhost:3300/api/status
```

You should get JSON back including `vpnConfigUpdatedAt`. Two useful checks:

| Check | Expected |
|---|---|
| `GET /api/status` | `200` with hub status JSON |
| `GET /api/vpn/config` (before any push) | `404` — "No VPN config has been pushed yet" |
| Dashboard → Phone Sync → **Push to phone** | Status pill shows success |
| `GET /api/vpn/config` (after push) | `200` with your config payload |

### Stop / restart

- Stop: `Ctrl+C` in the server terminal.
- Port `3300` busy? An old copy is still running:
  ```powershell
  netstat -ano | findstr :3300
  taskkill /PID <number> /F
  ```
  Then start again.
- ⚠️ The pushed VPN config lives in **memory only** — restarting the hub
  wipes it. Just push again from the dashboard afterward.

---

## 2. Reach the hub from your phone

On the phone, `localhost` means the phone itself — use your computer's
**Wi-Fi IP** instead:

```powershell
ipconfig   # look for "IPv4 Address", e.g. 192.168.1.5
```

Then on the phone: `http://192.168.1.5:3300` (your IP).

- Both devices must be on the **same Wi-Fi**.
- Windows Firewall may prompt to allow Node.js — allow it (private networks).
- The dashboard's **QR pairing** encodes the hub URL, so scanning it from
  the phone fills this in automatically.

---

## 3. Port map

| Component | Address | Purpose |
|---|---|---|
| Dashboard + hub API | `http://127.0.0.1:3300` | Web UI, `/api/*` endpoints |
| Smart Proxy Engine | `127.0.0.1:8899` | PAC-routed browser proxy (extension) |
| VPN config push | `POST :3300/api/vpn-config` | Dashboard → hub |
| VPN config fetch | `GET :3300/api/vpn/config` | Hub → Android app |

---

## 4. Build & install the Android app

**Prerequisites:** Eclipse Adoptium JDK 21, Android SDK with
build-tools `34.0.0` and platform `android-34` (paths live at the top of
`android-app/build.ps1` — adjust if yours differ).

> The project builds with `build.ps1` (manual aapt2/javac/d8 pipeline),
> **not** Gradle.

```powershell
# FIRST TIME ONLY — the VPN core needs the WireGuard tunnel library:
# download tunnel-1.0.20230706.aar from
# https://repo1.maven.org/maven2/com/wireguard/android/tunnel/1.0.20230706/
# and save it as android-app\libs\tunnel.aar (exact filename).
# Without it, build.ps1 stops with a clear error.

cd phantom-GPS\android-app
.\build.ps1
```

On success: `android-app\build\OmniRush-GPS.apk`
(also copied to `desktop-app\public\OmniRush-GPS.apk`).

**Install on the phone** (USB debugging on):

```powershell
adb install -r android-app\build\OmniRush-GPS.apk
```

or copy the APK to the phone and tap it (allow "install unknown apps").

**First run:** open the Phantom VPN screen → tap **CONNECT** → accept the
Android VPN permission dialog → pair with the hub via the dashboard QR →
connect.

---

## 5. Load the Chrome extension

1. Chrome → `chrome://extensions`
2. Enable **Developer mode** (top right)
3. **Load unpacked** → select the `location-changer-extension` folder
4. Accept the `privacy`, `proxy`, and `declarativeNetRequest` permission
   warnings (expected — they power leak protection + NetShield)
5. Pin the extension, open its popup, and check the **Leak Protection** and
   **NetShield** sections

---

## 6. End-to-end smoke test

1. Start the hub (`npm start`) and open the dashboard.
2. Pick a server in the fleet → **Push to phone** in Phone Sync.
3. `curl http://localhost:3300/api/vpn/config` returns the payload. ✅
4. On the phone (same Wi-Fi), open `http://<your-ip>:3300` — dashboard loads. ✅
5. Phantom VPN screen → **CONNECT** → VPN permission → status shows
   Connected. ✅
6. Extension popup → NetShield counters climb as you browse. ✅

---

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `npm start` → "cannot find module 'express'" | Run `npm install` in `desktop-app/` first |
| `EADDRINUSE :3300` | Kill the old process (see §1), restart |
| Dashboard shows old UI | Hard-refresh: `Ctrl+Shift+R` |
| Phone can't reach hub | Same Wi-Fi? Correct IP from `ipconfig`? Firewall allowed Node.js? |
| `GET /api/vpn/config` → 404 | Normal before first push — push from the dashboard |
| `build.ps1` → missing `tunnel.aar` | Download the AAR into `android-app\libs\` (§4) |
| `build.ps1` → SDK path errors | Edit the `$BUILD_TOOLS`/`$ANDROID_JAR` paths at the top of `build.ps1` |
| Extension proxy breaks browsing | The proxy is fail-closed by design — start the Smart Proxy Engine or toggle the proxy off |
