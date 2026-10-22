# Phantom VPN Config Contract

Clean-room interface contract between the **phantom-GPS web dashboard** (control
plane) and the **phantom-GPS Android app** (VPN consumer). Versioned, JSON only.

- Contract name: `phantom-vpn-config`
- Contract version: `1`
- The dashboard is the source of truth for *preferences*; the Android app is the
  source of truth for *connection state* (connected/connecting/disconnected).

## Transport

| Item | Value |
|---|---|
| Endpoint | `POST <hub>/api/vpn-config` |
| Default hub | `http://127.0.0.1:3300` (configurable in the dashboard) |
| Content-Type | `application/json` |
| Success response | `{"ok": true, "appliedAt": "<ISO-8601>"}` |
| Failure response | `{"ok": false, "error": "<message>"}` |

The dashboard POSTs the payload whenever the user presses **Push to phone** or
applies a profile. The Android app validates with the same schema, applies the
settings to its `VpnService`, and returns the result. If the hub is unreachable
the dashboard keeps the payload locally and marks the sync as **pending**.

## Payload schema (v1)

```json
{
  "contract": "phantom-vpn-config",
  "version": 1,
  "issuedAt": "2026-10-22T15:30:00.000Z",
  "rotation": {
    "cadenceSeconds": 300,
    "autoRotate": true,
    "shieldEnabled": true
  },
  "server": {
    "mode": "manual",
    "serverId": "zurich",
    "pool": ["zurich", "tokyo", "reykjavik"]
  },
  "protocol": "smart",
  "netshield": "ads-trackers",
  "killSwitch": {
    "enabled": true,
    "lockdown": true
  },
  "splitTunneling": {
    "enabled": false,
    "mode": "exclude",
    "apps": ["com.bank.example"],
    "ips": ["192.168.1.0/24"]
  },
  "dns": {
    "custom": [],
    "lanBypass": true,
    "ipv6": "block"
  }
}
```

### Field reference

| Field | Type | Enum / format | Default | Meaning |
|---|---|---|---|---|
| `contract` | string | `phantom-vpn-config` | — | Contract identifier |
| `version` | integer | `1` | — | Schema version |
| `issuedAt` | string | ISO-8601 UTC | — | When the dashboard built the payload |
| `rotation.cadenceSeconds` | integer | `60`, `180`, `300`, `600`, `1800` | `300` | Auto-rotation interval (1/3/5/10/30 min) |
| `rotation.autoRotate` | boolean | — | `true` | Rotation timer enabled |
| `rotation.shieldEnabled` | boolean | — | `true` | Master chameleon shield (geolocation mock + rotation) |
| `server.mode` | string | `manual` \| `fastest` \| `random` | `manual` | Server selection strategy |
| `server.serverId` | string | fleet id (see below) | `"zurich"` | Active server when `mode` is `manual` |
| `server.pool` | string[] | fleet ids | `[]` | Rotation pool when `mode` is `random` (empty = whole fleet) |
| `protocol` | string | `smart` \| `wireguard-udp` \| `wireguard-tcp` \| `wireguard-tls` | `smart` | Tunnel protocol. `smart` = UDP → TCP → TLS fallback |
| `netshield` | string | `off` \| `malware` \| `ads-trackers` \| `adult` | `off` | DNS-level filtering profile |
| `killSwitch.enabled` | boolean | — | `true` | Block traffic when the tunnel drops |
| `killSwitch.lockdown` | boolean | — | `true` | Prompt user for Android always-on + lockdown |
| `splitTunneling.enabled` | boolean | — | `false` | Split tunneling on/off |
| `splitTunneling.mode` | string | `exclude` \| `include` | `exclude` | `exclude`: apps/IPs bypass the tunnel. `include`: only these use the tunnel |
| `splitTunneling.apps` | string[] | Android package names | `[]` | Per-app rules |
| `splitTunneling.ips` | string[] | CIDR or single IP | `[]` | Per-IP/CIDR rules |
| `dns.custom` | string[] | IPv4/IPv6 addresses | `[]` | Custom resolvers sent through the tunnel |
| `dns.lanBypass` | boolean | — | `true` | Add local-network routes outside the tunnel |
| `dns.ipv6` | string | `block` \| `route` | `block` | `block`: blackhole `::/0`. `route`: route IPv6 through the tunnel |

### Fleet ids

`zurich tokyo reykjavik newyork singapore berlin london paris amsterdam stockholm
oslo helsinki toronto sydney seoul dubai saopaulo vienna madrid rome dublin
warsaw prague bucharest taipei mumbai losangeles frankfurt lisbon athens
vancouver auckland capetown bangkok` (32 nodes)

## Connection profiles (dashboard-local)

Profiles are stored in the dashboard's `localStorage` and are **not** part of
the push payload; applying a profile copies its values into the live config
and then pushes that config. Profile record shape:

```json
{
  "id": "profile-<uuid>",
  "name": "Maximum Stealth",
  "createdAt": "2026-10-22T15:30:00.000Z",
  "config": { "server": {...}, "protocol": "wireguard-tls", "netshield": "ads-trackers",
              "killSwitch": {...}, "splitTunneling": {...}, "rotation": {...}, "dns": {...} }
}
```

`config` is a partial payload — only the `rotation`, `server`, `protocol`,
`netshield`, `killSwitch`, `splitTunneling`, and `dns` sections.

## Android consumer notes

- Parse and validate with the schema above; ignore unknown fields (forward
  compatibility for v2).
- `protocol: "smart"` → attempt WireGuard-UDP, then WireGuard-TCP, then
  WireGuard-TLS with per-attempt timeouts before reporting failure.
- `netshield` maps to the DNS filter level of the on-device filtering resolver.
- `killSwitch.lockdown: true` → show the always-on VPN + "block connections
  without VPN" setup guidance; `killSwitch.enabled` → `setBlocking(true)` on
  the `VpnService.Builder` regardless.
- `splitTunneling.mode: "exclude"` → `addDisallowedApplication()` per package
  and route exclusions per CIDR; `"include"` → `addAllowedApplication()`.
- This contract is **original work**. Do not introduce code copied from any
  third-party VPN client; reimplement behavior from this spec.
