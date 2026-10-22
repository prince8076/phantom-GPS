# BUILD_NOTES.md — Phantom GPS Android VPN core

Draft implementation notes for the clean-room WireGuard VPN core in
`src/main/java/ai/omnirush/chameleon/vpn/`. Nothing here is copied from
ProtonVPN or WireGuard sources; concepts (smart protocol fallback,
always-on kill switch, DNS-level NetShield, per-app split tunneling) were
studied from public behavior and reimplemented.

## 1. Gradle setup

`android-app/build.gradle` (module level). The existing app is plain
framework Java (`org.json`, no AndroidX) — the tunnel artifact below is the
**only** new dependency:

```gradle
dependencies {
    // WireGuard protocol engine (wireguard-go + public Java API).
    // Published on Maven Central by the WireGuard project.
    implementation 'com.wireguard.android:tunnel:1.0.20230706'
}
```

Notes:

- **License — read this first.** `com.wireguard.android:tunnel` is
  **GPL-2.0**. Linking it (even as a Gradle dependency) brings GPL
  obligations to the whole APK: you must offer the complete corresponding
  source of the app to anyone you distribute the APK to. If phantom-GPS is
  meant to stay closed-source, do **not** ship this dependency — use the
  `WgTunnelBackend` seam with a userspace WireGuard implementation under a
  permissive license (e.g. boringtun, MIT/Apache-2.0) instead.
- No NDK setup needed: the AAR bundles `libgojni.so` for all ABIs.
- minSdk 26 is fine for everything here. `targetSdk 34` is required for
  `foregroundServiceType="systemExempted"` to take effect; on devices below
  API 34 the manifest attribute is ignored and the service still works as a
  normal foreground service.
- `QUERY_ALL_PACKAGES` (split-tunnel app picker) is a restricted permission
  on API 30+; on Play-distributed builds it needs a declaration. For
  side-loaded builds it just works.

## 2. Manifest requirements (see updated AndroidManifest.xml)

- `PhantomVpnService`: `android:permission="android.permission.BIND_VPN_SERVICE"`,
  `foregroundServiceType="systemExempted"`, plus the
  `<action android:name="android.net.VpnService" />` intent filter.
- `com.wireguard.android.backend.GoBackend$VpnService`: **mandatory** —
  the tunnel artifact brings the TUN interface up through its own inner
  `VpnService`. Without this entry `GoBackend.setState(UP)` fails.
- `PhantomTileService`: `BIND_QUICK_SETTINGS_TILE` + `QS_TILE` intent filter.
- `BootReceiver`: `BOOT_COMPLETED` / `QUICKBOOT_POWERON` / `MY_PACKAGE_REPLACED`.
- `VpnActionReceiver`: notification "Disconnect" action, `exported="false"`.
- New `uses-permission`s: `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`,
  `WAKE_LOCK`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `QUERY_ALL_PACKAGES`.

## 3. Architecture

```
MainActivity ──(VpnService.prepare consent)──▶ PhantomVpnService (VpnService)
        │                                              │ state machine (VpnState)
        │ config JSON ◀── control-hub sync              ├─▶ SmartProtocolSelector (UDP→TCP→TLS)
        │                                              ├─▶ WgTunnelBackend ─▶ WireGuardGoBackend
        │                                              │       (GoBackend + inner VpnService)
        │                                              ├─▶ KillSwitchManager (always-on guidance)
        │                                              ├─▶ NetShieldDns (filtering resolvers)
        │                                              └─▶ VpnStateStore (listeners: UI, tile)
BootReceiver ──(was connected?)──▶ PhantomVpnService
PhantomTileService ◀── VpnStateStore ──▶ toggle connect/disconnect
```

- **Smart protocol**: `WIREGUARD_UDP → WIREGUARD_TCP → WIREGUARD_TLS`,
  each with its own timeout. Stock wireguard-go is UDP-only, so TCP/TLS
  candidates are skipped unless a TCP-encapsulation shim is added later
  (the seam is in place: `supportsTcpTlsTransports()`).
- **Kill switch**: two layers. (1) While our tunnel is up,
  `Builder.setBlocking(true)` on the native path. (2) The real Android kill
  switch is the OS: `KillSwitchManager` guides the user to enable
  "Always-on VPN" + "Block connections without VPN" in system settings.
  There is no public API to read those OS toggles — we model the expected
  state, documented in the class.
