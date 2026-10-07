# Temperatures — RESEARCHED


- Motor and MOSFET temperature per controller (`GET_VALUES` bits 0-1). Combined widget = max over controllers whose sensor is `PRESENT`; detail view per controller (hidden with one controller).
- °C / °F switchable.

## Sensor present?
Per controller, per sensor, on every sample (sticky, re-checked every 60 s):
- `ABSENT` if the value is NaN, < −40 °C or > 200 °C (bldc forces an invalid reading to −100 °C), or it stays exactly 0.0 / frozen (σ < 0.05) for 300 s while the other temperature moves > 3 °C.
- `UNKNOWN` for the first 10 s, then `PRESENT` once a plausible value (−30…200 °C) has been seen.
- `ABSENT` → value hidden, its alerts disabled, excluded from max.

## Colour zones
Derating zones from the bldc factory defaults (no mcconf in v1): **76 °C** (acceleration derating starts), **85 °C** (current derating starts), **92.5 °C** (halfway to cut-off at 100 °C). The user can set their own limits per controller. Same numbers drive the alerts ([alerts.md](alerts.md)).

## Tests
Motor `−100.0 …` → ABSENT; `0.0` for 300 s while FET goes 30 → 45 °C → ABSENT; `27.4, 27.6, 28.1` → PRESENT.
