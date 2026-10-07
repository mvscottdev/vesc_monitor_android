# Alerts — RESEARCHED

Fault codes: [ble-protocol.md](../01-research/ble-protocol.md#fault-codes-mc_fault_code-append-only).

## Engine
- Evaluated in the **native service**, so alerts work in the background. Outputs: sound, vibration, heads-up notification, on-screen banner. No TTS. Alerts are logged into the ride (`alert_event`).
- Each alert: `enabled`, `threshold`, `hysteresis`, `dwell` (must hold this long), `cooldown`, `severity` (info / warning / critical). Fires when dwell is met; clears past `threshold ∓ hysteresis`; escalation ignores cooldown.
- An alert is **available only when its inputs are `PRESENT`** (e.g. no motor temp sensor → no motor temp alert, [temperature.md](temperature.md)); unavailable alerts are hidden with the reason.
- Faults latch in the app (the VESC clears `fault_code` ~500 ms after the fault ends): timestamp, code, controller, count.
- Background sound/vibration from the service on Android 15-17 still needs a device check (open research item).

## Defaults
No mcconf in v1: limits that live in mcconf use the **bldc factory defaults** and are user-editable per controller.

| Alert | Default trigger | Hyst. | Dwell | Cooldown | Severity |
|---|---|---|---|---|---|
| FET temp approaching / derating / critical | 76 / 85 / 92.5 °C | 3 °C | 2 / 1 / 0.5 s | 60 / 60 / 30 s | info / warn / crit |
| Motor temp (same three) | 76 / 85 / 92.5 °C | 3 °C | 2 s | 60 s | info / warn / crit |
| Rapid temp rise | FET ≥ 0.7 °C/s over 5 s; motor ≥ 0.3 °C/s over 20 s | — | — | 120 s | warn |
| Low battery | SoC ≤ 20 % / ≤ 10 % | 3 % | 5 s | 5 min | warn / crit |
| Approaching battery cut | `vPack ≤ cut_start + 1 V` / `≤ (cut_start + cut_end)/2` from `GET_BATTERY_CUT`, only if not the defaults 10/8 V | 0.5 V | 1 s | 60 s | warn / crit |
| Low cell voltage (loaded) | NMC 3.0 / 2.8 V; LFP 2.7 / 2.5 V | 0.05 V | 1 s | 60 s | warn / crit |
| Excessive sag | ≥ 20 % / ≥ 30 % of `vRestRef`, valid reference only | 3 % | 1 s | 60 s | warn / crit |
| Controllers' voltages diverge | ≥ 1.5 V | 0.5 V | 5 s | 5 min | warn |
| Motor / battery current near limit | ≥ 95 % of the user-set limit | 5 % | 2 s | 30 s | info, **off by default** |
| Duty near max | ≥ 0.90 / ≥ 0.94 (max duty default 0.95) | 0.03 | 1 s | 20 s | warn / crit |
| Fault | any `fault_code ≠ 0` | latched | 0 | 0 | per group below |
| Link stale | no sample 1 s / 3 s; a CAN controller silent > 5 polls | — | — | 30 s | warn / crit |
| BLE RSSI weak | ≤ −90 dBm for 5 s | 5 dB | 5 s | 60 s | info |
| GPS lost (run / calibration) | `LOST` | — | 3 s | 30 s | warn |
| Speed limit | above user limit | 1 km/h | 1 s | 30 s | info |
| Wheel slip | ≥ 3 slip flags in 60 s | — | — | 60 s | info |
| Storage | 80 / 95 / 100 % of the limit ([ride-logging.md](ride-logging.md#storage-limit)) | — | — | 10 min at 100 % | info / warn / crit |
| Dropped samples | recorder queue overflow | — | — | 60 s | warn |

All thresholds are settings; the simulated ones (temp rise, sag, slip) need field tuning.

## Fault severity
- **Critical:** 1-9 (over/under voltage, DRV, abs over-current, over-temp FET/motor, gate driver, MCU under-voltage), 14, 15-18 (current sensor offset, unbalanced currents), 19 BRK, 23-24 flash corruption, 27 phase filter.
- **Encoder / resolver** (11-13, 20-22, 25, 26, 28, 30): critical if the motor stops, otherwise warning.
- **Warning:** 10 watchdog reboot (also resets counters), 29 LV output, 31-33 over/under-speed.
- Unknown code: critical, shown as `FAULT_<n>` with the controller.

## Tests
Defaults: info at 76 °C after 2 s, warning at 85, critical at 92.5, clears at 82. Duty 0.91 for 1 s → warning; 0.86 clears. `cut_start 39.0, cut_end 33.0` → warn ≤ 40.0 V, crit ≤ 36.0 V.

## Built so far
- Engine in `core/alert/`: levels with dwell, hysteresis on the way down (one step at a time), escalation immediate after its dwell, cooldown only suppresses repeated notifications. A missing input (absent sensor, no detected pack, default battery cut, one controller for divergence) clears the alert and keeps it quiet.
- Catalog: FET and motor temperature (76 / 85 / 92.5 °C from the bldc defaults), fast temperature rise, low battery, approaching battery cut (from `GET_BATTERY_CUT`), low cell voltage, excessive sag, voltage divergence, duty near max (0.90 / 0.94), faults (latched, marked cleared when the code returns to 0; encoder faults critical when the motor is stopped), link stale, wheel slip. Current-near-limit stays off.
- The service evaluates at ~10 Hz whether or not the UI observes frames, sends an `alerts` event on change and posts a heads-up notification (own channel, sound and vibration) for warnings and critical alerts while the app is in the background.
- AlertBanner in ride mode (critical stays until cleared or dismissed, warnings hide after 6 s; dismissing a fault clears the latch); info alerts are counted in the status strip.
- Alerts screen (Connect → Alerts): every catalog alert grouped as Temperature, Battery, Power, Faults, Connection with its trigger and severities, an on/off switch per alert (persisted on the phone, applied to the native engine at once and on every new session; an off alert never fires, shows or notifies), a hint when the connected vehicle can't support it (no motor sensor, pack not detected, single controller), and a Test sound button that posts a sample notification.
- Thresholds: tap an alert to change each level's threshold (temperatures, temperature rise, low battery, sag, voltage divergence, duty, link stale, slip) in display units; levels must keep escalating; saved on the phone and applied to the native engine at once; "Reset to default". Battery cut (from the controller), low cell (per chemistry) and faults are fixed.
- Per alert "Sound" / "Silent": a silent alert still shows the banner but never sounds or posts a background notification (saved on the phone).
- Per alert "Clears below/above" (hysteresis, as the alert-edit mockup) and "Repeat after" (cooldown, 0-3600 s in 10 s steps) in the threshold editor; Reset to default restores them with the thresholds. Stored on the phone as `alerts.tuning`.
- Not yet: separate vibrate / sound outputs, the controller limit tick on a slider, RSSI / GPS / speed limit / storage / dropped-sample alerts, widget pulse.
