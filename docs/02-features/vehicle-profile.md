# Vehicle profile & wheel calibration — RESEARCHED

## Goal
Know what speed, SoC and range need, without writing anything to the VESC.
## Profile (saved per BLE device)
- Per controller: `motorPoles`, `gearRatio`, `wheelDiameterMm` (or just the effective `speedPerErpm` learned from `GET_VALUES_SETUP`), learned GPS scale `k`.
- Vehicle: `type` (`scooter | ebike`), `driveType` (`hub | mid-drive`), `speedSource` (`auto | vesc | erpm | gps`, [speed.md](speed.md)), battery block ([battery.md](battery.md)).
- Each value has a source: `user` > `vesc` > `learned` > `default`; the UI shows which.
- **No mcconf in v1:** motor poles, gear and wheel are not read. When the VESC speed looks valid, the app stores `speedPerErpm = speed / ERPM`, which is all the `erpm` source needs. The user may enter poles/gear/wheel as an override.
- A snapshot of the profile is stored with every ride (the stored canonical speed stays correct if the profile changes later).

## GPS quality levels
Timestamps: `Location.elapsedRealtimeNanos` only. `fixAge = now − fix time`.

| Level | All of | Used for |
|---|---|---|
| `SPEED_GOOD` | `hasSpeedAccuracy`, `0 < speedAcc ≤ 0.5 m/s`, `hAcc ≤ 10 m`, `fixAge ≤ 1.5 s`, ≥ 6 satellites used, not mock | speed scale `k`, display cross-check |
| `CAL_GOOD` | `0 < speedAcc ≤ 0.3 m/s`, `hAcc ≤ 5 m`, `fixAge ≤ 1.5 s`, ≥ 8 satellites, bearing accuracy ≤ 15° if reported | wheel calibration, speed-test validation |
| `LOST` | no fix for 3 s | fallback, alert during a run or calibration |
Hysteresis: good after 3 consecutive good fixes, degraded after 2 bad. `speedAcc == 0` means unavailable. The phone's actual GNSS rate and `hasSpeedAccuracy` support are probed at first GPS use and logged.

## GPS wheel-calibration helper
Purpose: tell the user the **wheel diameter to set in VESC Tool** (the app never writes it).
1. User taps "Calibrate wheel" and rides steadily.
2. **Window** = 8 s, non-overlapping, accepted when: every fix `CAL_GOOD`; mean GPS speed ≥ 5 m/s (18 km/h); GPS speed range ≤ 0.6 m/s; motor slope ≤ 0.3 m/s²; no slip in the window or the second before; heading change ≤ 5° overall and ≤ 4°/s. Otherwise show "weak GPS / not steady" and pause.
3. Per window `r = mean(v_gps) / mean(v_app(t − lag))`.
4. Result after **≥ 30 windows** (~4 min, ~1.2 km); may stop at 20 if the CI is ≤ 0.5 %: `k = median(r)`, `σ = 1.4826·MAD`, `ci95 = 1.96 · 1.2533 · σ / √N`. `HIGH`: N ≥ 30 and ci95 ≤ 0.5 %; `MEDIUM`: N ≥ 20 and ≤ 1.0 %; else keep collecting.
5. Suggested diameter `d' = d × k`, rounded to 0.1 mm, with range from the CI. If `k` is within 0.3 % of 0.5, 2, 1.5, 0.667, 0.75 or 1.333: "looks like a pole-count or gear error, not wheel size" (VESC counts poles, not pole pairs).
6. Speed bins 18-25, 25-35, > 35 km/h: if their medians differ > 1.5 %, suggest calibrating at the usual cruise speed.
7. Result per controller; the user may apply it as an in-app override.

Worked example: `d = 254.0 mm`, 42 windows, median 1.031, σ 1.9 % → ci95 0.72 % → MEDIUM, `d' = 261.9 mm` (260.0-263.8).

## Edge cases
Slip windows excluded; different front/rear wheels → per-controller results; city multipath rejected by `CAL_GOOD`; grades > 8 % (when altitude is usable) rejected; tyre pressure and wear shift the result 0.5-1.5 %.
