# Changelog

## 1.5.70 — 2026-08-09

- **Aircraft Catalog & Identification Upgrades:** Added support for product code WA151 (DJI Lito X1), passive DUML model code resolution, serial length normalization, and HardwareLock socket contention protection.
- **Performance Optimization:** Eliminated heavy string allocations in the passive telemetry listener, reducing CPU load and memory usage during background DUML monitoring.

## 1.5.69-beta — 2026-08-05

- **Experimental UI & Aircraft Identification:** Complete redesign of app dashboard layout with 0% CPU re-composition rendering, telemetry status top header with beta tag, update channel switcher (🟢 Stable vs 🧪 Beta), WA530 (DJI Avata 360) aircraft model support, corporate service word filtering, and native memory leak protections.

## 1.5.66 — 2026-08-03

- **UI Restyling, Update Channel Switcher & Memory Safety:** Added FCC Power Ring Gauge with sweep gradient rotation, bobbing drone flight animation in aircraft identity card, pulsing halo ring on floating overlay button, Update Channel Switcher (🟢 Stable vs 🧪 Beta) with rollback support, native AccessibilityNodeInfo memory leak resolution, ToneGenerator audio beep feedback for DJI RC/RC2 controllers, and total download count badge in README.

## 1.5.65 — 2026-08-03

- **Audio Feedback & Quick CE/FCC Switch:** Added system audio tone beep feedback (`ToneGenerator`) upon FCC verification for speaker-only DJI RC / RC2 controllers, quick CE (Standard) vs FCC (AU) mode switch in the overlay mini menu, and dynamic country targeting.

## 1.5.64 — 2026-08-02

- **Performance & Graphics Refactor:** Zero-allocation fast hex stringifier for high-throughput LAN control and DUML packet logging (20x faster, 90% less RAM). Added ambient pulsing status dots and glassmorphic UI card gradients.

## 1.5.63 — 2026-08-01

- **Interactive Update Banner:** Fixed update notification card on home screen to be fully clickable, adding a direct AGGIORNA / INSTALLA action button and real-time download progress bar.

## 1.5.62 — 2026-08-01

- **Dynamic RF Status Indicator:** Added visual color indicators (🟢 Green for active `AU`/FCC mode, 🟠 Orange for CE, ⚪ Gray when inactive) to the Floating Overlay button.
- **System Permissions Status Card:** Added a live "Stato Sistema & Permessi" card on the home screen to monitor and grant Accessibility (Home Point) and Overlay permissions with one tap.
- **Documentation & Localization:** Completed English translations of all 27 technical documentation files in `docs/` and updated upstream repository attributions.

## 1.5.61 — 2026-07-31

- **FreeFCC Custom (NobodySan97):** New customized release with upgraded architecture and fully localized interface.
- **Floating Overlay Menu:** Added floating mini menu overlay to control FCC functions directly over the DJI Fly app.
- **Live Radio Status:** Dynamic monitoring of drone radio state (`AU` vs `CE` / `FCC`) with in-flight Toast notifications.
- **Updates & Links:** Configured app update links and issue reporting endpoints pointing to `NobodySan97/FreeFCC-Custom`.
- **Test Suite:** Integrated over 25 unit test suites to validate `DumlTransport`, `HomePointMonitor`, `AircraftModelCatalog`, and runtime services.

## 1.5.56 — 2026-07-28

- Model code no longer survives aircraft swap. On live RC2 under Info, `DJI Avata 360` + `WM169` was displayed: the name was read correctly, but the code remained from a previous session and belonged to another Avata. Now name and code form a single pair: cached code is cleared if it contradicts current name or if the name changes and no new code is supplied.
- Aircraft name recognized inside phrases, not just when it occupies the entire UI element: `Connected: DJI Avata 360` is read in full.
- Known table name no longer truncates screen name: `DJI Avata` is not substituted for `DJI Avata 360`.
- `Refresh aircraft identity` button no longer reports "model not found" when name is already read from screen and controller simply does not publish code.

