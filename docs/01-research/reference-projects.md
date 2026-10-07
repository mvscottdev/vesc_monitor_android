# Reference projects — RESEARCHED

Pinned commits read on 2026-09-29. Porting GPL-3 code is allowed; every ported file gets a line `// @source <repo>@<sha7> <path>:<lines> (<license>)`.

| Project | Commit | Stack / license | Use it for |
|---|---|---|---|
| [vedderb/bldc](https://github.com/vedderb/bldc) | `4fd8279` | C firmware, GPL-3 | Ground truth: reply layouts, CAN forwarding, fault codes |
| [vedderb/vesc_tool](https://github.com/vedderb/vesc_tool) | `dc53c65` | Qt/C++, GPL-3 | Client spec: framing (`packet.cpp`), tolerant parsers (`commands.cpp`), 20-byte no-response writes, CAN/hwType recipe |
| [vedderb/nrf51_vesc](https://github.com/vedderb/nrf51_vesc), [nrf52_vesc](https://github.com/vedderb/nrf52_vesc), [vesc_express](https://github.com/vedderb/vesc_express) | `3fb3b27`, `954429b`, `c085911` | bridge firmware, GPL-3 | What the BLE bridges do (MTU, UART, pairing, quirks) |
| [vescape-app/vescape](https://github.com/vescape-app/vescape) | `224f7e5` | Expo 57 / RN 0.86.3, Kotlin FGS, GPL-3 | Architecture precedent: service-owned session, poll loop, reconnect, replay transport, UI attach pattern, agent docs |
| [RuggeroCadamuroITA/VescViewer](https://github.com/RuggeroCadamuroITA/VescViewer) | `ce0259d` | Kotlin/Compose, MIT | Transport profile detection (NUS/HM-10/any), write chunking, sanity gate |
| [FreeSK8/freesk8_mobile](https://github.com/FreeSK8/freesk8_mobile) | `5796c0b` | Flutter, GPL-3 | Firmware-matrix ideas; `GET_VALUES_SETUP` for multi-ESC |
| [gowrav/vesc-ble-can](https://github.com/gowrav/vesc-ble-can) | `35884c3` | Python, MIT | Brute-force CAN discovery fallback; desk test tool for a real bridge |
| [NordicSemiconductor/Android-BLE-Library](https://github.com/NordicSemiconductor/Android-BLE-Library) | `4a86e6c` | Java/Kotlin, BSD-3 | Our BLE library (2.11.x) |

Not useful: Metr, Floaty, Float Control (closed / Refloat), Esk8 (no BLE code).

## Facts that correct earlier assumptions
- vescape targets Refloat and never sends `GET_VALUES`: there is no universal parser to port, only scaffolding.
- vescape's "~35 Hz" is a peak on a VESC Express; its committed real ride capture averages **14.7 Hz**. It stores a **2 Hz** trace (its docs say otherwise; trust code over docs).
- VescViewer's distance (`tach_abs / 3`) is wrong: the divisor is `3 × poles × gear` (verified in bldc).

## Port or write
| Component | Decision |
|---|---|
| Codec, CRC, reassembler | Write fresh (ring buffer, 512 cap, drop-one-byte resync, 100 ms silence reset); take VescViewer's 10 reassembler tests + vescape's split-frame test as vectors |
| GET_VALUES / SETUP / FW_VERSION parsers | Write fresh, table-driven, as vesc_tool does |
| CAN discovery | Port the shape of vescape's detector, write the content (ping → FW_VERSION per id → hwType) |
| Poll loop | Port vescape `PollingLoop.kt`, generalised to N targets |
| GATT client details | Port vescape's hygiene ideas + VescViewer's chunking and endpoint fallback; behind the Nordic library |
| Reconnect policy, scheduler seam | Port vescape (`ReconnectPolicy`, `runtime/Scheduler`) |
| Stale watchdog | Write fresh (~40 lines) from vescape's idea |
| Foreground service shell | Adapt vescape (type computation, refused-promotion handling, notification gate) |
| Replay transport + JSONL recorder | Port the format (`meta`, `session-state`, `ble-chunk {direction, base64}`, `t` ms), adapt the code |
| Alert engine | Write fresh (capability-driven) |
| Storage | Do not port (own design, [ride-logging.md](../02-features/ride-logging.md)) |
| JS hot path | Adapt vescape (shared values + 1 Hz store publish + snapshot pull) |

## Mistakes to avoid (seen in the reference apps)
God classes on the main looper (vescape 3,809-line session controller, FreeSK8 3,193-line widget); docs drifting from code; parsing by exact length and falling back to floats; picking the first CAN ID that answers; one UI event per frame; no framer silence reset; BLE lifetime not owned by a service.
