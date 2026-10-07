# Battery — RESEARCHED

## Goal
Pack voltage, cell voltage, SoC %, remaining range and Wh used, including for riders who don't know their battery.
## Parameters
`cellsSeries`, `chemistry` (`NMC/NCA | LFP | LTO | custom`), `capacityAh`, `emptyCellV`, curve. Each has a source `user` > `vesc` > `learned` > `default`, shown in the UI.

## SoC (per-cell resting OCV tables, linear interpolation, clamp outside)
| SoC % | 100 | 95 | 90 | 80 | 70 | 60 | 50 | 40 | 30 | 20 | 10 | 5 | 0 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| NMC/NCA V | 4.20 | 4.14 | 4.08 | 3.98 | 3.89 | 3.81 | 3.74 | 3.68 | 3.62 | 3.53 | 3.40 | 3.30 | 3.00 |
| LFP V | 3.40 | 3.38 | 3.35 | 3.32 | 3.30 | 3.27 | 3.26 | 3.25 | 3.22 | 3.20 | 3.00 | 2.90 | 2.50 |
| LTO V (low confidence) | 2.75 | 2.66 | 2.58 | 2.50 | 2.44 | 2.40 | 2.36 | 2.32 | 2.27 | 2.20 | 2.08 | 1.95 | 1.60 |
NMC/NCA uses 5 % steps; the full table is the data file. Accuracy ±5 % SoC (composite curve; within 7 points of bldc's Samsung 30Q polynomial). LFP is coarse on its plateau: shown with "~" and ±15 %. LTO and `custom` are manual only.

**Display SoC:** `soc = (socAbs − socAbs(emptyCellV)) / (1 − socAbs(emptyCellV))`, default `emptyCellV` = **3.0 V NMC**, 2.8 V LFP, editable. If the VESC's `battery_cut_start / S` is above `emptyCellV`, show a hint (the VESC will limit power before the app's 0 %).

**Under load (internal only):** `SoC = table((V + R·I) / S)` with
- `R` learned from current steps (|ΔI| ≥ 8 A within 0.3-0.4 s from low current, or ≥ 15 A; `R = −ΔV/ΔI` in 0.005-0.5 Ω, median of the last 15) and a 30 s regression when current varies enough; prior `R = 0.005 Ω × S`.
- Filter toward the raw value with τ = 20 s (4 s at rest: |I| < 2 A for 10 s); **never rises while discharging** (I > 2 A); slew ≤ 1.5 %/s down, ≤ 2 %/s up (rest/charge only).
- On connect at |I| < 1 A: take the table value directly. Settled rest (|I| ≤ 0.5 A for 60 s) re-anchors.
- Simulated error 2-2.5 % RMS vs 9-20 % for raw voltage. `R` is never shown as a feature.
- Cold start hint: FET temp < 10 °C at ride start → "cold: SoC optimistic".

VESC's own `battery_level` (`GET_VALUES_SETUP`) is a cross-check only: > 15 points apart → info "VESC battery config looks wrong".

## Auto-detect
Bayesian score over chemistry {NMC/NCA, LFP} × S in 6..30 (LTO manual only):
- Hard excludes: rest `V/S` outside NMC [3.00, 4.22] or LFP [2.50, 3.65] (±0.03); max seen `V/S` > 4.30 / 3.75; filtered loaded min < 2.40 / 2.00.
- Priors: NMC 0.85, LFP 0.15; common S {10, 12, 13, 14, 16, 20, 22, 24} else −1.6 (log).
- Voltage-density terms, full-charge signature (+2 when max seen is in NMC 4.15-4.25 / LFP 3.55-3.66, +1 for 4.08-4.15).
- Evidence from the VESC (no mcconf in v1): `GET_BATTERY_CUT` values when not the bldc defaults 10/8 V: `cut_end/S` in NMC [2.7, 3.3] / LFP [2.3, 2.9] ±1.5; `cut_start/S` in NMC [3.0, 3.6] / LFP [2.6, 3.1] ±1.5.
- Confidence: top posterior ≥ 0.80 HIGH, ≥ 0.55 MEDIUM, else LOW; cap at MEDIUM when only the prior separates chemistries. UI shows the best guess + alternatives ≥ 0.10; the user confirms.
- Refinement: re-score after a charge (max held ≥ 30 s at |I| < 1 A); NMC vs LFP slope test after ≥ 20 % used (NMC drops ≥ 0.12 V/cell per 20 %, LFP ≤ 0.05).
- Known ambiguities: 13S NMC vs 16S LFP at 48-54 V; a full 20S (84 V) also fits 22S/24S at mid charge, so a first reading is LOW until a charge or the user confirms.