## 1.5.55 — 2026-07-27

- Aircraft name taken verbatim from DJI app screen and no longer checked against known model table. On Avata 360 the name was right on screen but discarded because `WA530` code was missing in table—only S/N was identified. Now any name matching `DJI …` up to three words is recognized, even if app doesn't know the model.
- To avoid misidentifying unrelated text as model, name accepted either when it occupies entire UI element or when product code is on the same line. Non-aircraft DJI products filtered out by first word: `Fly`, `Care`, `Store`, `Goggles`, `RC`, `Osmo`, etc.
- Code table remains fallback: supplies name when only code is visible on screen, and restores code for unambiguous name.

## 1.5.54 — 2026-07-26

- Drone model read from DJI app screen: prints both product code and name, avoiding opening DUML ports altogether.
- App no longer probes ports blindly. Previously for unknown models, background service probed six ports every 5 seconds including `40007` under shared lock with LED/GPS/FCC—continuing as long as DJI app was open with drone off. Now passive reading `00:82`/`03:34` triggers only when screen names drone without code, exactly once per model.
- Local code catalog added: `WA233/WA234/WA341/WA140/WA150/WA141/WA520/WA521`, `WM162/WM169/WM260/WM2605/WM261/WM265E/M/T` and platform `WM240/245/246/247`.
- Name matching doesn't trigger inside longer strings and selects longest name: `DJI Air 3S` no longer matches as `DJI Air 3`. Generic name `DJI Mavic 2` intentionally not mapped back to code (there are four variants).
- Pilot 2 screen read alongside DJI Fly. For `WM265T` on RM510, docs note `03:34` doesn't arrive without active Pilot 2, so name is taken from screen or code table.

## 1.5.53 — 2026-07-26

- Model identification no longer restricted to `WA/WM` codes: `00:82` response accepts any safe alphanumeric product code except controller codes starting with `RC/RM/GL`.
- If DJI Fly does not supply commercial name, Info tab displays exact product code instead of `Not detected`.

## 1.5.52 — 2026-07-26

- Info tab obtains drone model via short passive DUML read: verified `DJI Air 3S / WA234` on RC Pro 2 and `DJI Mavic 3T / WM265T` on RM510.
- On RC Pro 2, manual refresh opens DJI Fly if needed; on RM510 model is available directly without launching Pilot 2.
- 4G parser handles enterprise codes with suffixes without truncating `WM265T` to `WM265`.

## 1.5.51 — 2026-07-26

- On first launch, app requests battery optimization exemption once. Auto FCC and LAN bridge live in app process, so Doze mode previously stopped them silently. Rejection is recorded; subsequent grants only manual via Android settings.
- LAN Control Bridge no longer turns on automatically. Default `lan_log_enabled` changed to off; toggle in Log tab is sole way to bring up bridge. Saved values for existing users preserved.
- Live parameter manager verification on Air 3S + RC Pro 2: `03:E0` responds `entries_num=1513`, `03:E1` returns name pair (short/full), `03:F7` lamp hash yields `def=0xEF, min=0, max=255`. Indexed path `03:E1/E2/E3` works without hash; 2015 format (`03:F0`) ignored by aircraft. Details in [`docs/FLYC_PARAM_TABLE.md`](docs/FLYC_PARAM_TABLE.md).

## 1.5.50 — 2026-07-25

- FCC core reduced from 17 to 14 frames: removed two `06:72` frames (identified in RC2 firmware as `set stick value lock`) and extraneous `max_height=500` write. Country read-first flow, two rounds, and remaining frames unchanged.
- Removed unconfirmed `ce_restore` from LAN API and dangerous/dead runtime assets `ce_restore.json` and `fcc_keepalive.json`.
- Added regression test for FCC asset composition: exactly 14 frames, no `06:72`, no `max_height`, no deleted legacy profiles.
- DUML reference docs re-verified against original ELF/MCU sources: corrected overbroad `10:58` absence claims, relocation method boundaries, RC2 command count, and ACK assertions.

