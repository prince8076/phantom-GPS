# VPN Core — Licensing Note

The `vpn/` package in `android-app` is designed to integrate with the
WireGuard tunnel library:

- `com.wireguard.android:tunnel` (wireguard-android) — **GPL-2.0**

## What this means

Shipping that dependency inside the APK makes the **entire application**
subject to GPL-2.0 copyleft. In practice:

1. Anyone who receives the APK is entitled to the complete corresponding
   source code of the app (including your own code linked into it).
2. You must preserve copyright/license notices and make the source
   available to recipients (e.g. a public repo link in the app or on
   request — "written offer" mechanics apply to physical distribution).
3. You may not add further restrictions (no "all rights reserved"
   proprietary licensing on the shipped binary).

This was a deliberate choice (accepted 2026-10-22) because the
wireguard-android `GoBackend` is the most battle-tested WireGuard
integration on Android.

## The alternative (no copyleft)

The `WgTunnelBackend` interface in `vpn/` is a clean seam. A
boringtun-based backend (Cloudflare, **MIT licensed**) can be plugged in
instead — same `VpnConfig`, same `PhantomVpnService`, no GPL obligations.
That path needs more integration work (JNI/FFI wiring for boringtun)
but keeps the app proprietary-friendly.

## Clean-room reminder

The `vpn/` code here is original work inspired by studying ProtonVPN's
client (GPL-3.0) with REA. None of ProtonVPN's or WireGuard's source was
copied into this package. If you later reference either project's code
directly, re-check the license implications above.
