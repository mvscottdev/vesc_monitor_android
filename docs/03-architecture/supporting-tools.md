# Supporting tools — APPROVED

Repo layout, the native→UI data protocol, the database, dev tooling and QA, each with the reason for it.

## P1. Repo layout
```
app/                 Expo Router route files only (thin screens); Expo project root = repo root
src/                 React Native components, widgets, stores, theme
core/                standalone Gradle JVM project (stdlib + kotlinx-coroutines): codec, parsers,
                     derivations, alert rules, chunk codec, decimation (no android.*)
modules/vesc/        Expo native module: bridge + Android foreground service (adapters);
                     android/build.gradle adds ../../../core/src/main/kotlin to srcDirs
fixtures/            packet captures (JSONL) + expected outputs (shared by all tests)
contract/            telemetry-frame.v1.json (golden sample)
scripts/             check.mjs, replay, fixture tools
docs/                source of truth
```
Why: every concern has one obvious place (Expo Router would turn anything else in `app/` into a route), so Claude finds files without searching and a rewrite of one folder stays local. `core/` has no Android SDK on its classpath, so `import android.*` fails to compile, and its tests run in seconds without prebuild. Guard against JDK API drift with `-Xjdk-release` and keep Kotlin versions equal on both sides (spike S4 proves the build). Kotlin requires `-Xjdk-release` to equal `jvmTarget`, so `core/` builds standalone with both at 11; the app compiles the same sources with jvmTarget 17.

## P2. Fake VESC (replay transport) — strongly recommended
- `BleTransport` is an interface. `ReplayTransport` feeds recorded packet logs at real timing. A `SyntheticTransport` generates scripted rides: 1 or N VESCs, sag, overtemp, disconnects.
- Selected in a dev setting.
- Why: **the Android emulator has no BLE.** Without replay, every UI change needs the scooter. With it, 90% of the app can be built and tested at a desk. The same logs also become regression tests.

## P3. Native → UI protocol ("render protocol")
- Expo Modules event `telemetry` (a `Map`, not a JSON string) at ≤ 30 Hz, emitted by a native timer **latest-wins**, only while JS observes. Each event carries a **`TelemetryFrame`**: a flat object with `v` (contract version), `seq`, `tMs`, `generation`, a combined vehicle block, and a `vescs[]` array with one small entry per controller. Numbers and int enums only.
- On start/foreground the UI pulls `getLiveState()` (frame + short recent window), then follows events. Sparklines use a natively decimated ~1 Hz series.
- Values are already derived and in SI units. The UI converts only for display units.
- The UI writes frames into one store. Widgets read through Reanimated shared values, not React state.
- Commands from the UI are a small typed set of **intents** (`connect`, `armRun`, `setLogging`, …), never raw VESC commands.
- Contract file: `contract/telemetry-frame.v1.json`. Kotlin and TS tests both parse it. A change means a new version plus both tests.
- Why: JSON events are easy to read, debug and log. Zero-copy binary (JSI buffers) is only worth it if profiling shows the bridge is the bottleneck.

## P4. Database (spec in [ride-logging.md](../02-features/ride-logging.md))
- **SQLite owned only by the Kotlin service, via Room 2.8.x.** The UI never opens the file. It asks the native module for queries (`listRides`, `getRideSeries(id, from, to, buckets)`), and series come back already decimated (min/max buckets, not LTTB).
- Samples are stored as **per-second compressed chunks per stream**, not one row per sample: measured 1.6 vs 7.5 MB/h at 2 controllers × 20 Hz, 20× fewer rows. A 1 Hz tier is built at ride close, so old-ride compression is a delete + incremental vacuum.
- One transaction per second. Exported schemas + a migration test per version.
- Why: Room is the most common Android DB, so AI tools write it correctly. A single writer (the service) means no locking bugs between JS and Kotlin.

## P5. QA gates (from cheapest to most expensive)
| Gate | When | Tool |
|---|---|---|
| Auto-format | every edit | Claude Code hook: Prettier / ktlint |
| `scripts/check.mjs` | before every commit | cross-platform Node script: `tsc` + ESLint + Prettier + Jest + `core/gradlew -p core test ktlintCheck` + contract tests + grep guards + `expo-doctor`; prints failures only ([tooling.md](../04-rules/tooling.md)) |
| Fixture/golden tests | in check | captured packets → expected decoded JSON, per firmware version |
| Contract tests | in check | `telemetry-frame.v1.json` parsed on both sides |
| Replay smoke | per slice with UI | app on the emulator with `ReplayTransport`, screenshot check |
| `/code-review` | before merging a big slice | Claude Code |
| Hardware checklist | per release | coding-rules.md → Testing |

Optional later: Maestro (YAML UI flows) for repeatable UI smoke tests on the emulator.
