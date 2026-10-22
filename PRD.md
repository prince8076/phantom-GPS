# Product Requirements Document (PRD): Phantom GPS — OmniRush Chameleon v3.0

> Living document. Last updated: 2026-10-22.
> Covers everything built to date across the web control plane, desktop hub,
> Android app, and Chrome extension.

---

## 1. Product Summary

**Phantom GPS (OmniRush Chameleon v3.0)** is a multi-device stealth and privacy
platform spanning Windows, Android, and Chrome. It combines:

- **GeoIP chameleon & rotation** — 34-node global exit fleet with custom
  rotation cadence (1/3/5/10/30 min), connection profiles, and QR phone sync.
- **A full ProtonVPN-grade VPN stack** — WireGuard tunnel core on Android
  (Smart protocol fallback, kill switch, split tunneling, NetShield DNS
  filtering, leak protection), a browser bodyguard extension, and a web
  control plane that pushes configuration to the phone.
- **5-layer anti-tracking fortress** — MAC randomization, MAID zeroing,
  Tor multi-hop, browser fingerprint poisoning, Android mock location
  injection.
- **Multi-modal route simulation** — foot / bicycle / vehicle / flight travel
  with velocity interpolation and micro-jitter.

The VPN feature set was designed by studying ProtonVPN's open-source Android
app (via REA reverse engineering) and **reimplementing its client-side
patterns clean-room** — no Proton code, branding, or assets were copied.
Proton's server network, Secure Core backend, and account/billing systems are
not replicated; the user provides their own WireGuard server infrastructure
(see `SERVER_CONTRACT.md`).

---

## 2. Components

### 2.1 Web app — VPN control plane (`index.html`, `app.js`, `styles-additions.css`)

The dashboard is the control plane for the whole system.

**Built:**
- VPN connection hero: connect/disconnect, status pill, session stats
  (server, virtual IP, protocol, session time, data up/down).
- Server fleet: 34 nodes grouped by country, search, load bars, latency,
  fastest / manual / random-pool selection modes.
- Feature badges per node: Streaming, P2P, Secure Core-capable, Tor-friendly.
- Rotation cadence picker (1/3/5/10/30 min) + rotation history.
- Connection profiles (persisted in localStorage; seeded: Maximum Stealth,
  Streaming, Travel Mode).
- Protocol selection: Smart / WireGuard-UDP / WireGuard-TCP / WireGuard-TLS.
- NetShield level, kill switch, always-on lockdown toggles.
- Split-tunnel editor (app package names + IP/CIDR rules).
- DNS & leak settings: custom resolvers, LAN bypass, IPv6 handling.
- Secure Core multi-hop UI (entry/exit pickers; stored in config, honored
  when the server backend supports chaining).
- P2P port-forwarding toggle.
- Tor-over-VPN guidance card.
- Phone Sync panel: live config payload preview + QR pairing + push to
  `POST /api/vpn-config`.
- Dark Proton-patterned client theme for the VPN view.

**Design note:** fleet load/latency are simulated seed values until a live
feed is wired (see `SERVER_CONTRACT.md`).

### 2.2 Desktop control hub (`desktop-app/server.js`)

Express server; UI on port `3300`, Smart Proxy Engine on `127.0.0.1:8899`.

**Built:**
- Serves the dashboard and all existing APIs (`/api/status`, `/api/shield`,
  `/api/autorotate`, `/api/settings/interval`, `/api/rotate`,
  `/api/select-node`).
- `POST /api/vpn-config` — accepts the dashboard's pushed VPN config
  (validates the `phantom-vpn-config` contract id; stored in memory).
- `GET /api/vpn/config` — serves the latest pushed config to the Android app
  (404 when nothing pushed yet).
- `/api/status` includes `vpnConfigUpdatedAt`.
- Stealth radar map fixed: keyless OpenStreetMap tiles with dark-mode CSS
  filter (replaced CARTO, which now requires an API key).

**Limitations:** pushed config is in-memory only (lost on restart);
validation is minimal. Planned: `/api/servers`, `/api/vpn/credentials`,
`/api/netshield/resolvers` per `SERVER_CONTRACT.md` (can proxy the user's
own server).

### 2.3 Android app (`android-app/`) — VPN core + connection UI

Java app, package `ai.omnirush.chameleon`, minSdk 26, targetSdk 34.
Builds with `android-app/build.ps1` (manual aapt2/javac/d8 pipeline —
**not** Gradle).

**VPN core** (`.../vpn/`, 17 files):
- `PhantomVpnService` — VpnService with connection state machine,
  foreground notification + disconnect action, watchdog/reconnect.
