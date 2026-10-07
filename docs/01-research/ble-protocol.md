# VESC BLE protocol — RESEARCHED

Verified against bldc `4fd8279` (FW 7.01 dev), vesc_tool `dc53c65`, nrf51_vesc `3fb3b27`. Supported firmware: **5.03 and newer**; see [compatibility.md](compatibility.md).

## Transport
- Nordic UART Service (NUS) `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`; RX `…0002…` (phone writes; write and write-without-response), TX `…0003…` (notify). Fallbacks and quirks per bridge: [compatibility.md § Bridges](compatibility.md#ble-bridges).
- **Writes:** cut every framed packet into **20-byte chunks**, write-without-response when the characteristic supports it (vesc_tool does the same; works on every bridge including MTU 23).
- **Reads:** enable notifications on **every** notifiable characteristic of the detected profile (some modules notify on `…0002…`). All notifications feed one stream reassembler: a packet can span notifications and one notification can hold several packets.
- One central at a time. A bridge stops advertising while connected.
- NRF51/NRF52 bridges decode and re-frame every packet: a corrupted packet dies at the bridge, a partial packet expires after 100 ms.

## Framing
```
short: 0x02 | len (1 B)    | payload | crc16 (2 B, big-endian) | 0x03   payload 1..255
long:  0x03 | len (2 B BE) | payload | crc16                   | 0x03   payload 256..512
```
- CRC-16/XMODEM (poly 0x1021, init 0x0000, no reflection) over the payload only. Check value for `"123456789"` = `0x31C3`.
- Payload byte 0 = command ID. Max payload 512 (firmware `PACKET_MAX_PL_LEN`). Reject: 0x02 with len 0, 0x03 with len < 256, len > 512. The 0x04 (3-byte length) frame is never accepted by firmware and is not supported.
- Resync: on a bad frame drop one byte and search for the next 0x02/0x03 (firmware does the same).

Test vectors (golden fixtures):

| Request | Frame bytes |
|---|---|
| FW_VERSION `[0]` | `02 01 00 00 00 03` |
| GET_VALUES `[4]` | `02 01 04 40 84 03` |
| GET_VALUES_SETUP `[47]` | `02 01 2F D5 8D 03` |
| PING_CAN `[62]` | `02 01 3E D7 9D 03` |
| FORWARD_CAN id 1 + GET_VALUES `[34,1,4]` | `02 03 22 01 04 9B 13 03` |
| GET_VALUES_SELECTIVE mask `0x000281CF` | `02 05 32 00 02 81 CF 26 07 03` |

## Requests and responses
- **No request ID.** A response carries the request's command ID. Replies to `FORWARD_CAN` come back bare, without the CAN ID.
- Therefore: **one request in flight per link** (v1 default). Match by command ID, plus `controller_id` for GET_VALUES/SETUP (every mask includes bit 17) or the echoed mask for SELECTIVE. Drop anything that doesn't match. Depth-2 pipelining is a flagged experiment for the BLE bench only.
- Timeouts: 1 s per request, 5 s for `PING_CAN`. One retry, then the node is marked stale.

## Read-only allow-list (`CommandGuard`)
| ID | Command | Request | Use |
|---|---|---|---|
| 0 | FW_VERSION | — | firmware, hardware name, `hw_type` |
| 4 | GET_VALUES | — | full telemetry (low rate) |
| 34 | FORWARD_CAN | `[canId, innerCmd, …]` | reach controllers on CAN |
| 47 | GET_VALUES_SETUP | — | VESC-computed speed, battery level, odometer |
| 50 | GET_VALUES_SELECTIVE | `u32 mask` | fast telemetry |
| 51 | GET_VALUES_SETUP_SELECTIVE | `u32 mask` | fast SETUP subset |
| 62 | PING_CAN | — | discovery |
| 115 | GET_BATTERY_CUT | — | `l_battery_cut_start/end` |

Guard rules, each with a unit test: (1) ID in the list, else throw; (2) request payload length matches the command; (3) `FORWARD_CAN`: inner command must be allow-listed, no nested `FORWARD_CAN`, CAN ID 255 (broadcast) denied. Everything else is denied, including `ALIVE` (30), `TERMINAL_CMD` (20), BMS/IMU/stats reads, and `GET_MCCONF`/`GET_APPCONF` (14/17, deferred with mcconf).

Side effects of allowed reads: `GET_VALUES` resets the firmware's current-averaging accumulators, so another app polling the same VESC shortens our averaging window. `PING_CAN` blocks the VESC for up to ~2.5 s; never send it while polling.

## `GET_VALUES` (74 B payload incl. command ID)
Big-endian; scaled integers (`value = raw / scale`). Bit = SELECTIVE mask bit.

| Bit | Field | Type | Scale | Bit | Field | Type | Scale |
|---|---|---|---|---|---|---|---|
| 0 | temp_mos °C | i16 | 10 | 11 | watt_hours | i32 | 1e4 |
| 1 | temp_motor °C | i16 | 10 | 12 | watt_hours_charged | i32 | 1e4 |
| 2 | current_motor A | i32 | 100 | 13 | tachometer | i32 | 1 |
| 3 | current_in A | i32 | 100 | 14 | tachometer_abs | i32 | 1 |
| 4 | id A | i32 | 100 | 15 | fault_code | u8 | enum |
| 5 | iq A | i32 | 100 | 16 | pid_pos | i32 | 1e6 |
| 6 | duty | i16 | 1000 | 17 | controller_id | u8 | — |
| 7 | ERPM | i32 | 1 | 18 | temp_mos_1/2/3 | 3×i16 | 10 |
| 8 | v_in V | i16 | 10 | 19, 20 | vd, vq V | i32 | 1000 |
| 9 | amp_hours | i32 | 1e4 | 21 | status (bit0 timeout, bit1 kill switch) | u8 | — |
| 10 | amp_hours_charged | i32 | 1e4 | | | | |

- **Parse** by walking the mask in bit order (full `GET_VALUES` = all bits) with a remaining-bytes guard per field, as vesc_tool does. 74 B = known layout; longer = parse the known prefix and flag "untested firmware"; a field cut short = reject the frame.
- `current_motor`/`current_in` are averages since the previous read. Tachometer: 6 counts per electrical revolution; `distance_m = tach · π · wheel_d / (3 · poles · gear)`.
- **Polling masks:** fast `0x000281CF` (bits 0,1,2,3,6,7,8,15,17: temps, currents, duty, ERPM, v_in, fault, id; 27 B payload, 2 notifications on NRF51); slow ~1 Hz `0x00027E00` (bits 9-14 + 17: Ah/Wh/tach counters). The echoed mask must equal the request.

## `GET_VALUES_SETUP` (70 B payload)
| Bit | Field | Type / scale | Note |
|---|---|---|---|
| 0, 1 | temp_mos, temp_motor | i16 /10 | |
| 2, 3 | current_motor, current_in | i32 /100 | **sum** over this VESC + CAN peers that broadcast status (off by default) |
| 4, 5 | duty, ERPM | i16 /1000, i32 | local |
| 6 | **speed** m/s | i32 /1000 | from this node's ERPM and its `si_motor_poles`, `si_gear_ratio`, `si_wheel_diameter` (or a hardware wheel sensor / LispBM override) |
| 7 | v_in | i16 /10 | |
| 8 | battery_level 0..1 | i16 /1000 | VESC's own simple curve; cross-check only |
| 9-12 | Ah, Ah charged, Wh, Wh charged (totals) | i32 /1e4 | same peer rule as bit 2 |
| 13, 14 | distance, distance_abs m | i32 /1000 | since boot |
| 15 | pid_pos | i32 /1e6 | |
| 16 | fault_code | u8 | |
| 17 | controller_id | u8 | |
| 18 | num_vescs | u8 | only peers that broadcast status → hint only |
| 19 | battery Wh left | i32 /1000 | |
| 20 | odometer m | u32 | persistent (mechanism UNVERIFIED) |
| 21 | uptime ms | u32 | |

"VESC config looks valid" (speed source `auto`): with |ERPM| > 200, `k = speed / ERPM` must differ from the factory-default constant `1.24e-4` m/s per ERPM (14 poles, gear 3, 0.083 m wheel) and give a plausible wheel diameter. Shown as a confidence, never as certainty.

## `FW_VERSION` response
`major i8, minor i8, hw_name cstring, uuid 12 B, pairing_done u8, test_version u8, hw_type u8, custom_config_num u8, has_phase_filters u8, qml_hw u8, qml_app u8, nrf_flags u8`, then `fw_name cstring` (6.00+) and `hw_crc u32` (6.05+). Read each optional field only while bytes remain. `hw_type`: 0 motor controller, 1 VESC BMS, 2 custom module (VESC Express, IO board). The UUID identifies the controller: zero it in committed fixtures.

## CAN
- `FORWARD_CAN [34, canId, inner…]`: the local VESC relays the inner command and returns the peer's reply as an ordinary packet. No reply if the ID is absent (the timeout is the only signal). The reply route is one global per VESC: another master (display, VESC Tool on USB) forwarding at the same time can steal replies.
- `PING_CAN` returns bare IDs (no `hw_type`); the local node is not in its own list. It works only when the local node's CAN mode is "VESC"; otherwise it returns nothing.
- Discovery: [vesc-topology.md](../02-features/vesc-topology.md).

## `GET_BATTERY_CUT`
Reply `[115, cut_start i32 /1e3, cut_end i32 /1e3]` (9 bytes), volts.

## Fault codes (`mc_fault_code`, append-only)
| Val | Name | Val | Name | Val | Name |
|---|---|---|---|---|---|
| 0 | NONE | 12 | ENCODER_SINCOS_BELOW_MIN | 24 | FLASH_CORRUPTION_MC_CFG |
| 1 | OVER_VOLTAGE | 13 | ENCODER_SINCOS_ABOVE_MAX | 25 | ENCODER_NO_MAGNET |
| 2 | UNDER_VOLTAGE | 14 | FLASH_CORRUPTION | 26 | ENCODER_MAGNET_TOO_STRONG |
| 3 | DRV (gate driver) | 15-17 | HIGH_OFFSET_CURRENT_SENSOR_1/2/3 | 27 | PHASE_FILTER (6.00) |
| 4 | ABS_OVER_CURRENT | 18 | UNBALANCED_CURRENTS | 28 | ENCODER_FAULT (6.00) |
| 5 | OVER_TEMP_FET | 19 | BRK | 29 | LV_OUTPUT_FAULT (6.00) |
| 6 | OVER_TEMP_MOTOR | 20-22 | RESOLVER_LOT/DOS/LOS | 30 | ENCODER_SLIP (7.00) |
| 7, 8 | GATE_DRIVER_OVER/UNDER_VOLTAGE | 23 | FLASH_CORRUPTION_APP_CFG | 31-33 | OVERSPEED, UNDERSPEED, ABS_OVERSPEED (7.00) |
| 9 | MCU_UNDER_VOLTAGE | 10 | BOOTING_FROM_WATCHDOG_RESET | 11 | ENCODER_SPI |

The firmware clears `fault_code` about 500 ms after the fault ends, so the app **latches** faults (timestamp, code, controller, count). Unknown codes show as `FAULT_<n>` and still alert.

## Not used in v1
mcconf/appconf (signature-dependent offsets), BMS values, IMU, stats, decoded ADC (throttle), PAS (no command exists).
