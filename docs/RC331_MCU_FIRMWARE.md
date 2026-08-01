# RC2 (`rc331`) MCU Firmware: Unencrypted Image & `06:72` Command Semantics

Analysis Date: 2026-07-24.

This document records the first **unencrypted** MCU firmware image in the corpus for the DJI RC2 (`rc331`), resolving the long-standing question regarding command `06:72`—the final opaque legacy keepalive command previously directed to `rc:0` (the external RC MCU over UART `/dev/ttyHS2`).

Main Finding:

> **`06:72` = `set stick value lock`, `06:74` = `get stick value lock`.**  
> This manages stick/control hardware input locking on the remote controller, NOT the RF region. The legacy label "RADIO set region to FCC (01)" / "RADIO commit region change" is **refuted** by the firmware disassembly.

No commands were sent to physical hardware during this static analysis.

## Artifacts and Integrity

| File | Size | SHA-256 |
|---|---:|---|
| `V10.00.0700_rc331_dji_system.bin` | 1,465,948,160 | `1778183d5a742bbacd77567bf7b33ec9d6927c0edc963437da684767e401d058` |
| `rc331_0600_v10.06.00.50_20251103.pro.fw.sig` | 108,640 | `9e7b970bb373827494c2390769841632756390acd42c46e7ceca478ec74b236e` |
| Extracted `0600` payload | 108,136 | `f5ee13337efe61a8e60801a0e21275745a9bcb44277ef45617421471f836beea` |

The outer container is a POSIX tar archive containing four modules: `0200` (Android), `0205`, **`0600` (108 KB, MCU)**, `1400`.

IMaH header for module `0600`:
- `enc_key=` is empty: signed, but NOT encrypted.

## RC MCU DUML Command Table

At `0x08005350`–`0x080054a8`, a 12-byte dispatch table maps DUML handlers:

- **`06:72`** -> **`set stick value lock` (`CONFIRMED`)**
- **`06:74`** -> **`get stick value lock` (`CONFIRMED`)**

## `06:72` — `set stick value lock`

Disassembly confirms the payload structure: six 1-byte channel values (`ch0..ch5`) plus an optional 7th byte (`ch5f`):

```text
payload[0] = ch0   payload[3] = ch3
payload[1] = ch1   payload[4] = ch4
payload[2] = ch2   payload[5] = ch5
payload[6] = ch5f  (read when payload_len >= 7)
```

The string in `06:74` (`0x080117b0`):  
`get stick value lock: ch0%d ch1%d ch2%d ch3%d ch4%d ch5%d, id%d, index%d`

## Impact on FreeFCC Custom

The historical `06:72` frames modified control channel locking, not the RF transmission region. FreeFCC Custom does not send `06:72` frames.
