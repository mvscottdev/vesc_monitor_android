# BLE connection — RESEARCHED

## Goal
Connect to **any VESC BLE bridge**, keep the session alive while the app is open or backgrounded, reconnect automatically, and explain every failure in plain words. Bridge table: [compatibility.md](../01-research/compatibility.md#ble-bridges). Wire protocol: [ble-protocol.md](../01-research/ble-protocol.md).

## Library and seam
- Nordic **Android-BLE-Library 2.11.x + `ble-ktx`** (`no.nordicsemi.android:ble`, `ble-ktx`), used only inside the adapter.
- Everything above it talks to our `BleTransport` interface: `connect()`, `disconnect()`, `notifications: Flow<ByteArray>`, `suspend write(bytes)`, `mtu`, `state`. Implementations: `NordicBleTransport`, `ReplayTransport`, `SyntheticTransport`. A later swap (Kotlin-BLE-Library 2.0 at GA, or raw GATT) touches one module.

## Scan and transport detection
- User scan: unfiltered, name + RSSI, "looks like VESC" hint. Reconnect scans filter by **remembered address**, never by the NUS UUID alone (NRF51/52 put it in the scan response only).
- Detection cascade: NUS → `FFE0/FFE1` → first service with a notify + write pair (user confirms). Never by device name. Enable notifications on **every** notifiable characteristic and accept data from all of them.
- One long-lived scan session; never more than 4 `startScan` calls per 30 s (Android silently throttles at 5).

## Connect sequence
1. Permissions granted, adapter on. Direct connect (`autoConnect=false`, LE transport; ~30 s timeout).
2. `requestConnectionPriority(HIGH)` immediately.
3. `requestMtu(517)` with a **2 s timeout that never blocks**; continue with whatever MTU results (NRF51 stays at 23). Never ask for 247 (VESC Express truncation window).
4. Discover services, run the cascade, enable CCCDs one at a time.
5. Probe `FW_VERSION` (2.5 s budget), run topology discovery ([vesc-topology.md](vesc-topology.md)), start polling. State becomes `connected` only after the first valid reply.
No PHY or DLE tuning: the limit is connection events, not air time.

## Polling scheduler
- Response-paced: the next request goes out when the previous reply is complete (or times out). **One request in flight per link** (v1). A depth-2 pipelining flag exists for the BLE bench only.
- Rotation over the N controllers on the link; each has a target rate `min(50 Hz, measured capacity / N)`.
- Hot: `GET_VALUES_SELECTIVE` fast mask per controller. Cold (~1 Hz): counters mask, `GET_VALUES_SETUP_SELECTIVE` on the primary controller (speed source `vesc`, odometer), `GET_BATTERY_CUT` once per connection.
- Safety re-poll after `max(4 × interval, 1 s)`; measured rate per controller (EWMA) is shown in diagnostics and logged.
- Planning numbers (model, not measured): NRF51 strict one-in-flight ≈ 33 Hz for 1 controller, ≈ 17 Hz each for 2; pessimistic 13 / 7 Hz. The BLE bench replaces these with measurements.

## Reconnect
- First connect direct; after a link loss use `autoConnect=true` (keeps working screen-off, where scanning may stop on OnePlus/Xiaomi). A filtered scan by address is the fallback.
- **Stale watchdog:** connected but no valid frame for 4 s → rebuild the link.
- Backoff: 0.5 s steps up to 5 s for ~12 attempts, then 30 s; never the slow tier while the app is visible. Unbounded while the session runs.
- On any error status: `disconnect()` + `close()`, wait ≥ 200 ms, then retry. Ignore callbacks from a stale `BluetoothGatt`. No hidden `refresh()` unless discovery finds no UART service.
- Bonded bridges (NRF52 with PIN, Express encrypted): a pairing dialog is allowed; "insufficient authentication" means "needs pairing".

## Connection state and errors
`idle | scanning | connecting | connected | reconnecting | lost`, each with a `reason`:

| Symptom | Message |
|---|---|
| Remembered module not found in 10-15 s / direct connect 133 after ~30 s | "Module not found. Is it powered? Close VESC Tool or any other app connected to it." |
| Drops within seconds with status 19, twice | "Connection taken by another device." |
| Connected but no reply to `FW_VERSION` | "Module connected but the VESC is not answering: check the UART app, 115200 baud, RX/TX wiring, and that the VESC has not disabled the module." |
| Repeated CRC errors | "Link errors" with counters |
| No UART-like service | "Not a VESC bridge?" + manual characteristic pick |
| Firmware < 5.03 | "Firmware too old: update in VESC Tool" |
| Bluetooth off / permission denied | deep link to settings (`CONNECT` = cannot connect, `SCAN` = cannot search) |
| Pairing dismissed / bond lost | "Pairing needed" + retry |
Busy-by-another-app can only be inferred, so it is always worded as a hint.

## Foreground service
- Manifest: `foregroundServiceType="connectedDevice|location"`, permissions `FOREGROUND_SERVICE`, `_CONNECTED_DEVICE`, `_LOCATION`. Never `dataSync` (6 h cap).
- Started only from the visible Activity, after `BLUETOOTH_CONNECT` is granted. Runtime type `connectedDevice`; add `location` only when GPS turns on **while the app is visible** (re-call `startForeground`).
- `onStartCommand` returns `START_NOT_STICKY` (no self-restart). `stopWithTask=false`: `onTaskRemoved` finishes the ride save within 3-5 s, then stops. This is best effort: a Task Manager stop or an OEM kill gives no callback, so rides are checkpointed every second and recovered on the next start ([ride-logging.md](ride-logging.md)); `ApplicationExitInfo` tells the user why.
- Persistent notification: state + key values, updated on state change or every 10 s.

## Permissions (API 31+)
`BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS` at first connect. `ACCESS_FINE_LOCATION` only when the user turns on a GPS feature, never background location.

## Keep-alive onboarding
Ask for the battery-optimisation exemption, then show OEM steps: OnePlus first (disable deep optimisation, lock in recents, re-check each start), Samsung, Xiaomi/HyperOS. After a system kill (detected with `ApplicationExitInfo`), show the guide again.

## BLE bench (first slice, hidden screen)
Logs negotiated MTU, per-request write time, notification count and spacing per reply, achieved Hz for strict vs depth-2, full vs selective mask, direct vs CAN-forwarded, screen on vs off. The results set the poll defaults. Static bench on a stand is enough.

## Data flow
`BleTransport` → reassembler → codec → parsers → derivations → (a) ride recorder, (b) alert engine, (c) coalesced emit to the UI. See [architecture](../03-architecture/overview.md).

## Built so far (background)
- Connect screen (as the connect mockup): "Connect your vehicle" with a short promise, then a NEARBY list with a Bluetooth tile, name, whether it advertises a UART service, signal bars and dBm; the hint to close VESC Tool first. The rail on the left reaches the other screens.
- Run in background screen (Connect → Settings → Run in background): live checks for nearby-devices permission, notifications and battery optimisation, re-run when the rider returns from system settings, with buttons to the app's settings and the system battery-optimisation list, plus the usual extra power-saver steps for the phone's maker (OnePlus/OPPO/realme, Samsung, Xiaomi, Huawei/Honor, vivo; menu names vary).
- Automatic reconnect (BLE only): once a session has reached `connected`, a loss for a recoverable reason (link lost, module not found, VESC not answering, link errors, taken by another device, Bluetooth off) goes to `reconnecting` and retries with the backoff above, unbounded until the rider disconnects. Permissions, pairing, "not a bridge" and old firmware stop at `lost`. A first connect that fails is never retried. The open ride keeps recording across the gap; the dashboard shows a non-blocking "Connection lost · retrying" banner with the attempt count, and the notification shows the attempt.
- Connection failed card (connect mockup): when a session ends on an error, Connect shows what happened, what to do and one button that helps (Try again on the same bridge, Bluetooth settings, app settings), plus Choose another device, with the raw reason code small for bug reports.
- After a reconnect the open ride continues; if discovery finds a different set of controllers it closes that ride and starts a new one.
- Stale watchdog (BLE only): polling but no valid reply from any controller for 4 s rebuilds the link ("VESC not answering", then the retries above).
- The app tells the service when it is on screen; the 30 s tier is used only off screen.
- Not yet: `autoConnect=true` on retries (retries use the same direct connect).
- Ongoing notification (settings mockup): speed, charge and power on the big line; source, rate and trip below; the ride timer (system chronometer) with "REC" while a ride records. Buttons: Stop recording (same as the Ride logging switch) and Disconnect. Both act on the app only. Text follows the speed unit setting.
- Setup steps on Connect (connect-detect mockup): Connected (packet size), Firmware (hardware), Looking for more controllers (found so far), Live data, updated as discovery runs; once live, a FOUND ON THIS VEHICLE list shows each controller (local or CAN id, firmware, rate). Tap a controller's name there to call it e.g. Front or Rear; the name is saved on the phone for that set of controllers and shows on the dashboard cards. Other CAN nodes that answer (BMS, custom modules) are listed greyed as "not a motor controller, skipped" and never polled.
