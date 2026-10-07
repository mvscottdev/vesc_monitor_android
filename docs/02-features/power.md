# Power & current — RESEARCHED


- `P = v_in × current_in` per controller (its own voltage); combined = sum. Shown in W or kW.
- Motor current per controller and summed; battery current summed (signed); duty = max |duty|.
- `current_in` / `current_motor` from `GET_VALUES` are averages since the previous read (another app polling the same VESC shortens the window).
- Peaks per ride and run: max W, max battery A, max regen W.

## Energy counters (Wh, Ah, tachometer)
VESC counters live in RAM and restart at 0 when the controller reboots. Always sum **deltas**, never raw values:
```
first sample of the session → 0 (don't count energy from before the session)
x ≥ prev − eps              → max(0, x − prev)          eps: 0.01 Wh, 0.001 Ah, 0.5 m
x <  prev − eps             → x (reboot; count since boot), resets++
```
- Used and regenerated are tracked separately; net = used − regen. Combined = sum of per-controller deltas.
- Cross-check against `∫ V·I dt`; if a counter delta after a gap exceeds 3 × the integral + 1 Wh, use the integral and flag `counterSuspect`. Missing counters → integrate `V·I` (trapezoid, dt ≤ 1 s).
- Fault 10 (`BOOTING_FROM_WATCHDOG_RESET`) also marks a reboot.
- Test: Wh `10.00, 10.50, 11.10, 0.20, 0.55` → `0.50 + 0.60 + 0.20 (reset) + 0.35 = 1.65 Wh`, resets = 1.
