# ProtonVPN → phantom-GPS Feature Parity Checklist

Goal: full **client-side** feature parity across the three phantom-GPS apps.
Boundary: Proton's server network, Secure Core backend, accounts/billing, and
proprietary server logic cannot be cloned. The user will provide their own
server; the clients must work against a clean server contract.

Source: `~/workspace/analysis/protonvpn-feature-inventory.md`
(ProtonVPN Android 5.20.98.0, REA/JADX + native strings).

Legend: ✅ built · 🟡 partial/stub · ❌ missing · 🔌 needs user's server

## 1. Tunnel & protocols

| Proton capability | Web (control plane) | Extension (bodyguard) | Android (VPN client) | Server needed | Next action |
|---|---|---|---|---|---|
| WireGuard UDP/TCP/TLS + Smart fallback | ✅ protocol selector (Smart/UDP/TCP/TLS) | N/A (no VPN in browser) | ✅ SmartProtocolSelector (UDP→TCP→TLS order) | 🔌 WG endpoints per protocol | Wire TCP/TLS shim (stock wireguard-go is UDP-only) |
| Stealth obfuscation / SNI randomization | ✅ toggle in config | ❌ | 🟡 flag exists, no obfuscation impl | 🔌 server must accept obfuscated ingress | Document as future; needs server support |
| OpenVPN-style fallback path | ❌ | N/A | ❌ | 🔌 | Deprioritized; WG covers parity |
| Connection state machine | 🟡 dashboard shows push state only | ❌ | ✅ VpnState machine | — | Web: show live VPN state via hub polling (future) |
| Recent connections / saved profiles | ✅ connection profiles (localStorage) | ❌ | 🟡 profile model exists | — | Android: profile picker UI |

## 2. Kill switch

| Proton capability | Web | Extension | Android | Server needed | Next action |
|---|---|---|---|---|---|
| Always-on VPN + lockdown modeling | ✅ toggles in config | ❌ | ✅ KillSwitchManager + guidance | — | Android: settings UI linking to system Always-on screen |
| Blocking tunnel (fail-closed) | ✅ config flag | ✅ proxy fails closed | ✅ setBlocking(true) path | — | Extension: verify fail-closed in manual test |

## 3. NetShield (DNS filtering)

| Proton capability | Web | Extension | Android | Server needed | Next action |
|---|---|---|---|---|---|
| Filter levels (malware / ads+trackers / adult) | ✅ level selector | ❌ | ✅ NetShieldDns config | 🔌 filtering resolver IPs | User must supply real resolver addresses; placeholders now |
| Block stats (ads/trackers/malware counts) | ❌ | ❌ | 🟡 stats model, no UI | 🔌 resolver must report counts | Future: stats API |

## 4. Split tunneling

| Proton capability | Web | Extension | Android | Server needed | Next action |
|---|---|---|---|---|---|
| Per-app include/exclude | ✅ package-name editor | ❌ | ✅ addAllowed/DisallowedApplication | — | Android: app picker UI (needs QUERY_ALL_PACKAGES) |
| Per-IP/CIDR include/exclude | ✅ CIDR editor | ❌ | ✅ route add/remove | — | Web: validate CIDR input |

## 5. Server list

| Proton capability | Web | Extension | Android | Server needed | Next action |
|---|---|---|---|---|---|
| Logical servers + connecting domains | ✅ 32-node fleet UI, search, load bars | ❌ | ❌ no fetch yet | 🔌 server-list API | Define + implement hub endpoint serving user's fleet |
| Fastest/random/manual selection | ✅ modes | ❌ | 🟡 selector logic, no data | 🔌 latency/load feed | Replace static seeds with hub-provided live data |
| Streaming-optimized labels | ❌ | ❌ | ❌ | 🔌 | Optional; user labels |
| Secure Core (multi-hop) | ❌ | ❌ | ❌ | 🔌 cooperating infra | Approximable via nested tunnels; needs server support |

## 6. Auth & certificates

| Proton capability | Web | Extension | Android | Server needed | Next action |
|---|---|---|---|---|---|
| Account auth / session | ❌ | ❌ | 🟡 CredentialProvider seam | 🔌 token endpoint | Define credential issuance API |
| Short-lived cert refresh | ❌ | ❌ | 🟡 refresh seam | 🔌 cert endpoint | Implement refresh worker against user's API |
| QR phone sync / login | ✅ QR phone-sync concept | ❌ | ❌ | 🔌 | Web: QR encodes hub URL + pairing token |

## 7. Auto-connect & session handling

| Proton capability | Web | Extension | Android | Server needed | Next action |
|---|---|---|---|---|---|
| Boot auto-connect | ❌ | ❌ | ✅ BootReceiver | — | Manual test on device |
| Quick Settings tile | ❌ | ❌ | ✅ PhantomTileService | — | Manual test on device |
| Persistent notification w/ actions | ❌ | ❌ | ✅ foreground notif + disconnect action | — | Manual test |
| App-update handling | ❌ | ❌ | ❌ | — | Low priority |

## 8. Custom DNS & leak protection

| Proton capability | Web | Extension | Android | Server needed | Next action |
|---|---|---|---|---|---|
| Custom DNS through tunnel | ✅ resolver editor | 🟡 Secure DNS guidance (manual) | ✅ addDnsServer | 🔌 user's resolver | Extension cannot enforce Secure DNS via API (documented) |
| LAN bypass | ✅ toggle | ✅ PAC bypasses LAN | ✅ LAN routes | — | Manual test |
| IPv6 leak blocking | ✅ setting | 🟡 WebRTC policy helps | ✅ IPv6 blackhole | — | Manual test |
| WebRTC leak prevention | ❌ | ✅ | ❌ | — | Done |

## 9. Misc client features

| Proton capability | Web | Extension | Android | Server needed | Next action |
|---|---|---|---|---|---|
| Onboarding / What's New | ❌ | ❌ | ❌ | — | Web: onboarding tour (nice-to-have) |
| Telemetry settings | ❌ | ❌ | ❌ | — | Optional; default off |
| Bug reporting | ❌ | ❌ | ❌ | 🔌 endpoint | Optional |
| Upgrade/promo flows | ❌ | ❌ | ❌ | 🔌 billing | Out of scope (user's own monetization) |
| 2FA / account security | ❌ | ❌ | ❌ | 🔌 | Out of scope |

## Priority order (user: own server, web app first)

1. Server contract for user's server (what the hub/Android must serve/consume).
2. Web app rebuild: Proton-grade connection dashboard UX (status, server list,
   profiles, settings) on top of existing control plane.
3. Hub: serve live server list + credentials from user's server config.
4. Android: MainActivity consumer (fetch config → resolve endpoints →
   credentials → start VPN) + connection UI.
5. Extension: keep bodyguard scope; verify fail-closed + leak tests.
6. Advanced: TCP/TLS shim, nested-tunnel "Secure Core"-like mode, stats.
