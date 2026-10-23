# Dependencies

All versions live in `gradle/libs.versions.toml`; no dynamic versions,
no SNAPSHOTs, no version ranges (enforced by the dependency-check
workflow). Versions below were verified against a live Maven repository
at implementation time (2026-10-24).

## Toolchain

| Component | Version | License | Why |
|---|---|---|---|
| Android Gradle Plugin | 9.4.1 | Apache-2.0 | Current stable AGP at implementation time |
| Kotlin | 2.4.21 | Apache-2.0 | Language; Compose compiler plugin ships with it |
| Gradle | 9.8.0 | Apache-2.0 | Build runner (wrapper) |
| JDK | 17 | GPL-2.0 w/ Classpath Exception (Temurin) | AGP 9.x requirement; CI uses `actions/setup-java` Temurin 17 |

SDK: `compileSdk` 37, `targetSdk` 37, `minSdk` 30 (Android 11 — the floor
for TLS-based wireless debugging).

## Runtime (implementation)

| Artifact | Version | License | Why |
|---|---|---|---|
| `androidx.compose:compose-bom` | 2026.09.00 | Apache-2.0 | BOM pins ui, ui-graphics, foundation, material3, material-icons-core/extended to one tested set |
| `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 | Compose integration for the single activity |
| `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | 2.11.0 | Apache-2.0 | ViewModels, lifecycle-aware `StateFlow` collection |
| `androidx.navigation:navigation-compose` | 2.10.2 | Apache-2.0 | 14-destination nav graph + dialog destinations |
| `androidx.datastore:datastore-preferences` | 1.3.0 | Apache-2.0 | Preferences (theme, onboarding, last host/ports) |
| `androidx.core:core-splashscreen` | 1.2.0 | Apache-2.0 | Splash screen branding |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 | Coroutines/Flow for pairing, session I/O, ViewModels |
| `org.bouncycastle:bcprov-jdk18on`, `bctls-jdk18on`, `bcutil-jdk18on` | 1.80 | MIT | TLS for ADB pairing with RFC 5705 keying-material export via public API. The platform Conscrypt exporter is a hidden API blocked for apps targeting recent SDKs (verified failing on-device); BC is the only viable path for the pairing channel binding. |

Notable non-dependencies: **no third-party ADB library** — the pairing
(SPAKE2) and classic ADB transport are hand-rolled in `adb/`
because existing libraries (e.g. `com.tananaev:adblib:1.3`) implement only
plaintext classic ADB and predate Android 11 wireless debugging. The pairing
TLS itself uses BouncyCastle (public API) rather than the platform stack.
See `gradle/aether-adb-deps.txt` and
[adb-pairing-protocol.md](adb-pairing-protocol.md). No analytics, no
networking library, no DI framework (manual `AppContainer`), no Room
(SQLiteOpenHelper suffices for the one-table history).

## Test

| Artifact | Version | License | Why |
|---|---|---|---|
| `junit:junit` | 4.13.2 | EPL-1.0 | Unit tests |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | Apache-2.0 | `runTest` for suspending code |
| `app.cash.turbine:turbine` | 1.2.1 | Apache-2.0 | `StateFlow` / ViewModel state testing |
| `org.json:json` | 20260814 | JSON License | **Unit tests only.** The `android.jar` on the test classpath stubs out `org.json` (every method throws `"Stub!"`); this real implementation shadows the stubs so `ExportInventory` JSON code is testable on the JVM. The APK still uses the framework's `org.json` at runtime — this dependency never ships. |

## CI-only tooling

- **ktfmt 0.65** (native linux-x86_64, SHA-256 checksum-verified in
  `ci.yml`) — Kotlin formatting gate, `--kotlinlang-style`.
- **apksigner** (from build-tools, release workflow) — signs the release
  APK only when the `AETHER_KEYSTORE_*` secrets are present.

## GitHub Actions (pinned SHAs)

All third-party actions are pinned to immutable commit SHAs (release
version kept as a trailing comment in each workflow):

- `actions/checkout` (v7.0.1), `actions/upload-artifact` (v7.0.2),
  `actions/download-artifact` (v8.0.2)
- `gradle/wrapper-validation-action` (v3.5.0), `gradle/actions/setup-gradle` (v6.4.0)
- `actions/setup-java` (v6.1.0), `android-actions/setup-android` (v4.0.4)

## Maintenance

- The weekly **dependency-check** workflow fails on dynamic versions and
  publishes the resolved `releaseRuntimeClasspath` for review. It does
  **not** run a vulnerability scan: osv-scanner's Java support needs a
  Gradle lockfile, which the project does not have yet. Adding
  `--write-locks` output is the recommended follow-up before enabling a
  scan.
- License compatibility is reviewed manually at upgrade time; every
  current dependency is Apache-2.0 or EPL-1.0 (test-only JUnit).