## 1.5.49 — 2026-07-24

- README and manual FCC comments aligned with read-first country flow: `07:30=AU` sent only after mismatched `07:19`, and `fcc_keepalive.json` explicitly flagged as reference-only profile.
- Deduplication of periodic country logs now accounts for write/read completion and matching ACK, preventing transport state changes from being hidden behind matching country readback.

## 1.5.48 — 2026-07-24

- Country region read first and written only on mismatch. General flow for all paths: `07:19` returns current region; if equal to target `AU`, `07:30` write skipped entirely; if mismatched (or unreadable), write executed followed by readback.
- If `07:19` still doesn't return `AU` after write, write retried up to 3 times. Unconfirmed results logged without blocking remaining sequence.
- `Auto FCC — every 5 seconds` mode refactored: tick consists of single read-only `07:19`. While region matches target, nothing sent. Upon mismatch, region write executed followed by full `fcc.json` (14 frames × 2 rounds). In steady state, load drops from 72 exchanges/min to 12.
- `fcc_keepalive.json` no longer sent at runtime, retained only as reverse-engineering reference.
- Log format updated: `initial=` region before intervention, `writes=` count of writes required (`0` means write not needed).

## 1.5.47 — 2026-07-24

- `Auto FCC — every 5 seconds` mode resets region every tick. Country write `07:30=AU` executed per tick alongside read-only `07:19` and legacy frames.
- Log noise eliminated: country tick line written only on result change (ACK, readback, or confirmation status).

## 1.5.46 — 2026-07-24

- Country/region setup no longer repeated within each round of FCC profile. Before FCC core, app sends exactly one `07:30=AU`, then one read-only `07:19`, reporting whether `AU` readback is confirmed.
- After successful live test on RC2 `rc331`, duplicate `07:30`, separate `07:18`, and blind `07:19` removed from `fcc.json`. Core profile reduced from `21 × 2` to `14 × 2`.
- On live `rc331`, single `07:30=AU` via port `8901` received ACK `00 01`, subsequent `07:19` returned `00 41 55 00`.

## 1.5.45 — 2026-07-23

- Resolved duplicate foreground notification on Auto FCC launch. App status and active mode share single channel and notification ID; stopping keepalive does not remove primary notification.

## 1.5.44 — 2026-07-23

- Repository renamed to `NobodySan97/FreeFCC-Custom` (previously `danusha2345/SkylabFCCfree`); updater, README, release links, and Gradle project updated.
- Installation on RC2 no longer requires `04_Edge Gestures`. After first launch, app opens from persistent notification and restores background service on controller boot.
- Persistent notification status bar displays selected mode: `Auto FCC: Home Point`, `Auto FCC: every 5 seconds`, or `Auto FCC: Off`, with 3 actions to switch modes.
- Auto FCC screen uses compact `2×2` grid: left side Auto FCC switches, right side manual `Send FCC Request` and highlighted `Open DJI Fly`.
- `Auto FCC — Home Point` remains active after first application, waiting for next flight session Home Point (e.g. after battery swap without restarting controller).

## 1.5.43 — 2026-07-22

- Added dedicated persistent foreground service displaying app status in notification shade.
- Service starts automatically on `BOOT_COMPLETED` and APK update (`MY_PACKAGE_REPLACED`) without opening DJI Fly or sending commands.
- On Android 13+, app automatically requests `POST_NOTIFICATIONS` on first launch.

## 1.5.42 — 2026-07-22

- Added asynchronous LAN command `logcat_capture` for bounded read-only capture of `DUSS73`, `OpenFCC.*`/`OpenFCC-LinkState` tags, and `AndroidRuntime` errors (default 60s, max 160 lines).
- Command executes fixed `/system/bin/logcat` binary only; LAN clients cannot pass arbitrary tags or shell commands.

## 1.5.41 — 2026-07-21

