# Performance rules — RESEARCHED

Budgets marked (spike) are targets still to be confirmed by a measurement on a real phone.

## Budgets
- Telemetry poll: as fast as BLE allows, response-paced, cap 50 Hz per controller; the measured rate is shown and logged.
- Native → UI: one live frame per display tick, **≤ 30 Hz, latest-wins**, only while the UI observes.
- UI: the phone runs at 120 Hz: target **p95 frame time ≤ 8.3 ms and ≤ 5 % janky frames** in ride mode with a full dashboard page (spike S2). If 120 Hz is out of reach, cap the dashboard at 60 Hz rather than jank.
- JS thread: frame delivery ≤ ~2 ms per second at 30 Hz (spike S1).
- DB: one transaction per second per open ride, off the main thread.

## Rules
**Native side**
- Parse and derive natively; the UI gets pre-computed values in SI units.
- Session work runs on its own thread, never the main looper.
- Emit from a native timer (latest-wins), not per BLE reply; gate on observers; include `seq` and `tMs`.
- Sparklines use a natively decimated ~1 Hz series, not a JS ring buffer of frames.

**React / state**
- Hot data: the live frame goes into **one shared value**; widgets read it through one mapper per widget cluster. Never `setState` or a Zustand set per sample.
- Warm data (connection, alerts, controller list): Zustand 5, at most ~5 re-renders/s for any subtree. No `useSyncExternalStore` beside Zustand.
- Dev-only render-rate canary at every stream boundary.
- Files using `useDerivedValue` carry `'use no memo'` (or the React Compiler stays off).
- No `setInterval` polling for telemetry in JS.

**Drawing (Skia + Reanimated)**
- **One `<Canvas>` per page or widget cluster, never one per widget** (each canvas is a native surface).
- Count mappers, not work: fewer, bigger mappers. Animate transforms/matrices, not geometry or shader parameters.
- Live numbers are drawn in Skia (monospace glyph width × length); no per-frame text shaping, no `TextInput` tricks.
- Paths from one SVG string (`MakeFromSVGString`), never a `moveTo/lineTo` loop.
- **Glass:** no blur over live content. Widgets use static-backdrop glass (pre-blurred background slices); real backdrop blur only for short-lived overlays. Tiers `full | static | flat`; auto-degrade to `flat` on power-save or thermal status ≥ moderate.

**Charts**
- Decimate before drawing, natively: min/max buckets (M4), one bucket per pixel column. Never render raw ride data.
- Series queries run off the JS thread, cancel superseded requests, never hold a DB read transaction open.

## Measuring
Release build on the phone: `adb shell dumpsys gfxinfo <pkg>` (jank %, percentiles), Perfetto / Android Studio profiler for threads, Hermes sampling profiler for the JS thread. Steps in [tooling.md](tooling.md). Measure before optimising.
