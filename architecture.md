# System Architecture Specification

```
┌────────────────────────────────────────────────────────┐
│                   Web Client UI                        │
│          (index.html, styles.css, app.js)              │
└──────────────────────────┬─────────────────────────────┘
                           │ 60s Timer / Manual Shift
                           ▼
┌────────────────────────────────────────────────────────┐
│               REA Chameleon Engine v6.3.0              │
│      (Proxy Routing, TLS Spoofing, DNS Leak Test)      │
└──────────────────────────┬─────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   [ Zurich CH ]     [ Tokyo JP ]     [ Reykjavik IS ]
   (Tor / AS59842)   (Proxy / AS2516) (Enclave / AS44556)
```

## Component Interaction
1. **Client Layer**: User interacts with the web interface or automated 1-minute rotation timer.
2. **Engine Layer**: `app.js` coordinates state updates, timer intervals, clipboard actions, and audit logging.
3. **Proxy Node Layer**: Simulates secure global endpoints with randomized IP addresses, GPS coordinates, and protocol handshakes.
