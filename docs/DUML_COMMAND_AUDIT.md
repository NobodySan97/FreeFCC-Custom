# FreeFCC Custom DUML Command Audit

Audit Date: 2026-07-23; updated 2026-07-24.

This document answers two core questions:
1. Which DUML commands FreeFCC Custom actually sends;
2. What is proven about each command versus historical hypothesis.

## Evidence Levels

| Level | Meaning |
|---|---|
| `CONFIRMED` | Semantics confirmed by firmware code/symbols and matching wire format or reproducible readback |
| `OBSERVED` | Frame or state change directly observed on live hardware |
| `INFERRED` | Purpose follows from context or function name, but payload is not fully decoded |
| `UNKNOWN` | Only `cmd_set`, `cmd_id`, route, and raw payload are known |

Writing to a local proxy or DUSS socket proves only that bytes were passed to the local service. It is not an ACK from the aircraft.

## Country Setup and FCC Core

Since 2026-07-24, country configuration is separated from the repeatable core:

1. One `07:30=AU` to `dst=0x09`;
2. One read-only `07:19` to the same destination;
3. 14 core frames from `fcc.json` sent in two rounds (28 core writes).

## Dangerous Legacy Commands Removed

- `ce_restore.json` contained stick-lock commands (`06:72`) directed to invalid destination `0x20`. Removed in v1.5.50.
- `fcc_keepalive.json` was superseded by dynamic country check (`FccCountryRegion.kt`). Removed in v1.5.50.
