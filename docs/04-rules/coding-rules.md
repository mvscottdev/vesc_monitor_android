# Coding rules — RESEARCHED

## General
- Docs first: if the behaviour changes, update the feature doc in the same change.
- Small, focused files (~300 lines max, functions ~40); one responsibility each. No god classes: split the session by concern from day one. No dead code, no speculative abstractions.
- Names follow [CONTEXT.md](../../CONTEXT.md): `controller`, `bridge`, `link`, `sample`, `ride`, `run`, `bracket`, `sag`, `soc`.
- Units in names when ambiguous: `speedKmh`, `voltageV`, `currentA`, `tempC`, `timestampMs`.
- Constants (thresholds, SoC tables, alert defaults, masks) are named data with a source comment, never numbers buried in logic.

## Kotlin (native)
- `core/` is pure Kotlin: stdlib + `kotlinx-coroutines-core` only, test-only libraries allowed. Android, BLE, Room, GPS live in `modules/vesc` adapters behind ports (`BleTransport`, `GpsSource`, `Scheduler`, `Clock`, `EventSink`, `SampleStore`).
- Coroutines + Flow; no callbacks leaking out of adapters. The session runs on its own single-thread dispatcher, never the main looper.
- All outgoing packets go through `CommandGuard`. Unit tests cover every command ID (allowed and denied), nested `FORWARD_CAN`, CAN ID 255.
- Parsers are table-driven (bit → width, scale, field) with a remaining-bytes guard per field; never dispatch on an exact payload length; unknown layouts are logged raw, never decoded as guesses.
- No allocation in the hot parse path where avoidable (reuse buffers).
- Never cancel the DB writer scope; signal it to drain. Final flushes in `NonCancellable`.
- Port provenance on ported code: `// @source <repo>@<sha7> <path>:<lines> (<license>)`.

## TypeScript / React Native
- `strict` TS, no `any`. No barrel files.
- Never subscribe components to raw telemetry through React state; use the frame shared value (see [performance.md](performance.md)).
- Worklets API: `scheduleOnUI` / `scheduleOnRN` (not the deprecated `runOnUI` / `runOnJS`). Files with `useDerivedValue` start with `'use no memo'`.
- Skia paths from one SVG string, never `moveTo/lineTo` loops.
- Styling via design tokens in one theme file; no inline magic colours.
- Screens compose; logic lives in hooks or modules. `app/` holds route files only; everything else is in `src/`.

## Testing
- Parser and codec: fixture bytes → expected values, per firmware line (5.03, 6.x, 7.x) and per BLE bridge captured. Golden frame test vectors from [ble-protocol.md](../01-research/ble-protocol.md).
- Derivations: table-driven tests using the test vectors in each feature doc (speed, SoC, detection, sag, timing, counters, alerts).
- Chunk codec: round-trip property tests + golden blobs.
- Contract: `contract/telemetry-frame.v1.json` parsed in Kotlin and TS tests.
- Every feature works on a 1-controller and a 2-controller fixture.
- Hardware checklist per release, on the OnePlus test phone: connect, reconnect (power-cycle the bridge), 30 min backgrounded with the screen off, swipe-away during a ride then recovery, all controllers streaming, NRF51 baseline bridge.
