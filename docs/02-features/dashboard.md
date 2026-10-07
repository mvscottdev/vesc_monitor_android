# Dashboard — RESEARCHED


## Requirements
- Landscape, dark theme first, keep-screen-on while the dashboard is shown, status and navigation bars hidden (edge-to-edge insets respected). Readable at arm's length on a handlebar.
- **Widget grid:** the user adds widgets, drags to move, resizes by snapping to cells; **widgets never overlap**. Up to ~12 widgets per page, 3-5 pages (swipe), saved presets.
- Each widget = metric + visual type + source (`combined` | a specific controller). With one controller the source selector is hidden ([vesc-topology.md](vesc-topology.md)).
- Visual style: [design-system.md](../05-design/design-system.md) and the [dashboard mockups](../05-design/mockups/dashboard.html); this doc defines behaviour and performance.
- Visual types: big number, radial gauge, bar, sparkline (last N s), status (connection, faults, GPS quality).
- **Edit mode vs ride mode:** gestures exist only in edit mode; in ride mode they are unmounted (no accidental drags).

## Grid model
```
Page   { id, cols, rows, widgets: Widget[] }            e.g. 12 × 6 cells in landscape
Widget { id, type, x, y, w, h (cells), source, config }
Layout { version, pages: Page[], presets }              versioned JSON, persisted locally
```
- Pure-TS layout engine (place, move, resize, collision resolve, min/max size per widget type), Jest-tested. May port functions from `react-grid-layout/core` (MIT) or be written from scratch.
- Edit gestures: RNGH `Pan` on the widget (move) and on a corner handle (resize); x/y/w/h in shared values; snap to cell with a spring on release; commit the layout once per gesture.
- Pages: `react-native-pager-view`. Page and widget lists in settings: `react-native-sortables`.

## Rendering
- Live values come from **one shared value** holding the latest frame; no React re-render per sample ([performance.md](../04-rules/performance.md)).
- **One Skia canvas per page** (or per widget cluster), never one per widget. Live numbers drawn in Skia.
- Glass: widgets sit on **static-backdrop glass** (pre-blurred background slices + tint + edge highlight), live gauges drawn on top. Real blur only for short-lived overlays (sheets, menus, edit toolbar). Setting `glass = full | static | flat`, auto-degrades to `flat` on power-save or thermal pressure.
- Target at 120 Hz: p95 frame ≤ 8.3 ms, ≤ 5 % janky frames with a full page (spike S2).
- Live numbers are not TalkBack-readable.

## Metrics catalogue
speed, power, battery current, motor current, voltage, cell voltage, SoC, range, Wh/km, Wh used, sag, min voltage, motor temp, MOSFET temp, duty, trip distance, max speed, ride time, fault, BLE RSSI, poll rate, GPS quality, speed source / `k` quality.

## Built so far
- Ride mode with **default presets** (`src/dashboard/presets.ts`): main page = Battery, Range, speed gauge, Power, Temperature, Sag (as the mockup). 1 controller adds a details page (voltage, battery current, duty, power, its card); 2 controllers add a page with a combined column (voltage, power, duty) and one card per controller; 3+ controllers add card pages, up to 4 cards each.
- Speed gauge: live combined speed, scale max = session max rounded up to the next 20 (at least 60 km/h), white tick at the session max, chip VESC, warn arc while slipping. Unknown or stale speed shows dashes, with "Set wheel size in VESC Tool" when the VESC has no wheel configured. Battery: SoC with "~" and the confidence when not high, bar with zones (warn < 20 %, crit < 10 %), V and V/cell, detected pack. Range: "learning" (capacity not learned yet), Wh/km and trip. Sag: sag V and session min V. Controller cards show their own speed.
- Display units (Connect → Settings → Display & units): km/h and km or mph and miles, °C or °F. The dashboard, controller cards, history, ride charts and readout, the speed test's live and peak speed and alert texts follow them; zones, stored values and everything native stay metric. Speed scale in mph: at least 40, next 10. The speed test arms with 0-20 / 0-30 / 0-40 mph and 100 m when the unit is mph (runs compare and rank by bracket label, so km/h and mph runs never mix). Alert thresholds stay in °C.
- Non-ride screens (history, ride detail, vehicle/battery, alerts, settings, storage, background) carry the mockups' left rail: dashboard, speed test, history, vehicle, alerts, and settings at the bottom; the current one is raised.
- Layout engine (`layout.ts`): place, move, resize with per-type size limits; no overlap is an invariant tested on every preset. Edit mode: long-press a page; tap a widget to select it, then move it a cell at a time, resize it, remove it, or add a widget from the picker (type + source: combined or one controller when there are 2+). Refused edits say why and leave the page as it was. Layouts are saved on the phone per set of controllers (versioned JSON, validated on load, falling back to the preset when invalid or naming a missing controller); "Reset to default" returns to the preset. Pages: switch with ‹ ›, add an empty page (up to 6) or remove one (asks first when it has widgets; the last page stays).
- Presets in edit mode replace the main page: Commute (the default), Performance (speed, big power bar, temperatures, duty, sag), Minimal (large speed, battery, range); controller pages stay.
- Undo (up to 30 steps) for every edit in the session.
- Edit mode follows the edit-grid mockup: drag the selected widget to move it (snaps to whole cells), pull its corner to resize, × on every widget removes it; a floating bar holds Undo, the arrow nudges for the selected widget, Add widget, Pages (switch, add, remove, preset, reset) and Done.
- Not yet in edit mode: user-named presets, style per widget (number, gauge, bar, trend).
- One Skia canvas per page, one model mapper per widget (`metrics.ts`) plus small derived props. Temperature zones: warn from 75 °C, crit from 85 °C (bldc default current-derating start, no mcconf in v1). Duty zones 0.85 / 0.95. The power bar's zero sits at one third; spans follow the session peaks (at least 1 kW drive, 500 W regen).
- Stale values dim to 35 % with an age tag; with one controller stale, only its card and its per-controller values go stale; combined values drop it and show "k/N" on the voltage widget.
- Digits are drawn from Barlow's default figures (no tabular feature in Skia `Text`): a changing value may shift its unit by a few dp (UNVERIFIED on the phone; switch to a Paragraph with `tnum` if it jitters).