Test vectors: `vRest 54.2, vMax 54.6` → 13S NMC HIGH; `vRest 84.0` alone → 20S 0.40 / 22S 0.20 / 24S 0.20 LOW; `vRest 51.0, vMax 58.4` → 14S NMC, capped MEDIUM "confirm chemistry".

## Capacity learning
`capAh = Σ Ah_net / Σ ΔSoC` over intervals between settled rests with ΔSoC ≥ 30 points, using the VESC Ah counters (reset-safe deltas, [power.md](power.md)); kept across sessions. Done after ΣΔSoC ≥ 30 and ≥ 2 intervals; until then range shows "learning". Energy `capWh = capAh × S × 3.74 V` (NMC mean) or 3.27 V (LFP).

## Range
- Wh/km: distance-domain exponential average (decay 2 km) blended with the ride average: `α = min(0.5, rideM / 6000)`, `e = α·eShort + (1−α)·eRide`, floor 2 Wh/km. Before 500 m: median of past rides or "learning".
- Net energy = discharge − regen. No update below 3 km/h (idle Wh kept separately).
- `whRemaining` = integral of the OCV table from 0 % to SoC × capacity (not VESC's linear average). `range = whRemaining / e`, max 999 km.
- Test: `eShort 26, eRide 22, rideM 6000` → 24 Wh/km; 300 Wh → 12.5 km.

## Widgets
Voltage, cell voltage, SoC bar, range, Wh/km, Wh used / regenerated, battery current, "parameters: detected / confirmed".

## Built so far
- OCV tables (NMC/NCA, LFP) and display SoC (0 % at 3.0 V/cell NMC, 2.8 V LFP).
- Auto-detect scored over chemistry × 6..30 S from the first quiet reading, re-scored on each settled rest (60 s) and new rest maximum; `GET_BATTERY_CUT` is evidence, bldc defaults are ignored. Confidence LOW / MEDIUM / HIGH; "confirm" when only the prior separates chemistries.
- SoC filter: step-learned and regression R (prior 5 mOhm per cell), rest re-anchoring, never rises while discharging, slew limits.
- Battery screen (Connect → Battery): the guess with its confidence and evidence lines (rest voltage per cell, the controller's cutoff, a full charge seen), the alternatives (tap to use one), "Use …" to confirm and "Enter by hand" (cells and chemistry). The chosen pack replaces detection at once, is remembered per vehicle on the phone (BLE address, or the source for synthetic runs) and can be reset to auto-detect. Low confidence makes manual entry the primary button.
- Capacity learning from the controllers' Ah counters between settled rests (each interval ≥ 30 SoC points of discharge, done after 2 intervals; a charge or a counter going backwards starts a new interval; a pack change resets it). The sums are kept per vehicle on the phone.
- Range: distance-domain Wh/km (50 m segments, 2 km decay) blended with the ride average, only above 3 km/h, net of regen; energy left is the OCV table integral down to the empty point. Shows "learning" until a capacity is learned and 500 m are ridden this session; before 500 m the median Wh/km of the last 5 rides of 2 km or more on this vehicle stands in (remembered on the phone when a session ends). Frame fields `capacityAh`, `rangeKm`.
- Battery screen layout follows the vehicle-battery mockup: big cell count, chemistry, "best match of N", a confidence bar, the evidence lines; other possibilities on the right.
- Battery screen → Capacity: enter the pack's rated Ah (stepper, 1..500 Ah) to get a range number at once, or "Learn from rides" to go back to learning; shows the learned value and progress. Saved per vehicle on the phone.
- Empty point (Battery → Empty point): the cell voltage shown as 0 % can be raised or lowered (2.50-3.70 V per cell) to keep a reserve; SoC and range count down to it. Remembered per vehicle on the phone; Default goes back to the chemistry's value.
- Not yet: full voltage override, slope test, "config Ah looks wrong" check.
