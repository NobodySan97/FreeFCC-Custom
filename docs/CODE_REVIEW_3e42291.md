# Code Review — Changes for 2026-07-19

**Range:** `a4e4305..3e42291` (7 commits)  
**Build:** All unit test classes passing.

## Context

The primary architectural shift was moving from a periodic keepalive loop to a **one-shot model**: the foreground service passively listens for Home Point (`03:44`) on port 40007 over a single long-lived connection, applies the full FCC profile once after the home location is saved, and then stops itself. `HomePointMonitor`, `Port40007Lock`, `LedReadback`, and `FccRuntime` were added, while `FccKeepaliveSchedule` was removed.

## Confirmed Findings

### 1. Race condition on one-shot completion overwrites incoming Auto-FCC request
- **File**: `FccKeepaliveService.kt:221-227`
- **Issue**: Worker cleanup ran outside `synchronized(Companion)`. If a user triggered `start()` during completion, `runRequested=true` was overwritten to false, losing the incoming request.

### 2. Failure retry removed
- **File**: `FccKeepaliveService.kt:234`
- **Issue**: Removing retry logic meant if `connect()` failed due to transient socket contention, the service stopped without retrying FCC.

### 3. Early service stops without `startForeground()` crash process on API 26+
- **File**: `FccKeepaliveService.kt:122-127, 132-143`
- **Issue**: Early return branches invoked `stopSelfResult()` without calling `startForeground()`, causing `RemoteServiceException`.

### 4. `exchangeWire` swallows mid-stream IOException
- **File**: `DumlTransport.kt:482-488`
- **Issue**: Mid-stream socket breaks returned success with truncated byte arrays instead of reporting read failures.