- `SmartProtocolSelector` — WireGuard UDP → TCP → TLS fallback ordering.
- `KillSwitchManager` — blocking builder + Always-on/lockdown guidance.
- `NetShieldDns` — DNS filtering levels (real filtering resolvers supplied
  by the user's server).
- Split tunneling (per-app via builder + per-IP routes), IPv6 blackhole,
  LAN bypass routes.
- `WgTunnelBackend` seam + `WireGuardGoBackend` adapter over the
  open-source `com.wireguard.android:tunnel` (GPL-2.0 — see
  `VPN_CORE_LICENSE_NOTE.md`; user accepted GPL-2.0 for the Android app).
- `CredentialProvider` seam + `HubClient` (hub HTTP: pushed config, server
  list, credentials, NetShield resolvers).
- `VpnConnector.prepareConfig()` — one-call fetch → resolve → credentials →
  complete `VpnConfig` on the UI thread.
- `HubSettings` — persisted hub URL from QR pairing.
- `BootReceiver` (auto-connect), `PhantomTileService` (quick tile),
  `VpnActionReceiver`, `VpnState`/`VpnStateStore`/`VpnStateListener`.
- `VpnConfig` — full schema incl. Secure Core entry/exit + P2P port-forward.

**Connection UI** (Proton-patterned, app's own identity):
- `VpnConnectActivity` — status header, hero connect button, session stats,
  hub server list with flags/load/feature badges, NetShield + kill-switch
  toggles, VPN consent flow.
- `ServerListAdapter`, layouts `activity_vpn_connect` / `item_server`,
  `btn_connect` drawable.

**Build requirement:** download `tunnel-1.0.20230706.aar` from Maven Central
into `android-app/libs/tunnel.aar` before running `build.ps1`.

### 2.4 Chrome extension (`location-changer-extension/`) — browser bodyguard

Manifest V3, v2.3.

**Built:**
- Fingerprint/geolocation defenses (content script, MAIN world).
- WebRTC leak prevention (`chrome.privacy`), speculative-connection blocking.
- Fail-closed PAC proxy routing through `127.0.0.1:8899` (LAN bypass,
  no DIRECT fallback).
- **NetShield**: declarativeNetRequest rulesets — 91 ad/tracker domains,
  12 malware/cryptominer domains — with per-category toggles and live
  block counters in the popup.
- Secure DNS guidance (manual — Chrome exposes no extension API).
- Leak Protection popup section; hub status polling.

**Note:** new `privacy`, `proxy`, `declarativeNetRequest` permissions trigger
Chrome install/update warnings.

---

## 3. Feature Matrix (requested → status)

| Feature | Web | Android | Extension | Needs user's server |
|---|---|---|---|---|
| Ad blocker while browsing | — | — | ✅ rulesets + counters | — |
| Stop trackers | — | — | ✅ + fingerprint defenses | — |
| Malware/phishing protection | ✅ toggle | ✅ NetShield DNS | ✅ domain rules | ✅ filtering resolvers |
| Faster page loads | — | — | ✅ (less to download) | — |
| NetShield levels | ✅ | ✅ | ✅ | ✅ resolver IPs |
| Split tunneling | ✅ editor | ✅ per-app/IP | — | — |
| Kill switch | ✅ toggle | ✅ + lockdown | — | — |
| Worldwide cover | ✅ 34-node UI | ✅ server list | — | ✅ real servers |
| Streaming labels | ✅ badges | ✅ badges | — | ✅ server support |
| Secure Core multi-hop | ✅ UI + config | ✅ schema | — | ✅ chained infra |
| P2P support | ✅ toggle | ✅ flag | — | ✅ port forwarding |
| Tor over VPN | ✅ guidance | — | — | — |

---

## 4. API Contracts

- Dashboard → hub: `POST /api/vpn-config` (`VPN_CONFIG_CONTRACT.md`).
- Hub → Android: `GET /api/vpn/config`, `GET /api/servers`,
  `POST /api/vpn/credentials`, `GET /api/netshield/resolvers`
  (`SERVER_CONTRACT.md`).
- Full Proton→phantom feature mapping: `PARITY_CHECKLIST.md`.
- REA feature inventory: `~/workspace/analysis/protonvpn-feature-inventory.md`.

---

## 5. Setup

- Start the server: **`SERVERS.md`** (desktop hub, ports, verification,
  phone access, troubleshooting).
- Build the APK: `android-app/BUILD_NOTES.md` + `build.ps1`.
- Wire the Android UI: `android-app/MAINACTIVITY_WIRING.md`.
- License notes: `android-app/VPN_CORE_LICENSE_NOTE.md` (GPL-2.0 accepted).

---

## 6. Roadmap / Open Items

1. User provisions WireGuard servers + implements `SERVER_CONTRACT.md`
   endpoints (or minimal static test setup).
2. Hub: disk persistence for pushed config; implement `/api/servers`,
   `/api/vpn/credentials`, `/api/netshield/resolvers` (or proxy).
3. Android: `./build.ps1` on user's machine; fix any compile errors; add a
   MainActivity entry point launching `VpnConnectActivity`.
4. Live fleet load/latency feed (replace simulated seeds).
5. Real NetShield filtering resolver addresses.
6. TCP/TLS WireGuard transport shim (stock wireguard-go is UDP-only).
7. Manual Chrome extension testing (permission prompts, fail-closed proxy).
8. Nested-tunnel Secure Core approximation once server chaining exists.

---

## 7. Ground Rules (standing)

- Clean-room reimplementation only: study open-source patterns, never copy
  proprietary code, branding, or assets.
- No cloning of Proton's server infrastructure, accounts, or billing.
- Extension is a bodyguard, not a VPN client (browsers can't run tunnels).
