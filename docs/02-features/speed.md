# Speed — RESEARCHED

## Goal
Accurate, low-latency speed for the dashboard and acceleration tests on any e-scooter or e-bike. Protocol: [ble-protocol.md](../01-research/ble-protocol.md).

## Speed sources
Selectable per vehicle profile; the active source is always shown on the speed widget.

| Source | How | Best for |
|---|---|---|
| `vesc` | `GET_VALUES_SETUP.speed` (m/s ×1000): the VESC's own calculation from `si_motor_poles`, `si_gear_ratio`, `si_wheel_diameter`, per controller. On some builds it comes from a hardware wheel sensor or a LispBM script | Owners whose VESC is configured (most) |
| `erpm` | App formula below with profile values / overrides | VESC config missing or wrong |
| `gps` | Phone GPS | Mid-drives with changing gears, validation |
| `auto` (default) | `vesc` if the VESC config looks valid → `erpm` if the profile has values → `gps` | Everyone |

"Config looks valid": with |ERPM| > 200, `k_cfg = speed / ERPM` differs from the factory-default constant `1.24e-4` m/s per ERPM and implies a plausible wheel diameter; shown as a confidence. VESC speed zero while ERPM is non-zero → invalid, `auto` switches to `erpm`.

The app also derives the effective ratio `speed / ERPM` from `vesc` and stores it in the profile, so `erpm` works without mcconf.

## App ERPM formula (per controller)
```
pole_pairs  = motor_poles / 2
v_erpm[m/s] = erpm / pole_pairs / gear_ratio / 60 × π × wheel_diameter[m]
```
Test: `erpm=10000, poles=30, gear=1, d=0.254 m` → 8.866 m/s = 31.92 km/h. Distance from the tachometer: `tach × π × d / (3 × poles × gear)`.

Timestamps: BLE arrival time minus half the measured round trip, monotonic clock.

## Combining controllers
```
fresh = controllers with a sample ≤ max(1 s, 5 missed polls) old
1 fresh → that value
tol = max(0.4 m/s, 4 % of mean)
spread ≤ tol → mean
else → ≥3: median; accelerating: lower; braking: higher; set slip flag (hold 0.5 s)
GPS fresh and SPEED_GOOD → prefer the controller closest to GPS
```
Tolerance covers tyre mismatch (2-3 %) and front/rear path difference in turns (2.4 % at R = 5 m). Tests: `[10.0, 10.3]` accel → 10.15, no slip; `[10.0, 11.5]` accel → 10.0 + slip, braking → 11.5 + slip.

## GPS fusion (when GPS is on)
Displayed speed = `k × v_motor` **always** (motor is fast; GPS lags 0.5-1 s). GPS only adjusts a scale `k` per controller and raises slip flags:
- `k` = scalar Kalman: start `k = 1, P = (5 %)²`; update only when the fix is `SPEED_GOOD` ([vehicle-profile.md § GPS quality](vehicle-profile.md#gps-quality-levels)), motor speed ≥ 4 m/s, steady (|a| < 0.3 m/s² and range < 0.6 m/s over 2 s), no slip. `z = v_gps / v_motor(t_gps − lag)`, `R = (speedAcc / v_motor)² + 0.004²`, `Q = (3e-4)²` per update. Accept 0.5 ≤ k ≤ 2.0, else banner "check wheel / poles / gear".
- Slip flag: motor-speed slope above +12 / −10 m/s² (no GPS needed), or `|k·v_motor − v_gps| > max(0.7 m/s, 10 %)`. During a slip: show the last good speed extrapolated (±6 m/s² max) for ≤ 1.5 s, or a fresh good GPS speed.
- `k` is persisted per controller per profile and offered as a **suggested override**, never applied silently. Quality: `GOOD` (σ < 0.5 %), `OK`, `LEARNING` (σ > 2 %).
- Simulated: 0.17-0.26 km/h RMS error vs 1.25 km/h raw with a 3.5 % wrong wheel. A complementary filter or a Kalman on speed was worse than raw because of GPS lag.

All thresholds are settings (simulated, tune on real rides).

## E-bikes
Hub motor: `vesc` or `erpm` as for scooters. Mid-drive: gears change the ratio; if `vesc` reports good speed (hardware wheel sensor on Luna-type builds, or a script), use it; otherwise the UI recommends `gps`. Profile field `driveType`: `hub | mid-drive`.

## Derived
Trip distance (VESC distance / tachometer keyframes, or integrated speed for `gps`), max and average speed.

## Edge cases
Reverse: signed speed, fusion on |v|, no `k` updates while reversing. Sensorless ERPM is unreliable below ~500 ERPM. GPS lost: keep `k`, quality decays to `OK` after 10 min. Unknown parameters: "configure wheel" instead of a wrong number.

## Built so far
- Source `vesc` only: per controller `k = speed / ERPM` is learned from `GET_VALUES_SETUP_SELECTIVE` (1 Hz, |ERPM| > 200, median of 9); fast speed is `k × ERPM` from every fast sample. A zero VESC speed while turning, or a k equal to bldc's default constant, shows "Set wheel size in VESC Tool".
- Combined speed and slip as above (tolerance max(0.4 m/s, 4 %), lower while accelerating, higher while braking, median with 3+, slip held 0.5 s). Session max speed.
- Trip distance: mean of the controllers' reset-safe `distance_abs` deltas (each controller sees the same road). Wh/km from the reset-safe Wh counters once 200 m are ridden.
- Not yet: `erpm` source with manual wheel, GPS, average speed.
