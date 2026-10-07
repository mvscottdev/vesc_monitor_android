# src

React Native code; route files in `app/` only import screens from here.
- `screens/`: dashboard (ride mode, `/`; long-press a page → edit mode), connect (scan, connect, source; long-press the title → bench), bench, source, history, ride detail, storage, speed test, alerts, battery, background.
- `dashboard/`: `layout.ts` (pure grid engine, no overlap), `presets.ts` (default pages for 1..N controllers), `metrics.ts` (worklet widget models from the frame), `geometry.ts` (cells → dp), `dashboard-page.tsx` (one Skia canvas per page), `canvas/` (panel, bars, speed gauge, controller card), `edit.ts` (edit-mode operations, stored layout validation), `dashboard-editor.tsx`.
- `components/`: status strip, alert banner, REC pill, scan list, connection status, button.
- `speed-test/run.ts`: run phase and abort texts, time formatting, saved runs, compare curves (pure, tested).
- `history/series.ts`: ride chart paths and scrub readout; `battery/evidence.ts`: battery confirm evidence lines; `settings/vendors.ts`: phone-maker power-saver steps and the background warn check; `settings/units.ts`: display unit conversion (worklet-safe); `storage/usage.ts`: storage totals and old rides (pure, tested).
- `alerts/texts.ts`: plain-language alert texts by key and the banner choice; `alerts/catalog.ts`: the alert list for the settings screen and availability (pure, tested).
- `telemetry/frame.ts`: the one hot shared value fed by the `telemetry` event; `contract.ts` checks frame v1.
- `stores/session-store.ts`: Zustand warm state from `session`, `scan` and `alerts` events (≤ 5 Hz). `stores/units-store.ts`: display units, loaded once and saved on change.
- `theme/tokens.ts`: GENERATED from `docs/05-design/tokens.json` (`node scripts/design.mjs tokens`); `theme/fonts.ts`: bundled Barlow Semi Condensed + IBM Plex Sans for RN and Skia.
- `lib/`: intents with permission requests, formatting.
