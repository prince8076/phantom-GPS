# Security Architecture & Threat Mitigation

## 1. DNS Leak Prevention
All name resolution queries are proxied directly through encrypted tunnels associated with the active node (e.g., Zurich Tor Exit / Cloudflare Warp), preventing local ISP logging.

## 2. TLS Fingerprint Randomization
The REA engine injects randomized JA3/JA4 TLS client Hello fingerprints on each rotation cycle, making automated bot detection and deep packet inspection (DPI) ineffective.

## 3. Geolocation Spoofing
Navigator geolocation APIs and IP-based geolocation headers (`X-Forwarded-For`, `Client-IP`) are mocked dynamically to align with the selected proxy coordinates.
