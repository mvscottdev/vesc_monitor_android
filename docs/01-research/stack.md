# Stack — RESEARCHED

Versions checked 2026-09-29. Stay on the **Expo-pinned** versions (`npx expo install`, `expo-doctor` in check), even when npm has newer ones. Re-evaluate at Expo SDK 58 stable.

| Layer | Choice | Version |
|---|---|---|
| App framework | Expo SDK 57, CNG/prebuild, `expo-dev-client`, release APKs built by GitHub Actions, local Gradle builds optional, no EAS. `android/` is generated, gitignored, never hand-edited | `expo ~57.0.26` (≥ 57.0.17) |
| RN / React | New Architecture, Hermes | RN 0.86.3 / React 19.2.3 |
| UI language | TypeScript strict | `~6.0.3` |
| Native language | Kotlin + coroutines/Flow; AGP 8.12, Gradle 9.3.1, JDK 17, NDK 27.1 | Kotlin 2.1.20; set `kotlinVersion` and `kspVersion` explicitly (Expo's module default is 2.0.21) |
| Pure core | Standalone Gradle JVM project `core/`, compiled into the module via `srcDirs`; stdlib + `kotlinx-coroutines-core` only | Kotlin 2.1.20, JUnit |
| Native module | Expo Modules API (Kotlin) in `modules/vesc` | `expo-modules-core ~57.0.20` |
| BLE | Nordic Android-BLE-Library + `ble-ktx` behind `BleTransport` | 2.11.x |
| Native → JS live | Expo `sendEvent(Map)`, latest-wins ≤ 30 Hz, observer-gated | — |
| Native → JS series | JSON integer arrays + scale (v1); `NativeArrayBuffer` behind the same call if needed | — |
| State | shared values (hot) + Zustand (warm) + native queries (cold) | `zustand 5.0.15` |
| Animation | Reanimated + Worklets (use `scheduleOnUI` / `scheduleOnRN`) | 4.5.1 / 0.10.1 |
| Drawing | `@shopify/react-native-skia`, one canvas per page/cluster | 2.6.2 |
| Charts | In-house Skia line chart over native min/max buckets (modelled on vescape `charts/line`); `victory-native` only for simple bar charts | `victory-native 42.0.1` (optional) |
| Gestures | react-native-gesture-handler | ~2.32 |
| Grid | Custom pure-TS layout engine + RNGH + Reanimated; `react-native-pager-view` for pages; `react-native-sortables` for settings lists | pager-view 8.0.2 |
| Glass | Static-backdrop glass in Skia; real blur only for overlays (Skia `BackdropBlur` or `expo-blur`) | `expo-blur ~57.0.3` |
| Navigation | Expo Router | `~57.0.24` |
| Storage | Room in the Kotlin service, per-second chunks | Room 2.8.x (`androidx.room`, KSP) |
| Location | Kotlin service `GpsSource` = `LocationManager` GPS + `GnssStatus`; FLP optional later | — |
| Device UX | `orientation: landscape`, `expo-keep-awake`, `expo-navigation-bar` `setHidden`, hidden status bar, edge-to-edge insets | `~57.0.x` |
| Tests | JUnit in `core/`; Jest (`jest-expo`); RNTL sparingly; Maestro later | `jest-expo 57.0.5`, RNTL 14.0.1 (fall back to 13 if it clashes) |
| Lint / format | ESLint (`eslint-config-expo`) + Prettier; ktlint; detekt later | ktlint 1.8.0 |
| Package manager | npm + Node 24 | — |

Not used: expo-location (second FGS), expo-sqlite / op-sqlite (second writer), Nitro / JSI buffers (escape hatch only if spike S1 fails), Jotai, Legend-State, `expo-glass-effect` (iOS only), AGSL refraction, Room 3 (revisit at v1.1).

## Open spikes (need the phone and toolchain)
S1 event cost · S2 canvas strategy at 120 Hz · S3 glass cost · S4 `core/` + module build end to end (also confirms Kotlin/KSP versions) · S5 grid gestures.
