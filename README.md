<div align="center">

# FreeFCC Custom

### Upgraded open-source FCC unlock for DJI smart controllers with a screen

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue?style=flat-square)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/NobodySan97/FreeFCC-Custom?style=flat-square)](https://github.com/NobodySan97/FreeFCC-Custom/releases)
[![Downloads](https://img.shields.io/github/downloads/NobodySan97/FreeFCC-Custom/total?style=flat-square&color=brightgreen)](https://github.com/NobodySan97/FreeFCC-Custom/releases)

A free, hardened, and open-source Android app that unlocks FCC mode (5.8 GHz & 2.4 GHz), sends experimental 4G activation frames, and queries device telemetry on DJI smart controllers with a screen (DJI RC, RC 2, RC Pro, RC Plus).

> ℹ️ **Fork & Upstream Attribution**:  
> This repository is a custom fork derived from and combining the discoveries of [doesthings/FreeFCC](https://github.com/doesthings/FreeFCC) and [danusha2345/SkylabFCCfree](https://github.com/danusha2345/SkylabFCCfree), deeply re-engineered for stability and performance.
>
> 🔹 **Foundation from `doesthings/FreeFCC`:**
> * Core DUML framing (`0x55` with CRC-8/CRC-16) and localhost TCP socket routing (`40009`/`40007`).
> * Event-driven Auto FCC via Android Accessibility on DJI Fly Home Point announcements.
> * Auxiliary LED (`03:F8`) and GPS master enable (`0xC5429582`) parameter controls.
> * Local Wi-Fi LAN diagnostic HTTP bridge.
>
> 🔹 **Integration from `danusha2345/SkylabFCCfree`:**
> * Radio region commit (`06:72`) and Wi-Fi channel group mappings (`07:30`, `07:18`, `07:19`) required for 5.8 GHz band-switching.
> * Targeted 4G service mode switch frame (`0x51:0x1A`) over Unix domain socket (`/duss/mb/0x205`).
> * Low-overhead 10-second send-only periodic keepalive approach.
>
> 🚀 **Exclusive Hardening & Enhancements in `NobodySan97/FreeFCC-Custom`:**
> * **Complete *CleanDesigner* UI Rework:** Modern Jetpack Compose interface with Glassmorphism, Material You theming, and a **Side Navigation Rail** designed specifically for landscape DJI controller screens (DJI RC, RC 2, RC Pro, RC Plus) with 52dp thumb/glove-friendly touch targets.
> * **Isolated Country Code Execution:** Pushes `07:30` in a dedicated socket session, ensuring the 5.8 GHz FCC profile engages immediately without command collisions.
> * **Multi-Tier Concurrency & Thread-Safety:** Atomic `HardwareLock` and `DumlPortSessionLock` architecture across the UI, floating button widget, and background services, eliminating socket interleaving and CRC errors.
> * **Optimized Quad-Core Performance:** Throttled accessibility scans with native memory handle recycling (`node.recycle()`) for seamless, lag-free operation alongside DJI Fly (<2% CPU load).
> * **Hardened Dual-CRC Telemetry & Serial Parsing:** Full CRC-8 and CRC-16 validation before packet extraction to eliminate false-framing sync hazards.
> * **100% Telemetry-Free Architecture:** Clean, zero-tracking, zero analytics, R8-optimized binaries, and automated SHA-256 release checksum generation.

</div>

---

> ## Disclaimer
>
> This software is provided for educational and research purposes only. Modifying radio transmission parameters may violate laws and regulations in your country or region. In most places, increasing radio power beyond what is legally permitted for your area requires authorization from the relevant regulatory authority.
>
> You are solely responsible for ensuring that your use of this software complies with all applicable local, regional, and national laws. The author of this project accepts no liability for any damage, legal consequences, or regulatory action arising from the use of this tool.
>
> This project is not affiliated with, endorsed by, or sponsored by DJI. Using this tool may void your warranty and DJI Care Refresh coverage.

---

## ⚡ Features

| Feature | Description |
|---------|-------------|
| **FCC Unlock** | Switches the radio from CE to FCC mode for higher power and more channels (dual-band 2.4 GHz & 5.8 GHz) |
| **Auto FCC (Home Point)** | Event-driven mode that re-applies FCC automatically whenever DJI Fly sets a new Home Point |
| **Auto FCC (10s Periodic)** | Low-overhead send-only keepalive that pushes FCC frames every 10 seconds without continuous readback |
| **4G Activation** | Sends targeted 4G service mode frames to the aircraft (`0x51:0x1A`, experimental) |
| **GPS & LED Control** | Direct toggle and verification of aircraft auxiliary LEDs and master GPS parameters |
| **Device Info & Telemetry** | Displays detected aircraft model name/code, controller code, and factory serial number |
| **Floating Action Button** | Interactive on-screen overlay widget for quick in-flight FCC status toggling |
| **In-App Auto-Updater** | Automatically checks for updates and installs the latest APK directly from GitHub Releases |
| **LAN Diagnostic API** | Optional local Wi-Fi HTTP bridge for live logs, diagnostics, and DUML inspection |
| **100% Free & Telemetry-Free** | No ads, no tracking, no paid activations, and fully open-source |

> **Note on altitude/distance/NFZ unlock:** The 120m CE altitude limit is enforced by the **DJI Fly app** via a C0 class runtime flag on every connection. DUML commands alone cannot bypass this limit—it requires modifying the DJI Fly app itself or flashing patched firmware.

---

## 📥 Download

| Download | Link |
|----------|------|
| **Latest Release (APK)** | [GitHub Releases](https://github.com/NobodySan97/FreeFCC-Custom/releases) |
| **Helper Apps Archive (zip)** | [freefcc.pages.dev/downloads/freefcc-helpers.zip](https://freefcc.pages.dev/downloads/freefcc-helpers.zip) |

---

## 🛠️ Quick Installation Guide (DJI RC / RC2)

No PC is required—installation is done directly on the controller via a microSD card.

1. **Format the microSD card in the RC2 first:** Insert the card into your controller, open Android Settings $\rightarrow$ Storage, and format it as portable storage.
2. **Download files:** Download the latest `FreeFCC-Custom.apk` and `freefcc-helpers.zip`. Extract the zip and place the APK inside the helper folder on your microSD card.
3. **Install system helpers:** In the controller file browser, install `01_PackageInstaller` and `02_FileManager`.
4. **Restart controller:** Hold the power button to reboot.
5. **Install Launcher & App:** Open `03_ATVLauncher`, navigate to Files, and install `FreeFCC-Custom.apk`.
6. **First Launch:** Open FreeFCC Custom once to allow background permissions and status notifications.

---

## 🚀 How to Use

1. Power on your drone and link it to the controller.
2. Open **FreeFCC Custom** and choose your preferred mode:
   - **Auto FCC — Home Point (Recommended):** Automatically applies FCC upon Home Point match. Enable *FreeFCC Custom Home Point Test* in Accessibility Settings when prompted.
   - **Auto FCC — every 10 sec:** Pushes FCC frames periodically in background via send-only bursts.
   - **Send FCC Request:** One-shot manual trigger.
3. Tap **Open DJI Fly** to launch the flight app.
4. Enjoy extended range and higher transmission power.

---

## 📊 How Do I Know If It Worked?

In the **DJI Fly app**, open **Settings $\rightarrow$ Transmission** and check the signal graph:

<table>
<tr>
<td align="center"><b>FCC Mode (Active)</b></td>
<td align="center"><b>CE Mode (Stock)</b></td>
</tr>
<tr>
<td><img src=".github/fcc.webp" alt="FCC mode"></td>
<td><img src=".github/ce.webp" alt="CE mode"></td>
</tr>
<tr>
<td align="center" style="color:#34D399"><b>Signal line extends far past the 1km mark</b></td>
<td align="center" style="color:#7A85A3">Signal line cuts off around the 1km mark</td>
</tr>
</table>

---

## 🛸 Compatibility

| Drone | Controller | FCC (2.4/5.8 GHz) | 4G Modem | Status |
|-------|-----------|-------------------|----------|--------|
| **DJI Mini 5 Pro** | RC2 | ✅ Working | N/A | Full FCC + LED |
| **DJI Mini 4 Pro** | RC2 | ✅ Working | N/A | Full FCC |
| **DJI Air 3 / Air 3S** | RC2 | ✅ Working | N/A | Full FCC |
| **DJI Mavic 3 / Mavic 4 Series** | RC Pro / RC Pro 2 | ✅ Working | Experimental | Full FCC |
| **DJI Neo 1 / Neo 2** | RC2 | ✅ Working | N/A | Full FCC |
| **DJI Avata 2 / Avata 360** | RC2 | ✅ Working | Experimental | Full FCC + LED |
| **DJI M30T / Matrice 350** | RC Plus | ✅ Working | Experimental | Full FCC |

---

## 📜 License & Credits

- **License:** AGPL-3.0 (See [LICENSE](LICENSE)).
- **Upstream Repositories:** [doesthings/FreeFCC](https://github.com/doesthings/FreeFCC) & [danusha2345/SkylabFCCfree](https://github.com/danusha2345/SkylabFCCfree).
- **DUML Protocol Reference:** [dji-firmware-tools](https://github.com/o-gs/dji-firmware-tools).
