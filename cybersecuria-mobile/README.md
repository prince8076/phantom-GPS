# CyberSecuria mobile application

This folder is the actual Android/iOS application project. It uses React Native with Expo and is separate from the earlier browser prototype.

## Start the app

From this folder:

```powershell
npm install
npm start
```

Then scan the Expo QR code with Expo Go, or press `a` for Android / `i` for iOS on a machine with the relevant simulator configured.

## Included mobile flow

- Home dashboard with security score, protected assets, priority incidents, and full scan action
- Threat protection center with link, QR-payload, and device-posture checks
- Email breach monitor with validation and privacy guidance
- Secure calling room and trusted contacts flow
- Cyber insurance policy summary, covered-scenario grid, and claim-intake draft
- Incidents, protection, breach monitor, and profile tabs
- Portrait-first layout designed for phone screens

The feature categories are based on public descriptions of consumer cybersecurity products. CyberSecuria uses original UI, copy, and local demo behavior; it does not copy CYBX source code, assets, or private implementation. Live breach intelligence, real QR/camera scanning, WebRTC calling, device telemetry, authentication, payments, and insurer APIs still require backend/native integrations.
