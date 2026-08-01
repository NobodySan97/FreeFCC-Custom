# Drone-Hacks Mobile 1.0.1 APK Analysis

Date: 2026-07-24.

Analysis of Drone-Hacks Mobile v1.0.1 Tauri/Rust Android application (`release-22baaacc.apk`).

## Findings

- One-Shot FCC mode requests `cmdSet`, `cmdId`, and `payload` from an external API and sends a single logical DUML command over the local Android USB bridge.
- The raw command byte payloads are not statically embedded in the client APK.
