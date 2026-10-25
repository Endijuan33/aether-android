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
| `androidx.navigation:navigation-compose` | 2.10.2 | Apache-2.0 | 15 destinations including two dialogs |
| `androidx.datastore:datastore-preferences` | 1.3.0 | Apache-2.0 | Preferences (theme, onboarding, last host/ports) |
| `androidx.core:core-splashscreen` | 1.2.0 | Apache-2.0 | Splash screen branding |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 | Coroutines/Flow for pairing, session I/O, ViewModels |
| `org.conscrypt:conscrypt-android` | 2.8.0 | Apache-2.0 | Public TLS exporter, minSdk 21 and 16-KiB-aligned native libraries; replaces 2.5.2 |

Notable non-dependencies: **no third-party ADB library** — the pairing
(SPAKE2) and classic ADB transport are hand-rolled in `adb/`
because existing libraries (e.g. `com.tananaev:adblib:1.3`) implement only
plaintext classic ADB and predate Android 11 wireless debugging. The pairing
TLS uses standalone Conscrypt’s public exporter; transport TLS uses the platform stack.
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

| `org.bouncycastle:bcprov-jdk18on`, `bctls-jdk18on`, `bcutil-jdk18on` | 1.85 | MIT | JVM TLS fixtures only; 1.80 had matching published OSV advisories |
| AndroidX test runner / ext JUnit | 1.7.0 / 1.3.0 | Apache-2.0 | Device-test infrastructure |
| Compose ui-test-junit4 / ui-test-manifest | BOM-aligned | Apache-2.0 | Instrumentation UI tests; manifest helper is debug-only |

## Build/CI-only tooling

- **ktfmt 0.65** (Java fat JAR, checksum-verified by Gradle’s `verifyKtfmt`) — Kotlin formatting gate, `--kotlinlang-style`.
- **apksigner** (from build-tools, release workflow) — signs the release
  APK only when the `AETHER_KEYSTORE_*` secrets are present.

## GitHub Actions (pinned SHAs)

All third-party actions are pinned to immutable commit SHAs (release
version kept as a trailing comment in each workflow):

- `actions/checkout` (v7.0.1), `actions/upload-artifact` (v7.0.2),
  `actions/download-artifact` (v8.0.2)
- `gradle/wrapper-validation-action` (v3.5.0), `gradle/actions/setup-gradle` (v6.4.0)
- `actions/setup-java` (v6.1.0), `android-actions/setup-android` (v4.0.4)
- `ReactiveCircus/android-emulator-runner` (immutable v2 commit a421e43855164a8197daf9d8d40fe71c6996bb0d)

## Maintenance

- Dependency locks and Gradle SHA-256 verification pin resolved artifacts. Checksum metadata is bootstrapped from resolved repository artifacts; it detects changes after acceptance, but is not independent signature attestation. Wrapper JAR/distribution and ktfmt digests were compared separately with publisher data.
- CI and the weekly dependency workflow export the actual resolved release Maven coordinates, then query OSV using `scripts/check_dependency_advisories.py`. Matching advisories and service/network failures fail the check. No matching advisory is not a guarantee of safety. Build-tool/test dependencies have a separate review boundary; the selected BC security fixtures were also queried.
- Update locks/checksums only after reviewing coordinate changes and publisher evidence, using the full build command with `--write-locks --write-verification-metadata sha256`; then rerun in strict mode without those options.
- Runtime dependencies are Apache-2.0. Test-only JUnit uses EPL-1.0, BouncyCastle uses MIT and org.json uses the JSON License. Android native/third-party notices are included under `app/src/main/assets/licenses`; upstream native licenses must be retained when preparing distribution.

Notification pairing uses AndroidX NotificationCompat/RemoteInput already present in the locked runtime graph and platform NSD/foreground-service APIs. The redesign adds no runtime/test dependency and leaves dependency versions and verification metadata unchanged.

### CI verification coverage (2026-10-26)

CI exposed missing verification entries for the Linux AAPT2 JAR/POM
(`9.4.1-15978811`), Guava parent POM (`33.4.0-jre`), JUnit BOM module
(`5.11.0-M2`) and Kotlin Gradle plugins BOM module/POM (`2.2.10`). These are
artifacts of the existing build graph, not dependency upgrades. Their SHA-256
entries were added without changing the locked versions or verification policy.

The AAPT2 artifacts were downloaded from `https://dl.google.com/dl/android/maven2`
and compared with Google's SHA-256 sidecars. The BOM/parent metadata came from
`https://repo.maven.apache.org/maven2`; its available SHA-256 or SHA-1 sidecars
were checked before calculating the recorded SHA-256 hashes. Repository sidecars
establish consistency with the publisher's hosted files, not independent
signature attestation. Local cache/build success alone does not establish that
all metadata and platform tools needed by a GitHub runner have verification entries.
