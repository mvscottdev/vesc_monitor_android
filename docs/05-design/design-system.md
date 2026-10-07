# Design system — DRAFT

The visual spec for the app. [tokens.json](tokens.json) is the source for every value; the [mockups](mockups/index.html) show every screen; this file states the rules and names the components. How to use the tools: [README.md](README.md).

## 1. Principles
1. **One glance.** The ride screen is read in under a second at arm's length, on a vibrating handlebar, in sun or at night. Big tabular numbers, few words, colour only where it means something.
2. **Ride mode and manage mode are different places.** Ride mode is full screen, 56 dp targets, nothing moves by accident. Everything else (history, settings, vehicle) uses a nav rail and normal 48 dp targets.
3. **State in form, not only in numbers.** Warn/crit change the widget's border, fill and icon colour. Stale data dims. A rider sees a problem before reading it.
4. **Never show a stale speed.** Other values may show their last value with an age tag; speed shows dashes.
5. **Say where a value comes from.** Speed source chip (VESC / ERPM / GPS), provenance badge on every setting (You / VESC / Detected / Default), combine rule on multi-controller widgets (Combined / Sum / Max / Hottest).
6. **One controller is the normal case.** With one controller, every per-controller selector, the Controllers page and source chips are hidden. Nothing in a layout assumes two.
7. **Read-only copy.** Words never suggest the app changes the controller ("Set it in VESC Tool", never "Apply to controller").

## 2. Foundations (values in [tokens.json](tokens.json))
| Group | Summary |
|---|---|
| Reference screen | 915 × 412 dp landscape; must also work at 732 × 360 dp. Mockups use 1 px = 1 dp. |
| Themes | `night` (default, dark glass) and `sun` (light, high contrast, flat, for direct sunlight). Same token names, different values. |
| Colour roles | `accent` = speed and primary actions. `metric.*` = one hue per physical quantity (drive amber, regen teal, voltage violet, temperature orange). `state.*` = ok / info / warn / crit / stale, never used for decoration. |
| Zones | Temperature: ok → warn at derating start − 10 °C → crit at derating start (limits read from the controller). SoC: crit < 10 %, warn < 20 %. Duty: warn ≥ 0.85, crit ≥ 0.95. |
| Type | `display` = Barlow Semi Condensed for every number (tabular digits). `ui` = IBM Plex Sans for words. `mono` = IBM Plex Mono for IDs and codes. Scale: hero 112 · numXL 64 · numL 40 · numM 28 · numS 20 · title 20 · heading 15 · body 14 · caption 12 · label 11 (uppercase, +1.1 tracking). Live values ≥ 28 dp. |
| Space & shape | 4 dp steps (4, 8, 12, 16, 20, 24, 32, 40). Radii 6 / 10 / 14 / 20 / pill. Widgets use `md` (14). |
| Ride grid | 12 columns × 6 rows, 8 dp gap, 10 dp padding, 30 dp status strip on top. Widgets are `{x, y, w, h}` in cells, min 2 × 1. |
| Glass | Tiers `full` (real blur, overlays only), `static` (widgets: pre-blurred page background + drawn edge and highlight), `flat` (power save, thermal, sun). Never blur over live-animating content. |
| Motion | Value tween 90 ms, gauge spring (damping 22, stiffness 260), sheet 280 ms, crit pulse 900 ms. Reduced motion: values jump, no pulse, no slides. |
| Icons | Lucide, 2 dp stroke, 16 / 20 / 24 dp. Same names in `mockups/icons.js` and in the app, where `node scripts/design.mjs tokens` turns the sprite into Skia path strings (`src/theme/icons.ts`, drawn by `components/icon.tsx`; no SVG library needed). |

