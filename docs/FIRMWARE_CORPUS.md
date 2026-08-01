# Local Firmware Corpus & Evidence Map

Documentation Date: 2026-07-24.

This document sets the boundary for reproducible static analysis in FreeFCC Custom. The corpus contains only firmware and application binaries previously stored locally.

## Major Corpora

| Platform | Location | Available Artifacts | Proven Findings |
|---|---|---|---|
| DJI RC / RM510 | Local scratch directory | `dji_wlm`, `dji_link`, `libduml_frwk.so`, `libwlm.so` | DUML routing, `09:EC`, `51:14` |
| DJI RC Pro 2 / RC520 | Local scratch directory | Android OTA v139/v400/v440/v576, extracted system/vendor | Route `0xEE`, table `0x51`, `51:1A` semantics |
| WM260 | Local scratch directory | `system_2.img`, `vendor_2.img`, `dji_perception`, `dji_sys` | Route `10:58` -> `bvision:0` |
| WA530 V01.00.0300 | Local scratch directory | Android 9 OTA, system/vendor, SquashFS | Aircraft-side `09:EC` routing |
| Drone-Hacks Mobile 1.0.1 | Local scratch directory | Tauri/Rust APK, Android USB bridge | One-Shot FCC requests payload via private API |
