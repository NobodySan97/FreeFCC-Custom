# DJI RC2 Port and Stream Map

This document records the results of a live inspection of the DJI RC2 (`rc331`) and separates observed facts from interpretations. The inspection is not limited to 4G: the goal is to discover available network, DJI/DUML, diagnostic, and system channels on the controller.

## Evidence Levels

- **OBSERVED** — Directly obtained through scanning, capture, or service response.
- **DERIVED** — Unambiguously follows from observed data structures.
- **HYPOTHESIS** — Working explanation requiring further verification.
- **NEGATIVE** — Expected behavior or signature is absent in the verified corpus; this is not a claim about all versions and devices.

No unknown port received DJI commands during this session. Early TCP checks were limited to connection attempts without application payload. After identifying `5744` as a GNSS debug/command socket, even an empty connection to it is considered potentially intrusive and is no longer permitted. `8902` was read passively: the client sent nothing, and the endpoint published the stream on its own.

## Testbed

| Field | Value |
|---|---|
| Date | 2026-07-21 |
| Controller | DJI RC2, code `rc331` |
| DJI Fly | 1.21.4 |
| Aircraft | Avata 360, code `WA530` |
| FreeFCC Custom | 1.5.39 during external check |
| Network | Wi-Fi LAN, controller address `192.168.1.139` in this session |

## External RC2 TCP Ports

A full TCP connect scan (`1..65535`) was performed via Wi-Fi with low concurrency and no payload delivery.

| Port | Observation | Conclusion |
|---:|---|---|
| `53/tcp` | **OBSERVED:** DNS `version.bind TXT CHAOS` received `REFUSED`, flags `0x8185` | DNS service available, but version is hidden |
| `5037/tcp` | **OBSERVED:** ADB smart-socket requests `host:version`, `host:devices-l`, `host:features` returned `FAIL001ano devices/emulators found` | **DERIVED:** Endpoint understands ADB framing; usable ADB shell on RC2 not proven |
| `8787/tcp` | **OBSERVED:** FreeFCC Custom HTTP API | Application LAN bridge |
| `8902/tcp` | **OBSERVED:** Emits continuous binary stream immediately after connect without request | Passive multiplexed telemetry/diagnostic stream |

Known localhost ports `40007`, `40009`, `8901`, `8903`, `8904` are not externally exposed over Wi-Fi.

## Known Internal Application Routes

| Endpoint | Confirmed Role in Project | Constraints |
|---|---|---|
| `127.0.0.1:40007` | Wrapped telemetry/control, GPS and LED read/write, aircraft identity | Frequent new connections interrupted aircraft link; background polling prohibited |
| `127.0.0.1:40009` | Direct DUML broker, FCC/CE and passive frames | Not all requests receive matching response |
| `127.0.0.1:8901` | Identity frames observed on RC Pro 2 | Localhost capture needed for RC2 |
| `127.0.0.1:8902..8904` | Alternative DJI endpoints | External RC2 `8902` proven active stream |
| Abstract Unix `/duss/mb/0x205` | Endpoint accepts captured 4G-profile writes | Reachability does not prove physical 4G activation |

## Localhost Inventory (Proc Net)

| Port | Bind | UID | Output |
|---:|---|---:|---|
| `5037` | IPv6 wildcard | `0` | ADB-shaped system endpoint |
| `5744` | `127.0.0.1` | `1021` | **DERIVED:** Unicore uDriver GNSS debug/raw/command socket; do NOT connect |
| `8787` | Wi-Fi IPv4 RC2 | `10025` | FreeFCC Custom LAN API |
| `8901` | `127.0.0.1` | `0` | DJI internal endpoint |
| `8902` | IPv4 wildcard | `0` | Confirmed external passive high-rate stream |
| `40007` | `127.0.0.1` | `0` | DJI wrapped telemetry/control proxy |
| `40008` | `127.0.0.1` | `0` | **OBSERVED:** Previously unrecorded DJI/root listener |
| `40009` | `127.0.0.1` | `0` | DJI direct DUML broker |

## Summary

- **DERIVED:** RC2 publishes a multiplexed telemetry/diagnostic stream on `8902`.
- **OBSERVED:** `5037` understands ADB smart-socket protocol, but does not yield a target shell.
- **DERIVED:** `5744` is a dangerous GNSS debug/raw/command path; even an empty connect can displace the system client.
- **NEGATIVE:** Passive GNSS readiness does not equal a Home Point update trigger. Text-based events in DJI Fly remain the authoritative trigger for Home Point Auto FCC.