## 3. Navigation
```
Dashboard (ride mode, pages swipe) ──menu button──▶ NavRail screens
  │                                                  ├─ Speed test (armed/running are full screen, like ride mode)
  ├─ long-press empty cell ─▶ Edit layout ─▶ Widget picker (sheet)
  ├─ tap status link ─▶ Connect                        ├─ History ─▶ Ride detail / Run result / Compare
  └─ AlertBanner (overlay)                             ├─ Vehicle ─▶ Battery detection / Wheel calibration
                                                       ├─ Alerts ─▶ Edit alert (sheet)
                                                       └─ Settings ─▶ Storage / Run in background
First run / no device: Connect ─▶ Detect controllers ─▶ Battery detection (if unsure) ─▶ Dashboard
```
NavRail order (top to bottom): Dashboard `gauge`, Speed test `timer`, History `history`, Vehicle `bike`, Alerts `bell`, then Settings `settings` at the bottom.

## 4. Components
Class names in `mockups/kit.css` match these names in kebab-case, so a grep finds both.

| Component | Mockup class | What it is | Key props | Build with |
|---|---|---|---|---|
| StatusStrip | `.statusbar` | Ride-mode top line: menu, link dot + name, poll rate, GPS, page dots, REC pill, clock | linkState, rateHz, gps, page, logging | RN views; values from warm store (≤ 5 Hz) |
| RecPill | `.rec` | Logging toggle with ride time | on, elapsed | Pressable, 56 dp hit area |
| WidgetGrid | `.wgrid` | 12 × 6 cell grid, one Skia canvas per page | layout, page, editing | Pure-TS layout engine + RNGH + Reanimated |
| Widget | `.w` (+ `.warn` `.crit` `.stale`) | Glass cell with head (icon, label, SourceChip), body, foot | metric, style, source, zone | Drawn inside the page canvas |
| BigNumber | `.val .num .unit` | Value + unit, display face | value, unit, decimals, size | Skia text from a shared value |
| RadialGauge | `svg.gauge` | Arc gauge, 240-270° sweep, ticks, peak mark, warn arc | value, min, max, warn, peak, ticks | Skia path, animate sweep via transform/trim |
| BarMeter | `.bar` | Linear fill with zone colour | value, max, zone | Skia rect |
| PowerBar | `.bibar` | Bidirectional: regen left of zero, drive right, peak tick | watts, regenMax, driveMax, peak | Skia rects |
| Sparkline | `svg.spark` | Last N s trend with area fill and end dot | series, color, window | Skia path from native-decimated series |
| SourceChip | `.src` | Tiny outline chip: VESC, ERPM, GPS, Combined, Sum, Max, Front… | label | Text |
| StaleTag | `.stale-tag` | Dashed age tag, e.g. "4 s" | ageS | Text |
| AlertBanner | `.banner.{info,warn,crit}` | Overlay from the top: severity tile, title, one-line action, Mute, dismiss | alert | RN view, full glass tier |
| NavRail | `.rail` | 68 dp left rail, 52 × 48 items, active = accent pill, warn pip | route, badges | Expo Router layout |
| Panel | `.panel` | Grouped surface on manage screens | — | RN view, static glass |
| ListRow | `.lrow` (+ `.sel` `.dis`) | 52 dp row: lead icon, title/sub, trailing value/control | — | RN Pressable |
| ProvenanceBadge | `.prov.{user,vesc,auto,def}` | Where a setting's value comes from | source | Text |
| Chip | `.chip` (+ `.on` `.ok` `.warn` `.crit` `.dim`) | Filters, status, severity | — | Text |
| SegmentedControl | `.segc` | 2-5 exclusive options | options, value | Pressable row |
| Switch, Slider, Field, Button, IconButton | `.switch` `.slider` `.field` `.btn` `.icon-btn` | Standard controls. Button variants: default, `primary`, `ghost`, `danger`; sizes `sm`, default, `big` | — | RN |
| Sheet | `.sheet` (+ `.wide`) | Right-side sheet (400 / 520 dp) over a scrim; head, body, foot | — | RN + full glass |
| Stepper | `.steps .step.{done,now}` | Detection progress | steps | RN |
| Chart | `svg.chart` | Line + min/max band, grid, axis labels, threshold lines, scrub line + tooltip | series, band, yRange, xLabels, scrubAt | In-house Skia chart, data as min/max buckets from native |
| EditToolbar, ResizeHandle, DropGhost | `.edit-toolbar` `.handle` `.ghost-drop` | Edit-mode controls | — | RNGH gestures |

