# Tooling — RESEARCHED

## Dev machine (Windows)
- **JDK 17** (Gradle/AGP for RN 0.86 need it; a newer installed JDK is not used). Set `JAVA_HOME` to it.
- **Android Studio** + SDK + NDK 27.1 installed on **D:** (C: is nearly full). Put `ANDROID_HOME` and `GRADLE_USER_HOME` on D: as well.
- Windows long paths on; clone to a short path without spaces (e.g. `D:\dev\vesc`).
- Node 24 + npm. No Bun.
- Dev builds arm64-only to save time and disk.
- APKs come from GitHub Actions (signed release build): download the artifact from the workflow run and install it on the phone. Local builds are optional: `npx expo run:android` (dev client), `cd android && ./gradlew assembleRelease` (release). No EAS.

## Commands
| Task | Command |
|---|---|
| Install deps (Expo-pinned) | `npx expo install <pkg>` |
| Regenerate native project | `npx expo prebuild --clean` (never edit `android/` by hand) |
| Core tests | `core/gradlew -p core test ktlintCheck` (`core\gradlew.bat` on Windows; `core/` has its own wrapper, no Android SDK needed) |
| All checks | `node scripts/check.mjs` (prints failures only + one summary line; `expo-doctor` is reported as skipped when expo.dev is unreachable, e.g. in a sandbox) |
| Scrub a capture | `node scripts/scrub-capture.mjs <in.jsonl> [out.jsonl]` (zeroes MACs and controller UUIDs, fixes CRCs) |
| Health | `npx expo-doctor` |

`scripts/check.mjs` runs: `tsc --noEmit`, ESLint, Prettier check, Jest, core tests + ktlint, contract tests (`contract/telemetry-frame.v1.json` on both sides), `npx expo-doctor`, and the grep guards (no `androidx.room3`, no `fallbackToDestructiveMigration`, no deprecated `runOnUI/runOnJS`).

## Versions to pin (AI tools get these wrong)
Room **2.8.x** in package `androidx.room` (not `androidx.room3`); Reanimated/Worklets/Skia/RNGH at the Expo SDK 57 pins ([stack.md](../01-research/stack.md)); Nordic BLE library 2.11.x.

## Debugging
- `adb logcat -s VescSession:* VescBle:*` (filter by tag, never dump the full log).
- Fake VESC: `ReplayTransport` (JSONL captures in `fixtures/`) and `SyntheticTransport`, selected in dev settings.
- Captures from the phone: record in the app, then `adb pull` the JSONL; zero MACs and controller UUIDs before committing.

## Profiling
Release build on the phone: `adb shell dumpsys gfxinfo <pkg> reset`, ride-mode run, `dumpsys gfxinfo <pkg>` (jank %, p50/p90/p95/p99); Perfetto or the Android Studio profiler for threads; Hermes sampling profiler for the JS thread.
