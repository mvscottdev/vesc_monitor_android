# core

Pure Kotlin (JVM project, own Gradle wrapper; stdlib + kotlinx-coroutines only). Compiled into `modules/vesc` as sources.
- `protocol/`: CRC, framing, `Reassembler`, `CommandGuard` (only `GuardedFrame`s reach a transport), table-driven parsers.
- `link/`: ports (`BleTransport`, `Clock`), `Link` (request/reply matching, bench numbers), plain-language `Reason`s.
- `session/`: `Discovery` (1..N controllers), `PollScheduler`, `VehicleSession`, `SessionSnapshot`, `LiveValues` (latest values in SI; SETUP speed, battery level and distance at 1 Hz).
- `frame/`: `TelemetryFrame` v1 (contract in `../contract`), combined voltage and values (sum, max, hottest), session peaks, latched fault, `Derivations` (speed, trip, battery, sag per frame).
- `store/`: chunk codec (columnar varints + Deflate, golden blob in `../fixtures/chunks`), `ChunkBuilder`, `SampleStore` port. `ride/`: `EnergyCounter` (reset-safe), `RideRecorder` (one chunk per second per controller, the app's speed per sample), `RideSummary`, `RideSeries` (chart buckets with mean, min and max).
- `alert/`: `AlertRule` / `RuleState` (levels, dwell, hysteresis, cooldown), `AlertCatalog` (defaults), `AlertEngine` (per vehicle and per controller, latched faults).
- `run/`: `RunTimer` (speed-test state machine and abort rules), `RunMath` (rollout, first motion, crossings), `RunReport` (event map).
- `speed/`: `SpeedRatio` (k = speed / ERPM learned from SETUP replies), `SpeedCombiner` (combine rule and slip flag).
- `battery/`: `Ocv` tables, `BatteryDetect` (chemistry and cell-count scoring), `SocEstimator` (IR-compensated SoC filter), `SagTracker` (rest reference, sag, min voltage), `CapacityLearner` (Ah counters between rests), `RangeEstimator` (blended Wh/km, OCV-integral energy left). `Derivations` takes a confirmed pack that replaces detection.
- `replay/`: JSONL captures, `ReplayTransport`, `RecordingTransport`. `sim/`: `FakeVesc`, `SyntheticTransport`, `SimRide` (repeating synthetic ride).
- Test: `core/gradlew -p core test ktlintCheck`. Fixtures from `../fixtures`; regenerate synthetic captures with `-PregenerateFixtures=true`.