## 5. States every screen must handle
| State | Treatment |
|---|---|
| Connected | Link dot ok, measured rate in the strip |
| Connecting / reconnecting | Link dot crit, rate "—", warn AlertBanner with attempt count; widgets go stale after 1 s |
| One controller stale on CAN | Only that controller's widgets and card go stale |
| No data yet (first frame) | Widgets show "––" in `textTertiary`, no zero values |
| Value unknown by config | Replace the value with an action, e.g. "Set wheel size" |
| Alert warn / crit | AlertBanner + the source widget turns warn/crit; crit pulses |
| Logging off | RecPill `.off` (hollow dot, "REC" dimmed) |
| Sun theme | `data-theme="sun"`, glass tier `flat` |
| Reduced motion | No tween, pulse or slide |
| Glass tier flat | Widgets use `glassFill` only, no highlight |

## 6. Copy
Sentence case. Short, direct, from the rider's side ("Motor getting hot", not "MOTOR_TEMP_WARN"). Errors say what happened and what to do; the raw code is shown small underneath. Numbers always carry units; °C/°F and km/h/mph follow Settings. No exclamation marks.

## 7. Screen inventory
| Screen | Mockup | Feature doc |
|---|---|---|
| Dashboard (1 controller, 2 controllers, alert, reconnecting, sun) | [dashboard.html](mockups/dashboard.html) | [dashboard](../02-features/dashboard.md), [vesc-topology](../02-features/vesc-topology.md) |
| Edit layout, widget picker | [dashboard-edit.html](mockups/dashboard-edit.html) | [dashboard](../02-features/dashboard.md) |
| Scan, detect controllers, connection failed | [connect.html](mockups/connect.html) | [ble-connection](../02-features/ble-connection.md), [vesc-topology](../02-features/vesc-topology.md) |
| Vehicle profile, battery detection, wheel calibration (collecting, result) | [vehicle.html](mockups/vehicle.html) | [vehicle-profile](../02-features/vehicle-profile.md), [battery](../02-features/battery.md), [speed](../02-features/speed.md) |
| Speed test armed, running, result, compare | [speed-test.html](mockups/speed-test.html) | [speed-test](../02-features/speed-test.md) |
| Ride list, ride detail | [history.html](mockups/history.html) | [ride-logging](../02-features/ride-logging.md) |
| Alert list, edit alert | [alerts.html](mockups/alerts.html) | [alerts](../02-features/alerts.md) |
| Settings, storage, run in background, system notification | [settings.html](mockups/settings.html) | [ride-logging](../02-features/ride-logging.md), [ble-connection](../02-features/ble-connection.md) |

## 8. Implementation rules (React Native)
- **Tokens:** `src/theme/tokens.ts` is generated from `tokens.json` by `node scripts/design.mjs tokens`; `design.mjs check` (part of `scripts/check`) fails when it is stale. No literal colour, size or font in components; a lint rule can ban hex literals outside `src/theme/`.
- **Live values:** drawn in Skia from Reanimated shared values. No React re-render per frame. One `<Canvas>` per dashboard page, not per widget.
- **Fonts:** bundle the three families via `@expo-google-fonts/*`. Always set `fontVariant: ['tabular-nums']` on numbers outside Skia.
- **Glass:** widgets get the `static` tier (the page background is blurred once, edges drawn). Real blur only on sheets, banners and the edit toolbar.
- **Mockup vs app:** mockups are the reference for layout, hierarchy, colours and copy. Pixel-exact match is not the goal; the token values are.
