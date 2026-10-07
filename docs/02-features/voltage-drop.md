# Voltage drop — RESEARCHED


## Rest reference
- **Quick rest:** `|I_bat total| ≤ 0.5 A` for ≥ 3 s (all controllers fresh) → `vRestRef = vPack`. Used for sag.
- **Settled rest:** same for ≥ 60 s → SoC anchoring and battery detection ([battery.md](battery.md)).
- The reference follows the pack as it drains: `vRestRef(t) = vRestAtCapture − slopeVPerWh × WhNetSince`, with `slopeVPerWh` from the OCV table at the current SoC and the capacity (13S 12 Ah near 40 %: 0.0134 V/Wh). Capacity unknown → no decay, expires after 10 min. Always expires after 30 min; while expired sag shows "~".
- 0.5 A because a VESC idles at ~0.1-0.4 A (setting).

## Live sag
`sag = max(0, vRestRef − vNow)` in V and % of `vRestRef`, counted only when `I_bat ≥ 5 A`. A rise above the reference during regen is shown separately as "regen rise".

## Min voltage and peak sag
Per ride and per run: min of the **filtered** `v_in` with its time and current, min cell voltage (`min / S`), peak sag (V and %). Run window = launch to run end + 2 s.

## Test
`vRestRef 54.0`, 10 Wh drawn, slope 0.0134 → ref 53.87; `vNow 46.5 V, I 45 A` → sag 7.37 V = 13.7 %; `vNow 54.2` in regen → sag 0, regen rise 0.33 V.

## Built so far
- Quick rest (|I| ≤ 0.5 A for 3 s, all controllers fresh) captures the reference; sag = ref − V while I ≥ 5 A, 0 otherwise; regen rise tracked. Session min voltage. The reference expires after 10 min (no decay yet: it needs capacity).
