# Compatibility matrix — RESEARCHED

What "any VESC, any bridge, 1..N controllers" means in practice.
## Firmware
**Supported: 5.03 and newer**. One `GET_VALUES` layout (74 B), one `SETUP` layout (70 B), `hw_type` in `FW_VERSION`, `GET_BATTERY_CUT` always present.

| Firmware | App behaviour |
|---|---|
| < 5.03 | Refuse with "firmware too old, update in VESC Tool" (shows the reported version) |
| 5.03 … 7.x | Supported, fixtures per major line (5.03, 6.x, 7.x) |
| Newer / unknown fork (unknown `major.minor`, longer payloads) | Prefix-parse the known fields, banner "untested firmware", raw-frame capture offered |

Timeline facts: `GET_VALUES` identical from 5.03 to 7.01 dev; 6.00 only changed which sensors feed MOS temps on dual-MCU hardware; 7.00 added fault codes 30-33; the mcconf signature changes almost every release (why mcconf is deferred). Forks can use unrelated version numbers (e.g. 23.x); `fw_name` (6.00+) and `hw_crc` (6.05+) are the only fingerprints.

## Node types on CAN
| Node | `hw_type` | Treated as |
|---|---|---|
| Motor controller (bldc) | 0 | controller: polled |
| VESC BMS | 1 | ignored in v1 |
| VESC Express, IO board, other custom module | 2 | ignored; if it is the BLE bridge, the local node has no motor and all controllers are on CAN |

## Multi-controller setups
| Setup | How it appears | Confidence |
|---|---|---|
| Two or more separate boards on CAN (e.g. two single-motor boards) | one CAN ID each, all in `PING_CAN` except the local node | high |
| Single-MCU dual (VESC Duet, Stormcore 60D/100D, Unity, Luna) | motor 2 = `controller_id + 1`, answered locally; listed in `PING_CAN` when CAN mode is "VESC", otherwise reachable only by probing `id + 1` | high (verified in bldc) |
| Two-MCU "dual" boards (Flipsky dual FSESC, Ubox dual, Makerbase dual) | believed to be two IDs on an internal CAN bus | low (UNVERIFIED, needs a capture) |

CAN status broadcasts are off by default, so `GET_VALUES_SETUP` totals and `num_vescs` are hints; the app sums per-node values itself.

## BLE bridges
| Bridge | Service | MTU / notify payload | UART | Pairing | Quirks |
|---|---|---|---|---|---|
| NRF51822 `nrf51_vesc` (e.g. the Flipsky BLE module) | NUS | 23 fixed / 20 B; no DLE, no 2M PHY | 115200 | none (open) | NUS UUID only in the scan response; re-frames packets; asks for a 7.5-31.25 ms interval 5 s after connect; relaying can be disabled by the VESC (`COMM_EXT_NRF_SET_ENABLED`) |
| NRF52832/52840 `nrf52_vesc` | NUS | up to 247 / MTU-3; DLE 251 | 115200 | optional bond + passkey if a PIN was set | prefers 15-30 ms interval |
| VESC Express (ESP32) | NUS | request **517** (never 247) | none: sits on CAN | open by default; encrypted mode passkey | chunks notifications at MTU, not MTU-3: packets longer than MTU-3 are corrupted at MTU 24-257 |
| HM-10 / CC2541 clones | `FFE0`/`FFE1` | 23 / 20 B | often 9600 by default (must be 115200) | PIN `000000` | dumb pipe; unsupported by VESC Tool; best effort |
| Integrated / OEM modules | NUS or proprietary | measure | ? | ? | some notify on `…0002…`; detect, measure, report |

Detection cascade: NUS → `FFE0/FFE1` → first service with notify + write characteristics (user confirms). Never detect by name. Bluetooth Classic (SPP) is out of scope.

## E-bikes
- Speed: `GET_VALUES_SETUP.speed` (VESC-reported; on Luna BBSHD/M600 builds it comes from the wheel sensor, and a LispBM script can override it) or app ERPM.
- No PAS/cadence, torque or generic wheel-sensor command exists in the protocol. Throttle voltage (`GET_DECODED_ADC`) is not in the v1 allow-list.
