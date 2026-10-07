# Ride logging & history — RESEARCHED



## Requirements
- **Logging toggle**: quick button on the dashboard + default in settings. Off = nothing recorded except speed-test runs; live dashboard and alerts keep working.
- A ride starts when logging is on, connected and moving (or manually); it ends after X minutes idle, on disconnect, or when the app is closed.
- Log **every polled sample** of every controller, plus GPS fixes when GPS is on (at the phone's rate).
- Summary per ride: distance, duration, max/avg speed, Wh used and regenerated, Wh/km, min voltage, peak sag, max temps, peak power, peak battery current, faults, alerts, GPS route thumbnail.
- History UI: ride list, ride detail with charts (speed/power/voltage/temps vs time or distance), GPS route if recorded, storage screen.
- No backup or export in v1; the DB is excluded from Android backup (`allowBackup=false` or extraction rules).

## Storage model
- SQLite via **Room 2.8.x** (`androidx.room`, KSP; not `androidx.room3`), owned only by the service, WAL. One writer coroutine on a single-thread dispatcher.
- **Chunks:** one row per second per stream (stream = controller 0..N-1, 100 = GPS in 10 s chunks). Columnar, per column raw / delta / delta-of-delta zigzag varints (smallest wins), whole payload Deflate. Header carries `codec` version and field IDs, so a new telemetry field needs no DB migration. Keep a chunk payload ≤ ~1.3 KB (larger rows waste pages).
- **Stored per sample:** `t_ms` (delta), `v_in`, `cur_motor`, `cur_in`, `erpm`, `duty`, `temp_fet`, `temp_motor`, `fault`, plus the canonical speed the user saw (`speed_cms`). Integer-scaled exactly as on the wire. `id`/`iq` and extra MOSFET temps are an advanced toggle, off by default (+34 %).
- **Keyframes once per chunk:** VESC Ah, Ah charged, Wh, Wh charged, `tachometer_abs` → exact ride energy and distance as last − first (a decrease = VESC reboot, handled as a new segment).
- **Recomputed, never stored:** power, distance, Wh/km, sag.
- **Budget:** ~11 B × controllers × Hz per second of riding; measured 1.6 MB/h for 2 controllers × 20 Hz (synthetic data; real noise may move it ±30 %).

Schema: `vehicle_profile`, `capture` (ride or run; `state` recording/closed/recovered, `keep_full`, `tier0_present`, `codec_ver`, profile snapshot, byte counters), `capture_stream`, `chunk` (`capture_id, stream, tier, t0_ms, n, codec, data`; unique index), `ride` (summary columns), `run` + `run_split`, `alert_event`, `setting`.

## Tiers and compression
- **Tier 0** = raw chunks. **Tier 1** = 1 Hz avg/min/max of `v_in`, `cur_motor`, `cur_in`, `erpm`, `duty`, max temps, OR of faults, avg/max speed, in 60 s chunk rows. Tier 1 and the summary are **built when the ride closes** (and on recovery), from tier 0.
- **Compression after 7 days** (configurable) = delete tier-0 chunks of captures with `keep_full = 0`, then `PRAGMA incremental_vacuum` in ~2000-page slices, never while recording. Idempotent and crash-safe. "Compress now" runs the same job with 0 days.
- Tier 1 is kept forever (~190 MB/year at 1.5 h/day). Summaries stay exact.
- `auto_vacuum=INCREMENTAL` set once in `onOpen` (+ one `VACUUM` while the DB is empty). Full `VACUUM` only from a manual "Optimise storage" with a free-space check (needs up to 2× the file).
- Compaction runs in the service: at start, after a ride closes when idle, or manually. No WorkManager.

## Speed-test runs
A run is its own capture with `keep_full = 1`, created at run end by copying the run window's chunks (~60 KB for 30 s). Works with logging off and is never compressed.

## Crash safety
- Commit one transaction per second (chunk boundary): at most ~1 s lost on app kill.
- `synchronous=FULL` (one fsync per second) unless on-phone timing shows a problem; fallback NORMAL + `wal_checkpoint(PASSIVE)` every 30 s.
- `onTaskRemoved`: stop accepting samples, flush builders, close the capture in `NonCancellable`, write summary + tier 1, checkpoint, stop. Never cancel the writer scope; signal it to drain.
- **Recovery is the guarantee:** on service start, any capture still `recording` becomes `recovered`: end time from the last chunk, summary + tier 1 built from tier 0, shown in history as "ended unexpectedly" with the `ApplicationExitInfo` reason.
- Bounded sample queue; on overflow record an `alert_event` (`dropped_samples`), never drop silently.

## Storage limit
- Default **1 GB**, soft. Used bytes = size of db + wal + shm files. Per-ride size from the capture byte counters. "Hours left" = remaining bytes / average bytes-per-hour of the last 5 rides.
- 80 %: info banner. 95 %: persistent alert with "Compress now", "Delete oldest…", "Raise limit". 100 %: critical alert every 10 min, **logging continues**.
- Hard guard: device free space below max(200 MB, 2 × DB size) → stop logging with a loud alert (a full disk breaks transactions).
- Size, count and age limits produce **proposals** the user confirms; nothing is deleted silently.
- Storage screen: used, per-ride size, hours left, compress now, delete ride/run, delete all.

## Chart queries
- `getRideSeries(id, from, to, buckets)`: **min/max bucketing (M4)** with one bucket per pixel column; returns avg, min and max per bucket. Bucket ≥ 1 s → tier 1; smaller (zoomed in) → decode tier 0 for that window only. No other pre-aggregated levels.
- Response: JSON integer arrays + scale factors in `meta` (v1); an `ArrayBuffer` variant can replace it behind the same call.
- Runs as an Expo `AsyncFunction`; superseded requests are cancelled; the open ride's tier 1 is cached in memory. Multi-ride trends use `ride` summary columns only.
- Queries are short and never hold a read transaction open (WAL checkpoint starvation).

## Guardrails (in `scripts/check`)
No `fallbackToDestructiveMigration`; `exportSchema = true` with a checked-in `schemas/` dir and a migration test per version; no mixing `androidx.room` and `androidx.room3`; chunk codec in `core/` with golden fixtures and round-trip tests; kill -9 during a replayed ride in the smoke test must recover the ride.

## Built so far
- Room store (one row per ride, per-second chunks per controller), crash recovery, exact summaries from counter deltas.
- History (as the mockup): a table with the ride's day and time (Today / This week / ended unexpectedly), a speed sparkline, distance, time, max speed, Wh/km (net, from 500 m) and an alert count, filled per row from a 40-bucket series of the ride; Rides / Speed runs switch; storage used (tap for Storage). Long-press a ride to delete it after a confirm.
- Each sample also stores the app's speed for that controller (field 9, m/s × 100, missing while the speed ratio is not trusted); older rides simply lack it.
- Ride detail (tap a ride, as the mockup): title with controller count, chart tabs and Delete at the top; tiles for distance, time, avg · max speed, Wh used and regen, min voltage, peak kW and max temperatures; round gridlines in display units, a minutes or distance axis, a floating scrub tooltip. Earlier notes: summary tiles, Speed (when stored) / Power / Voltage / Temps / Duty charts as a mean line over a min/max band per time bucket (about 400 buckets, computed natively from the chunks so spikes stay visible), and a scrub readout of every metric at the touched time.
- Storage screen (History → Storage): space used by rides, hours recorded and measured bytes per hour, saved speed-test runs, and "delete rides older than 7 / 30 / 90 days" after a confirm with count and size (the open ride is never deleted).
- Each sample also stores the most severe alert active for that controller (field 10, alert key index × 4 + severity); ride detail marks where each alert started with a tick on the chart and a chip (time and title, tap to scrub there).
- Ride charts can switch between time and distance on the x axis ("By time" / "By distance") when the ride stored speed; distance is integrated from each bucket's mean speed, and the readout then also shows the distance.
- Settings (Connect → Settings, as the settings mockup): sections Display & units and Ride logging (record rides on/off) edit in place; Storage, Run in background (warn dot when something can stop the app), Data source and Developer open their screens.
- Ride start and end: with logging on and connected, a ride opens when the vehicle moves (speed above 0.5 m/s, or a controller turning above 500 ERPM, so a stand test counts) and closes after 10 minutes without motion; the next motion starts a new ride. Turning logging on while connected still opens a ride at once (manual start). Disconnecting ends the ride; a reconnect keeps it.
- Hard storage guard: when the phone's free space drops below max(200 MB, twice the database), checked every 30 s while connected, the open ride is closed and no new ride starts until space is freed; Settings › Ride logging says so.
- Not yet: pinch zoom, GPS route, the soft 1 GB limit with its banners, and old-ride compression.
