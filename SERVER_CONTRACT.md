# Server Contract — phantom-GPS with user's own server

The three clients are built; the user provides the VPN server infrastructure.
This contract defines what the server (or the desktop hub proxying it) must
expose so the clients can reach full client-side parity.

All endpoints are JSON. Auth: `Authorization: Bearer <token>` unless the
deployment is local/trusted (desktop hub on localhost).

## 1. Server list

`GET /api/servers`

Response:
```json
{
  "ok": true,
  "servers": [
    {
      "id": "de-frankfurt-01",
      "country": "DE",
      "city": "Frankfurt",
      "name": "DE #1",
      "load": 42,
      "latencyMs": 38,
      "protocols": ["wireguard-udp", "wireguard-tcp", "wireguard-tls"],
      "endpoints": {
        "wireguard-udp": { "host": "203.0.113.10", "port": 51820, "publicKey": "<base64>" },
        "wireguard-tcp": { "host": "203.0.113.10", "port": 443, "publicKey": "<base64>" },
        "wireguard-tls": { "host": "203.0.113.10", "port": 443, "publicKey": "<base64>", "sni": "example.com" }
      },
      "streaming": false,
      "secureCore": null
    }
  ]
}
```

Notes:
- `load` is 0–100. `latencyMs` may be null if unknown.
- For Secure Core-style multi-hop later: `"secureCore": { "entry": "<server-id>", "exit": "<server-id>" }`.
- The desktop hub may serve this from a local file the user edits, or proxy
  the user's real API. Either way the shape stays the same.

## 2. Credentials (short-lived)

Client generates its own WireGuard keypair and sends the public key.
The server assigns an address and returns everything the client needs.

`POST /api/vpn/credentials`

Request:
```json
{ "publicKey": "<client wg public key, base64>", "serverId": "de-frankfurt-01", "protocol": "wireguard-udp" }
```

Response:
```json
{
  "ok": true,
  "clientAddress": "10.8.0.2/32",
  "dns": ["10.8.0.1"],
  "serverPublicKey": "<base64>",
  "endpoint": "203.0.113.10:51820",
  "expiresAt": "2026-10-23T22:00:00Z"
}
```

`POST /api/vpn/credentials/refresh` — same shape; called before `expiresAt`.

## 3. NetShield-style DNS filtering

`GET /api/netshield/resolvers?level=ads_malware`

Response:
```json
{ "ok": true, "level": "ads_malware", "resolvers": ["10.8.0.1"] }
```

Levels: `off`, `malware`, `ads_malware`, `ads_malware_adult`.
The user must supply real filtering resolver addresses; the clients currently
carry placeholders.

## 4. Control hub (already implemented)

- `POST /api/vpn-config` — dashboard pushes the full client config.
- `GET /api/vpn/config` — Android fetches the latest pushed config.
- `GET /api/status` — includes `vpnConfigUpdatedAt`.

The hub is the glue: it can serve `/api/servers` from the user's fleet file
and proxy `/api/vpn/credentials` to the user's credential service, so the
Android app only ever talks to the hub.

## 5. What the user must provide

1. Real WireGuard servers with public keys and open ports per protocol.
2. A credential-issuing endpoint (or a static config for testing: one
   server, one client address, one keypair).
3. Filtering DNS resolver addresses for NetShield levels.
4. (Later) TCP/TLS ingress if Smart fallback beyond UDP is wanted.

## 6. Minimal test setup (no full backend)

For a first end-to-end test the user can hardcode:
- One server entry in the hub's fleet file.
- One static credential response (client keypair generated once).
- Plain DNS (NetShield off).

The Android consumer to be built: fetch hub config → pick server →
fetch/refresh credentials → build `VpnConfig` → start `PhantomVpnService`.
