# Speed test (acceleration runs) — RESEARCHED


## Requirements
- **Default bracket 0-60 km/h**; configurable (0-30, 0-50, 0-100, rolling 30-60, distance 100 m, 1/8 mile = 201.17 m). Several brackets per run; brackets above the vehicle's top speed show "not reached".
- Timing comes from **fused motor speed** ([speed.md](speed.md)); GPS is on for validation only.
- Record the full curve at full poll rate; the run is its own capture, kept at full rate, saved even with logging off ([ride-logging.md](ride-logging.md#speed-test-runs)).
- History: list, detail with charts, overlay of 2+ runs.

## State machine
`IDLE → ARMED → STAGED → RUNNING → DONE | ABORTED`
- **ARMED** (user tap) → **STAGED** when speed ≤ 0.5 km/h for ≥ 1 s on all controllers ("ready" shown). Arm timeout 120 s.
- **Launch trigger:** speed > 1.5 km/h and rising for 2 samples. The trigger only detects the launch; start time is computed afterwards.
- **RUNNING** ends when all brackets are reached, when acceleration stays ≤ 0.1 m/s² for 2 s (top speed; remaining brackets "not reached"), or at the timeout (25 s for 0-60, 40 s for 0-100).

## Start conventions (both stored)
| | Definition | Repeatability (simulated) |
|---|---|---|
| **1 ft rollout** (headline) | `t0` = time when integrated distance reaches 0.3048 m | ±7-11 ms |
| From first motion | line fit through samples at 1.5-6 km/h, extrapolated to v = 0, not before the last standstill sample | 30-150 ms bias, ±25-45 ms |
On scooters the first foot takes 0.4-0.6 s, so the two differ by that much. Every displayed time says which convention it uses. If low-speed ERPM is unreliable (sensorless start), rollout falls back to first motion with the flag "start estimated".

## Crossing time
Local least-squares line over samples within ±0.3 s of the crossing, solved for `v(t) = V`. Plain interpolation as fallback: `t = t_i + (V − v_i)/(v_{i+1} − v_i) · (t_{i+1} − t_i)` (test: `(3.10 s, 58.9)`, `(3.15 s, 60.5)`, V = 60 → 3.134375 s). Distance targets integrate speed from `t0`.
Accuracy is set by speed noise / acceleration at the crossing and BLE timestamp jitter, not by the sample rate: ±5-25 ms at 10-30 Hz with the local fit. Keep ≥ 10 Hz per controller during a run. If timestamp jitter p95 > 40 ms or rate < 8 Hz, show the time as "±".

## GPS validation
Compare the motor time with the lag-corrected GPS time: `|Δ| > 0.2 s` → hint "calibration off?". If `k·v` and GPS speed at bracket end differ > 3 %, show "motor speed may be off by x %" with a corrected time. A 3 % speed error moves a 3.2 s 0-60 by ~0.1 s.

## Abort rules
(a) false start: back to ≤ 0.5 km/h for ≥ 1 s before 10 km/h; (b) rolling start: first sample after trigger > 5 km/h; (c) lift-off: after peak > 10 km/h, `v < v_peak − max(3 km/h, 5 %)` for ≥ 0.3 s, unless all brackets are done; (d) no motor sample for > 0.5 s or a controller stale; (e) any fault; (f) run timeout; (g) arm timeout. Aborted runs are discarded or saved as "aborted" (setting).

## Saved with each run
Bracket times (both conventions), peak power, min voltage (+ time, current), max sag, max temps, flags: `slip` (before 20 km/h), `gpsQuality`, `startMethod`, `k`, `sampleRateHz`, timestamp jitter p95.

## UI
Big timer, live speed, bracket split list, clear "armed / ready" state (landscape).

## Test vectors
Constant 4 m/s² at 20 Hz: 1 ft at 0.390 s, 60 km/h at 4.167 s → rollout time 3.777 s. Standstill `[0.10, 0.12, 0.05…]` m/s for 1 s → STAGED; 0.20 m/s blocks it. First post-trigger sample 6 km/h → abort (b). 42 → 36 km/h for 0.4 s → abort (c).

## Built so far
- `core/run/`: `RunTimer` state machine with all abort rules, `RunMath` (rollout by exact trapezoid integration from the last standstill sample, first motion by a 1.5-6 km/h line fit, crossings by a local ±0.3 s fit with interpolation fallback, distance from `t0`, timestamp jitter p95), with test vectors.
- Fed on every fast sample with the combined learned-ratio speed (lower speed while accelerating) at the sample's own time; stale controller → abort (link), any fault → abort. Default brackets 0-30, 0-50, 0-60 km/h and 100 m; timeout 25 s.
- Arming is refused without a trusted speed (no learned ratio, or the default wheel).
- Speed test screen (from the Connect screen): phase line, big timer (extrapolated between 5 Hz session events), live speed, splits with both conventions, run facts (peak speed and power, min voltage, sample rate, slip, start estimated).
- Finished runs are saved (database version 2, table `run`: report and decimated curve as JSON, kept with logging off) and listed under "Past runs" on the speed test screen; long-press deletes. Aborted runs are not saved.
- Past runs: tap to select up to 4 and Compare overlays their speed curves on one time and speed scale (accent, drive, voltage, regen); the fastest 0-60 is marked Best. The screen gets an accent frame while armed and a drive frame while running.
- With the speed unit set to mph, runs are armed with 0-20 / 0-30 / 0-40 mph and 100 m; live speed, peak and the compare chart follow the unit.
- Screen as the speed-test mockups: before and during a run a big phase word (SPEED TEST / ARMED / READY / the running timer / ABORTED), one line of what to do, the bracket chips, a NOW speed card, battery and motor temperature, and one big Arm / Disarm button; after a run "Run result" with the headline bracket (0-60 km/h or 0-40 mph, else the last reached) and Best, every bracket's time, the run's speed curve and peak speed, peak power, min voltage and motor temperature tiles, with Arm again and Past runs.
- Past runs chart (one run or up to four): drag across it to read each run's speed at that time.
- Not yet: custom brackets, GPS validation, power and voltage overlays.
