# Live Hardware Map: DJI Air 3S + RC Pro 2 (`rc520`)

Test Date: 2026-07-26. Hardware: DJI Air 3S (`WA234`) + RC Pro 2 (`rc520`), FreeFCC Custom 1.5.45.

## Confirmed Results

- Aircraft product code `WA234` is reported via DUML push `00:82` (`0xA2 -> 0x82` on port 40009).
- User string `DJI Air 3S` is reported via `03:34`.
- Controller model string `RC520` is reported via VersionInquiry `00:01` on port 8901 (`0x2A -> 0x06`).
