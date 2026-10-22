# Database & Data Model Specification

## Entities

### 1. ProxyNode
- `id` (string, PK): Unique node identifier (e.g., `zurich`, `tokyo`)
- `name` (string): Human-readable location (e.g., `Zurich, Switzerland`)
- `ip` (string): Virtual IPv4/IPv6 address (e.g., `185.220.101.7`)
- `coords` (string): Latitude and Longitude
- `isp` (string): ISP / ASN description (e.g., `Tor Exit Node · AS59842`)

### 2. RotationLog
- `id` (uuid, PK): Log entry identifier
- `timestamp` (datetime): Exact time of rotation
- `virtual_ip` (string): Applied IP address
- `location_name` (string): Target location
- `latitude` (float): Spoofed latitude
- `longitude` (float): Spoofed longitude
- `protocol` (string): Tunnel protocol (Tor, WireGuard, OpenVPN, Warp)
- `status` (string): `Active`, `Rotated`, or `Terminated`
