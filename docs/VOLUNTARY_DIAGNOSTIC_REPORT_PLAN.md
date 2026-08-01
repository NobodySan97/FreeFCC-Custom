# Voluntary DUML Diagnostic Report Plan

## Status

Documented for future implementation. Currently the app does not send diagnostic reports, and no server endpoint is deployed.

## Purpose

If FCC, Auto FCC, 4G, LED, GPS, or another DUML feature fails, the user can voluntarily tap **Send diagnostic report**.

To diagnose the root cause, precise hardware model information and real DUML commands with responses are required. Generic system reports without this data are rarely useful.

## What to Send

### Hardware Info

- FreeFCC Custom version;
- Controller model:
  - `Build.DEVICE`, e.g., `rc331` or `rc520`;
  - `Build.MODEL`;
  - Android/firmware version;
- Aircraft model:
  - Detected DJI model/product code, e.g., `WA530`;
  - User-friendly model name, if unambiguously known to the app.

The model should not be determined solely by an old local cache: the report must specify whether it was verified in the current session.

### Confirmed Model Detection Path

Live test with DJI Air 3S + RC Pro 2 (2026-07-26) demonstrated:

- The exact aircraft code arrives in CRC-valid DUML push `00:82` as ASCII `WA234`;
- The human-readable name arrives in `03:34` as `00 + ASCII "DJI Air 3S"`;
- Both frames flow `0xA2 → 0x82` via `40009` after DJI Fly starts;
- Controller model returns via read-only `00:01 VersionInquiry` on RC Pro 2 via `port=8901`, `sender=0x2A`, `dst=0x06`: hardware string `RC520`;
- Controller push `00:81/00:82` also independently contains `rc520`.

Full raw frames and negative routes are documented in [`AIR3S_RC_PRO2_LIVE_MAP.md`](AIR3S_RC_PRO2_LIVE_MAP.md). Production does not require a continuous listener: one short passive window after DJI Fly starts is sufficient, stopping immediately after identity is received. Coordinates and full factory serial numbers must never be saved from the surrounding stream.

### DUML Logs

- Last 100 DUML commands sent and received by the app;
- Outcome of the last FCC operation;
- Outcome of LED/GPS reads/writes;
- Error logs if an exception occurred.

### Privacy Safeguards

- Never include GPS coordinates, Home Point location, or flight logs.
- Never include Wi-Fi passwords, IP addresses, or personal accounts.
- Serial numbers are anonymized or hashed before sending.
