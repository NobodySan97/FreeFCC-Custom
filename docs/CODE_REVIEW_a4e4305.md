# Code Review — Commit a4e4305 ("fix: harden FCC lifecycle and add DUML capture")

Date: 2026-07-19.  
Scope: `git diff HEAD~1` (9 files: DumlTransport.kt, FccKeepaliveService.kt, FccViewModel.kt, LanControl.kt, tests, docs).

## Confirmed Findings

### 1. `sendAndReceiveRaw` holds HardwareLock for full timeout
- **File**: `app/src/main/java/com/freefcc/app/DumlTransport.kt:343`
- **Issue**: The consume-until-match loop replaced fast failure with reading frames until `readWindowMs` expires. `handleLanDuml` holds `beginHardwareOp()` across the entire call.

### 2. Stale `fcc_sequence_written` flag gives false `fcc_enabled`
- **File**: `app/src/main/java/com/freefcc/app/FccViewModel.kt:271`
- **Issue**: Flag was only cleared on `disableFcc()`. Disconnection or restarts did not clear it.

### 3. `handleLanDumlCapture` without HardwareLock blocks LAN pool
- **File**: `app/src/main/java/com/freefcc/app/FccViewModel.kt:1484`
- **Issue**: Capture ran directly into `transport.captureFrames` without a concurrency guard.

### 4. `captureFrames`: busy-spin CPU usage after EOF
- **File**: `app/src/main/java/com/freefcc/app/DumlTransport.kt:422`
- **Issue**: After socket closure, `read()` returns -1 repeatedly without backoff.
