# Architecture — RESEARCHED

```
┌──────────────── React Native (TS) ──────────────────────┐
│ Screens: Dashboard · Speed test · History · Settings    │
│ Hot: one shared value ← live frame (≤30 Hz, no renders) │
│ Warm: Zustand (≤5 renders/s) · Cold: native queries     │
│ Intents → native module (connect, armRun, setLogging…)  │
└───────────────▲─────────────────────────┬───────────────┘
  TelemetryFrame events (latest-wins, ≤30 Hz,   │ typed intents
  only while observed) + snapshot pull          │
┌───────────────┴─────────────────────────▼───────────────┐
│ Expo native module (Kotlin): bridge only                │
├─────────────────────────────────────────────────────────┤
│ Foreground service: VehicleSession (own thread)         │
│  adapters: NordicBleTransport · Replay/SyntheticTransport│
│            LocationManagerGpsSource · Room DB · Notifier │
│  core (pure Kotlin, JVM project):                        │
│   Reassembler · Codec/CRC · CommandGuard · Parsers       │
│   Discovery · PollScheduler · Derivations (speed, SoC,   │
│   sag, power, combine) · SpeedTestEngine · AlertEngine   │
│   ChunkCodec · MinMaxDecimator                           │
│  RideRecorder → Room (1 chunk row / s / stream, WAL)     │
└─────────────────────────────────────────────────────────┘
```

## Principles
- **The session is independent of the JS lifecycle.** The service starts from the visible app, owns BLE, GPS, polling, derivations, alerts and storage. The UI attaches to a running session: it pulls `getLiveState()` (current frame + a short recent window) on start/foreground, then follows events. Every event carries the session `generation`, so late events from a dead session are ignored.
- **Calculations happen once, natively.** JS never recomputes telemetry.
- **Session work runs on a dedicated thread** (single-thread dispatcher), never the main looper.
- **Ports and adapters.** `core/` is a standalone Gradle JVM project (Kotlin stdlib + `kotlinx-coroutines-core` only), compiled into the Expo module via `sourceSets.main.java.srcDirs`. `import android.*` in core fails to compile. Ports in core: `BleTransport`, `GpsSource`, `Scheduler`, `Clock`, `EventSink`, `SampleStore`. Adapters live in `modules/vesc/`.

## Seams (each has a contract and tests)
| Seam | Contract |
|---|---|
| BLE | `BleTransport` ([ble-connection.md](../02-features/ble-connection.md)); replay format = JSONL (`meta`, `session-state`, `ble-chunk {direction, base64}`, `t` ms) |
| Wire | [ble-protocol.md](../01-research/ble-protocol.md); every outgoing packet passes `CommandGuard` |
| Native → UI | `TelemetryFrame v1`: flat Map event with `v`, `seq`, `tMs`, `generation`, combined block, `vescs[]` (≤ N small maps, numbers and int enums only). Emitted by a native timer, latest-wins, ≤ 30 Hz, only while JS observes. Golden sample `contract/telemetry-frame.v1.json` parsed in Kotlin and TS tests |
| Series | `getLiveSeries` (~1 Hz, natively decimated) for sparklines; `getRideSeries(id, from, to, buckets)` returns avg/min/max per bucket as JSON integer arrays + scale (v1), same signature can switch to `NativeArrayBuffer` |
| DB | Room schema version + exported schemas + a migration test per version ([ride-logging.md](../02-features/ride-logging.md)) |
| GPS | `GpsSource`: platform `LocationManager` GPS provider (+ network backup), `GnssStatus` satellite counts, `elapsedRealtimeNanos` timestamps. No expo-location (it would start a second service) |

## Session flow
1. Connect ([ble-connection.md](../02-features/ble-connection.md)) → discovery ([vesc-topology.md](../02-features/vesc-topology.md)) → polling.
2. Each reply: reassemble → decode → guard-checked match → parse → timestamp (arrival − RTT/2) → derive → (a) alert engine, (b) ride recorder buffer, (c) latest frame slot.
3. Recorder commits one chunk per second per stream; on close it builds the 1 Hz tier and the summary; unfinished rides are recovered on next start.
4. `onTaskRemoved`: finish the ride save (3-5 s bound), stop. No self-restart.