- **NetShield**: DNS through the tunnel via `addDnsServer()` using
  per-level filtering resolvers. Resolver IPs are placeholders —
  `NetShieldDns.setResolverIps()` must be fed real filtering-DNS endpoints
  from the control hub. The `DnsBlocklist` hook is the seam for a future
  on-device DNS proxy.
- **Split tunneling**: per-app via `Builder.addAllowedApplication()` /
  `addDisallowedApplication()` (native-builder path); per-IP via
  WireGuard `AllowedIPs` (GoBackend path). Per-app rules cannot be applied
  through the backend-owned interface — noted in `WireGuardGoBackend`.
- **IPv6**: `BLOCK`/`ROUTE` captures `::/0` into the tunnel so nothing
  leaks around it; `OFF` leaves IPv6 alone (leak risk, default is BLOCK).
- **LAN bypass**: `PUBLIC_V4_ROUTES` (0.0.0.0/0 minus RFC-1918) keeps local
  traffic off the tunnel on both paths.
- **Auth**: `CredentialProvider` seam for short-lived credentials;
  `VpnConfig.credentialsNeedRefresh()` triggers a refresh 5 min before
  expiry during the connect flow.

## 4. Control-hub sync contract (what the phone needs)

The web dashboard already syncs to the phone; the VPN core consumes one
JSON object per connection. Endpoint shape (proposed; the dashboard
subagent owns the web side — keep the field names in sync):

```
GET  /api/vpn/config?server=<id>        ->  VpnConfig JSON (see below)
POST /api/vpn/credentials/refresh        ->  { privateKey, address, expiresAt }
```

`VpnConfig` JSON (all fields optional except server.endpoint,
server.publicKey, credentials.privateKey, credentials.address):

```json
{
  "version": 1,
  "server": { "id": "zurich", "name": "Zurich, Switzerland",
              "country": "CH", "endpoint": "203.0.113.10",
              "port": 51820, "publicKey": "<base64>" },
  "protocol": "smart",
  "netshield": "ads_malware",
  "killSwitch": { "enabled": true, "setupGuidanceShown": false },
  "splitTunnel": { "mode": "exclude",
                   "apps": ["com.example.game"],
                   "ips": [] },
  "customDns": [],
  "lanBypass": true,
  "ipv6Mode": "block",
  "mtu": 1280,
  "credentials": { "privateKey": "<base64>",
                   "address": "10.8.0.2/32",
                   "expiresAt": 1760000000000 }
}
```

Enum spellings (case-insensitive on parse):
- `protocol`: `smart` | `wireguard_udp` | `wireguard_tcp` | `wireguard_tls`
- `netshield`: `off` | `malware` | `ads_malware` | `ads_malware_adult`
- `splitTunnel.mode`: `disabled` | `include_only` | `exclude`
- `ipv6Mode`: `block` | `route` | `off`

WireGuard keys are base64 (44 chars). `expiresAt` is epoch **millis**;
`0`/absent = non-expiring (not recommended).

## 5. Testing checklist (before merging to the repo)

1. `./gradlew :android-app:assembleDebug` compiles with the tunnel AAR.
2. Fresh install → Connect → system "Connection request" dialog appears once.
3. Toggle airplane mode mid-tunnel → `WAITING_FOR_NETWORK` → auto-resumes.
4. Kill the app process mid-tunnel → notification persists; `onRevoke`
   path leaves no orphan TUN.
5. Reboot with auto-connect on → tunnel re-establishes without UI.
6. `adb shell dumpsys connectivity` shows the VPN network; DNS queries
   egress the tunnel endpoint (verify with the dashboard's leak test).
7. IPv6 leak check with `ipv6Mode: block` (e.g. ipv6-test.com in a browser
   routed through the tunnel).
8. Tile: add "Phantom VPN" tile in Quick Settings → tap toggles.
9. Split-tunnel exclude: listed app's traffic bypasses the tunnel
   (native-builder path only — see class notes).

## 6. Known limitations of this draft

- No TCP/TLS obfuscation yet (needs a shim in front of wireguard-go).
- Per-app split tunneling only on the native-builder path.
- No nested double-hop (Secure-Core analogue) — needs cooperating server infra.
- Handshake liveness is inferred from backend state; a silent dead peer is
  caught by the 5 s watchdog, not during candidate probing.
- GPL-2.0 applies to any APK shipping the tunnel artifact (see §1).