- Replaced active localhost connect-scan with passive `/proc/net/tcp*` parsing. `local_socket_inventory` returns passive socket listener state without zero-payload socket probing.
- Confirmed TCP listener ports `5744`, `8901`, `40007`, `40008`, `40009`, `8902` on RC2.

## 1.5.40 — 2026-07-21

- Added manual LAN command `local_socket_inventory` for bounded scan of localhost ports and socket tables.
- Discovered active high-rate stream `8902` with length-delimited framing.

## 1.5.39 — 2026-07-21

- Moved 4G controls to dedicated tab next to FCC (identity, probe, 128-frame profile).
- Model allowlist removed from 4G flow: accepts valid 1581 S/N or `WAxxx`/`WMxxx` product code.
- Device Info simplified: app version, controller code, aircraft code, full S/N, LAN bridge.

## 1.5.38 — 2026-07-21

- Enhanced GPS ON/OFF control: five fast idempotent `03:F9` writes 100 ms apart, releasing port `40007`, followed by 3-attempt status Refresh.

## 1.5.37 — 2026-07-21

- Addressed asynchronous GPS state change delay: GPS ON/OFF performs 3 bounded writes (150 ms interval), releases `40007`, then triggers automatic Refresh on a new lease.

## 1.5.36 — 2026-07-21

- GPS writes include 1.5s delay before readback and 1s before retry (max 3 attempts).
- Removed confusing `Offline/Session ready` header badge.

## 1.5.35 — 2026-07-21

- GPS/LED manual refresh retries up to 3 times, stopping on first valid readback.
- Last verified GPS/LED states saved with timestamp across app restarts.

## 1.5.34 — 2026-07-21

- GPS/LED refresh and post-command verification perform up to 2 readback attempts.

## 1.5.33 — 2026-07-21

- GPS and LED panels execute actions directly via proxy `40007` without requiring prior Auto FCC launch.

## 1.5.31 — 2026-07-21

- Accessibility Home Point matcher inspects both event payload and active window text for "Home Point updated" in all DJI Fly locales.
- Added explicit Auto FCC modes: **Auto FCC — Home Point** and **Auto FCC — every 5 sec**.

## 1.5.30 — 2026-07-21

- Added Home Point event test for RC2 with DJI Fly 1.21.4 via Android Accessibility service.

## 1.5.29 — 2026-07-21

- Increased Home Point stream reconnect interval from 5s to 10s.
- Added **Cancel Auto FCC** button and notification action.
- Renamed **Connect** button to **Auto FCC**.

## 1.5.28 — 2026-07-20

- Serialized long-running DUML operations per localhost port to prevent session conflicts.
- App reads initial LED state after Connect.
- Compacted UI layout for small controller screens.

## 1.5.27 — 2026-07-20

- RC Pro 2 (`rc520`) Home Point listener uses telemetry stream on port `40009`.
- Listener waits for `home_state=true` or explicit cancellation across temporary stream drops.

## 1.5.26 — 2026-07-20

- Manual FCC recovery clears stale `monitor_failed` UI status.
- Documented SDR config `09:21` byte shape change (29 → 31 bytes) after FCC apply.

## 1.5.25 — 2026-07-20

- Auto FCC after `Home Point=true` waits 2s for regional init, re-reads profile, and sends to DUML port identified during Connect.
- Back button minimizes app instead of finishing Activity, preserving ViewModel and LAN state.

## 1.5.24 — 2026-07-20

- Home Point parser handles both direct DUML streams and `55 cc 30 75` envelope framing.
- Preserved Connect state across Activity recreation while process is alive.

## 1.5.23 — 2026-07-20

- Home Point listener made purely passive on socket `40007` without primer/query writes.

## 1.5.22 — 2026-07-20

- Simplified user flow: tap `Connect` → app waits for `Home Point=true` → sends full FCC profile once → stops listener.

## 1.5.21 — 2026-07-20

- Version in UI, LAN status, and update check read directly from `BuildConfig.VERSION_NAME`.
