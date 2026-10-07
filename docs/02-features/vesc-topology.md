# VESC topology (1..N controllers) — RESEARCHED

## Goal
Work with any VESC setup: one controller (common), dual, or N controllers on CAN, behind any BLE bridge.
## Detection (once per connection, and on user request)
Protocol details: [ble-protocol.md § CAN](../01-research/ble-protocol.md#can); node types: [compatibility.md](../01-research/compatibility.md).
1. `FW_VERSION` to the local node → firmware (refuse < 5.03), hardware name, UUID, `hw_type`. `hw_type != 0` (e.g. VESC Express bridge) → the local node has no motor; all controllers are on CAN.
2. Local `controller_id` from one `GET_VALUES` (bit 17). The local node is never in its own PING list.
3. `PING_CAN` (5 s timeout, never while polling) → CAN IDs.
4. `FORWARD_CAN(id, FW_VERSION)` per ID → keep `hw_type == 0` as controllers; ignore BMS (1) and custom modules (2).
5. If the local node is a single-MCU dual or unknown hardware and PING did not list `controller_id + 1`, probe it with `FORWARD_CAN(id + 1, FW_VERSION)`.
6. If PING returns nothing or times out (CAN mode not "VESC"), offer a scan of IDs 1-50 (200 ms each) and manual add-by-ID.
7. Confirm each controller with one valid `GET_VALUES`. Store the topology per device: `[{canId or local, hwType, fw, uuid}]`. The runtime never re-probes; the user can rescan, rename controllers (single → "Motor", two on a scooter → "Front"/"Rear") and exclude IDs.

## Polling
- One request in flight per link; rotation over the controllers (local direct, others via `FORWARD_CAN`). Scheduler details: [ble-connection.md § Polling scheduler](ble-connection.md#polling-scheduler).
- Replies are matched by command ID + `controller_id` (bit 17 is in every mask); mismatches are dropped.
- Each sample: `controllerId` (`local` | CAN ID), monotonic timestamp (arrival − RTT/2).

## Freshness
A controller is **fresh** if its last sample is ≤ max(1 s, 5 missed polls) old. Stale controllers are excluded from combined values (never counted as zero); the UI shows "k/N" and marks the value `PARTIAL`. After 1 timeout + 1 retry a controller is stale; it rejoins on the next valid reply.

## Combined values (main view)
| Metric | Rule |
|---|---|
| Voltage | N = 1: itself. N = 2: mean if within max(0.5 V, 1 %), else the lower + divergence flag. N ≥ 3: median. Optional per-controller rest offset (learned at |I| < 0.5 A for 5 s, used if ≤ 0.5 V) |
| Battery current | sum of signed `current_in` |
| Motor current | sum of signed motor current; per motor kept |
| Power | Σ Vᵢ × Iᵢ (own voltage) |
| Temps | max per type over controllers with the sensor `PRESENT` |
| Duty | max of |duty| |
| Fault | any non-zero, tagged with the controller, latched |
| Speed | [speed.md](speed.md#combining-controllers) |
| Ah / Wh / distance | sum of reset-safe **deltas** ([power.md](power.md)), never raw counters |

`GET_VALUES_SETUP` totals and `num_vescs` only include controllers that broadcast CAN status (off by default), so they are hints, not sources.

With N = 1, "combined" = that controller; the UI hides per-controller selectors.

## Edge cases
Controllers added/removed between sessions → rescan, keep names by CAN ID + UUID. Another master on the bus (display, VESC Tool on USB) can steal forwarded replies → timeouts rise; show a hint.
