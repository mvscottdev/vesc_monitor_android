# modules/vesc

Expo native module (Kotlin, Android only). Bridge + adapters; logic lives in `core/`.
- `android/build.gradle` compiles `../../../core/src/main/kotlin` into the module.
- `SessionHub`: one session thread; picks BLE / synthetic / replay transport, emits `telemetry` (≤ 30 Hz, while observed), `session` and `scan` (≤ 5 Hz) and `alerts` (on change; the engine runs at ~10 Hz in the background too). The `session` event carries the speed-test `run` state; intents `armRun` / `cancelRun`. `AlertNotifier` posts heads-up notifications while the app is in the background. Phone-side settings in SharedPreferences `vesc-monitor`: disabled alerts, the confirmed pack and capacity sums per vehicle, and `ui.*` keys (dashboard layouts) via `uiSetting` / `setUiSetting`.
- `RideLog`: ride recorder on its own writer thread, saved speed-test runs, `series` (charts downsampled from the chunks). `db/`: Room store, version 2 (`run` table, explicit migration).
- `BackgroundStatus`: live checks (nearby devices, notifications, battery optimisation) and the system battery-optimisation list.
- `NordicBleTransport` + `VescBleManager` + `BridgeProfile`: Nordic BLE library, NUS → FFE0 cascade, MTU 517, 20-byte writes.
- `VescService`: foreground service (`connectedDevice`) for BLE sessions; `START_NOT_STICKY`.
- `CaptureFiles`: JSONL captures in `files/captures`. `app.plugin.js`: manifest permissions.
- JS API: `index.ts` (typed intents), event types in `src/types.ts`.
