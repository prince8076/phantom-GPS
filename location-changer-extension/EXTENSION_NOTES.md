# OmniRush Chameleon — Extension Hardening Notes (v2.3, "Browser Bodyguard + NetShield")

## What changed in v2.3

Adds a **NetShield** layer on top of v2.2's Leak Protection, using
`chrome.declarativeNetRequest` static rulesets:

1. **Block ads & trackers** (`rules_ads.json`, 91 rules) — blocks requests to
   major ad networks, ad exchanges, analytics, session-replay and data-broker
   domains before they load. Side effect the user asked for: pages load
   faster with less to download.
2. **Block malware & unwanted software** (`rules_malware.json`, 12 rules) —
   blocks known cryptominers and widely-documented adware/bundler distribution
   domains. This is a best-effort browser supplement; real-time phishing feeds
   run at the DNS level via NetShield on the VPN tunnel (server resolvers).
3. **Per-category toggles + live counters** in the popup — block counts are
   tracked via `onRuleMatchedDebug` (best-effort) and resettable.

New permissions: `declarativeNetRequest`, `declarativeNetRequestFeedback`
(expect another Chrome install/update warning). Rulesets are toggleable at
runtime via `updateEnabledRulesets` — no reload needed.

Draft build — **not yet pushed** to `prince8076/phantom-GPS`. All code in this
folder is original, written clean-room. Nothing was copied from any VPN vendor.

## What changed in v2.2

The extension keeps everything it already did (canvas noise, WebGL GPU
spoofing, AudioContext jitter, hardware normalization, geolocation override,
5-minute epoch rotation, dashboard sync) and adds a **Leak Protection** layer
run by a new MV3 background service worker (`background.js`):

1. **WebRTC IP leak prevention** — sets
   `chrome.privacy.network.webRTCIPHandlingPolicy` (default:
   `disable_non_proxied_udp`; selectable: balanced `default_public_interface_only`
   or off). This is the same control Chrome's own WebRTC Network Limiter uses.
2. **Speculative-connection blocking** — forces
   `chrome.privacy.network.networkPredictionEnabled` to `false`, so Chrome does
   not speculatively resolve DNS / preconnect to the ISP resolver behind the
   proxy's back.
3. **Local proxy routing (PAC)** — optional toggle routes browser traffic
   through the Smart Proxy Engine at `127.0.0.1:8899` using an inline PAC
   script. Loopback, plain hostnames and RFC-1918/link-local ranges bypass
   (LAN bypass, mirroring VPN LAN-bypass). **Fail closed:** there is
   intentionally no `DIRECT` fallback for public hosts, so traffic cannot
   silently leak if the engine is down. The popup probes the engine and warns
   when it is offline.
4. **Secure DNS guidance** — Chrome exposes no extension API for Secure DNS,
   so the popup explains the manual step and offers a one-tap copy of
   `chrome://settings/security`.

## Permission rationale

| Permission | Why it is needed |
|---|---|
| `storage` | Persists leak-protection settings and existing state (pre-existing). |
| `privacy` | **New.** Required to read/write `webRTCIPHandlingPolicy` and `networkPredictionEnabled`. |
| `proxy` | **New.** Required to install/clear the PAC routing via `chrome.proxy.settings`. |
| `host_permissions: <all_urls>` | Pre-existing: content script runs on all pages; also covers the `127.0.0.1:3300/api/status` sync calls. |

Adding `privacy` and `proxy` will make Chrome show install/update warnings
("change your privacy-related settings", "read and manage proxy settings").
That is expected and honest — the toggles do exactly what they say.

## Chrome version requirements

- Manifest V3 service workers: Chrome 88+
- `chrome.privacy.network.webRTCIPHandlingPolicy`: Chrome 48+
- `chrome.privacy.network.networkPredictionEnabled`: long-standing (no concern)
- `chrome.proxy` with `pac_script` mode: Chrome 54+ for the mode enum; the
  `data:` PAC form used here is supported in all MV3-era releases
- `isInNet` / `isPlainHostName` in PAC: standard PAC builtins, available in Chrome

## What this extension deliberately does NOT do

- **No real VPN tunnel.** A browser extension cannot create a system VPN or
  capture non-browser traffic. Full-tunnel protection belongs in the Android
  app's `VpnService` (separate build track).
- **No Secure DNS enforcement.** Chrome provides no extension API for the
  "Use secure DNS" setting; the extension guides the user instead of
  pretending otherwise.
- **No `chrome://` navigation.** Extensions are blocked from opening
  `chrome://settings/*`, hence the copy-link button rather than a deep link.
- **No DNS-over-proxy claims for the HTTP engine.** The Smart Proxy Engine is
  an HTTP proxy (`PROXY 127.0.0.1:8899` in PAC); DNS for proxied requests is
  still resolved by Chrome unless Secure DNS (DoH) is enabled — which is
  exactly why the Secure DNS card exists.
- **No data collection.** Settings stay in `chrome.storage.local`. The only
  network calls are the pre-existing `127.0.0.1:3300/api/status` sync polls.
- **No copied code.** Clean-room implementation; concepts only (WebRTC policy
  enum, PAC semantics) taken from public Chrome developer documentation.

## Behavior notes & edge cases

- Settings are applied on install, on browser startup, and on every change.
  If another extension or an enterprise policy controls a setting, the
  background worker refuses to overwrite it and the popup explains who owns
  it (`levelOfControl` check).
- While the service worker is awake it re-asserts the WebRTC policy if an
  external change is detected (no extra permissions needed).
- The 5-minute epoch math in `content.js` and `popup.js` is unchanged; the
  popup still prefers the dashboard API and falls back to local epoch math
  when the dashboard is unreachable — same as before.
- `content.js` is **unchanged** from the repo version (carried over verbatim).

## Manual test checklist

1. Load unpacked from `chrome://extensions` (Developer mode). Confirm no
   manifest errors.
2. Open popup: Leak Protection section renders; WebRTC select defaults to
   Maximum; speculative toggle ON; proxy toggle OFF.
3. Toggle proxy ON with the dashboard running: visit an IP-check site and
   confirm egress via the proxy engine. Stop the dashboard: confirm the
   offline warning appears and public sites fail closed.
4. Toggle proxy ON with another proxy extension installed: confirm the
   "managed by …" warning appears.
5. WebRTC: set Maximum, run a WebRTC leak test (e.g. browserleaks.com/webrtc)
   — no non-proxied UDP candidates / no real IP.
6. DevTools → Network: with speculative blocking ON, confirm no DNS prefetch
   (`x-dns-prefetch-control` / preconnect) initiations.
7. Secure DNS: copy-link button copies `chrome://settings/security`.

## Files in this draft

- `manifest.json` — MV3, adds `privacy` + `proxy` permissions, `background.js`
- `background.js` — **new** service worker (settings store + apply logic)
- `popup.html` / `popup.js` — **updated** with Leak Protection section
- `content.js` — unchanged from repo
- `EXTENSION_NOTES.md` — this file
