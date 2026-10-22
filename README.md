# Phantom GPS — OmniRush Chameleon v3.0

> Geo-spoofing, multi-hop stealth networking, and a full ProtonVPN-grade VPN
> stack for Windows, Android & Chrome.

---

## What it is

**Phantom GPS** is a multi-device privacy platform with four parts that work
together:

| Component | Folder | What it does |
|---|---|---|
| **Web control plane** | `index.html`, `app.js` | VPN dashboard: connection hero, 34-node server fleet, profiles, protocol/NetShield/kill-switch/split-tunnel controls, Secure Core UI, QR phone sync |
| **Desktop control hub** | `desktop-app/` | Express server (`:3300`) serving the dashboard + VPN config sync endpoints; Smart Proxy Engine (`127.0.0.1:8899`) |
| **Android app** | `android-app/` | WireGuard VPN core (Smart fallback, kill switch, split tunneling, NetShield DNS, leak protection) + Proton-patterned connection UI |
| **Chrome extension** | `location-changer-extension/` | Browser bodyguard: fingerprint/geolocation defenses, WebRTC leak prevention, NetShield ad/tracker/malware blocking, fail-closed proxy routing |

The VPN feature set was designed by studying ProtonVPN's **open-source**
Android app and reimplementing its client-side patterns clean-room — no
Proton code, branding, or assets. You bring your own WireGuard servers
(see `SERVER_CONTRACT.md`).

---

## Key features

- **VPN connection hero** — connect/disconnect, live status, session stats
  (web dashboard + Android app, same UX patterns).
- **34-node server fleet** — country-grouped, searchable, load/latency
  scoring, fastest/manual/random modes, Streaming / P2P / Secure Core /
  Tor feature badges.
- **Smart protocol** — WireGuard UDP → TCP → TLS fallback ordering.
- **NetShield** — three layers: extension domain blocking (103 rules),
  Android DNS filtering, dashboard level selector.
- **Kill switch** — tunnel blocking + Android Always-on/lockdown guidance.
- **Split tunneling** — per-app and per-IP/CIDR, include or exclude.
- **Secure Core UI** — multi-hop entry/exit selection (needs chaining
  server infra).
- **Leak protection** — WebRTC policy, speculative-connection blocking,
  IPv6 blackhole, LAN bypass, DNS-over-HTTPS guidance.
- **QR phone sync** — dashboard pushes the full VPN config to the hub;
  the Android app fetches it and connects.
- **5-layer anti-tracking** — MAC randomization, MAID zeroing, Tor
  multi-hop, browser fingerprint poisoning, Android mock location.
- **Route simulation** — foot/bicycle/vehicle/flight with micro-jitter.

---

## Quick start

**1. Start the control hub** (full guide: `SERVERS.md`)
```powershell
cd phantom-GPS\desktop-app
npm install   # first time only
npm start
```
Open `http://localhost:3300`.

**2. Build the Android app**
```powershell
# first time only: download tunnel-1.0.20230706.aar from Maven Central
# into android-app\libs\tunnel.aar
cd phantom-GPS\android-app
.\build.ps1
```
Install `build\OmniRush-GPS.apk` on your phone (`adb install` or tap the
APK). Requires JDK 21 + Android SDK (build-tools 34.0.0).

**3. Load the Chrome extension**
- `chrome://extensions` → Developer mode → Load unpacked →
  `location-changer-extension`. Accept the privacy/proxy/filtering
  permission warnings.

**4. Connect**
- Dashboard → pick a server → **Push to phone** (Phone Sync panel).
- On the phone, open the Phantom VPN screen and tap **CONNECT**
  (accept the VPN permission dialog).

---

## Docs

| File | Contents |
|---|---|
| `PRD.md` | Full product requirements + everything built to date |
| `SERVERS.md` | How to start the server (hub, ports, verification, troubleshooting) |
| `SERVER_CONTRACT.md` | API your own server must expose (`/api/servers`, `/api/vpn/credentials`, …) |
| `VPN_CONFIG_CONTRACT.md` | Dashboard → hub config schema |
| `PARITY_CHECKLIST.md` | ProtonVPN feature → phantom-GPS status mapping |
| `android-app/BUILD_NOTES.md` | APK build details + WireGuard dependency |
| `android-app/MAINACTIVITY_WIRING.md` | Wiring the Android UI |
| `android-app/VPN_CORE_LICENSE_NOTE.md` | GPL-2.0 implications (accepted) |

---

## Architecture

```
┌──────────────┐   push config    ┌──────────────┐   fetch config   ┌──────────────┐
│ Web dashboard │ ──────────────▶ │ Control hub  │ ───────────────▶ │ Android app  │
│ (control      │  POST /api/     │ :3300        │  GET /api/vpn/   │ (WireGuard   │
│  plane)       │  vpn-config     │              │  config          │  tunnel)     │
└──────────────┘                 └──────────────┘                  └──────┬───────┘
                                                                        │ tunnel
                                                              ┌─────────▼────────┐
                                                              │ Your WireGuard   │
                                                              │ servers          │
                                                              └──────────────────┘
┌──────────────────┐  PAC/fail-closed   ┌──────────────────┐
│ Chrome extension │ ─────────────────▶ │ Smart Proxy Eng. │
│ (bodyguard)      │  127.0.0.1:8899    │                  │
└──────────────────┘                    └──────────────────┘
```
