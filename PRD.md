# Product Requirements Document (PRD): OmniRush Chameleon v3.0

## 1. Product Summary
OmniRush Chameleon v3.0 is a zero-footprint, multi-device stealth network and GPS emulator designed to eliminate digital tracking footprints across Windows laptops and Android smartphones.

---

## 2. Key Modules & Functional Requirements

### 2.1 Multi-Modal Motion Engine (Route Simulation)
- **Requirement**: Allow users to set an Origin and Destination (lat/lon or landmark) and travel between them realistically.
- **Modes**:
  - Foot (`1.4 m/s` ~ 5 km/h)
  - Bicycle (`5.0 m/s` ~ 18 km/h)
  - Vehicle (`16.7 m/s` ~ 60 km/h)
  - Flight (`140.0 m/s` ~ 500 km/h)
- **Anti-Cheat Heuristics**: Applies dynamic micro-jitter to prevent anti-fraud telemetry flagging.

### 2.2 Global Jurisdiction Pool (30+ Exit Nodes)
- **Requirement**: Break away from static 5-node rotations to an expansive global network of 30+ sovereign jurisdictions.
- **Randomization**: "True Random" pool mode avoids deterministic round-robin patterns.

### 2.3 Custom Cadence Selection
- **Requirement**: Configurable rotation timers (1 min, 3 min, 5 min, 10 min, 30 min).

### 2.4 QR Phone Sync
- **Requirement**: Generate real-time modal QR codes with encrypted cloud tunnel URLs so users can scan and connect with any smartphone camera.

### 2.5 5-Layer Privacy Fortress
- **Physical**: MAC Address Randomization via `netsh wlan`.
- **OS Identity**: Disable Windows MAID and diagnostic telemetry.
- **Network**: Zero-downtime failover Tor SOCKS/HTTP proxy.
- **Browser**: Canvas 2D, WebGL, and AudioContext fingerprint poisoning.
- **Android System**: Mock GPS override on `fused`, `gps`, and `network` providers with satellite constellation bundles.

### 2.3 Custom Cadence Selection
- **Requirement**: Configurable rotation timers (1 min, 3 min, 5 min, 10 min, 30 min).

### 2.4 QR Phone Sync
- **Requirement**: Generate real-time modal QR codes with encrypted cloud tunnel URLs so users can scan and connect with any smartphone camera.

### 2.5 5-Layer Privacy Fortress
- **Physical**: MAC Address Randomization via `netsh wlan`.
- **OS Identity**: Disable Windows MAID and diagnostic telemetry.
- **Network**: Zero-downtime failover Tor SOCKS/HTTP proxy.
- **Browser**: Canvas 2D, WebGL, and AudioContext fingerprint poisoning.
- **Android System**: Mock GPS override on `fused`, `gps`, and `network` providers with satellite constellation bundles.

