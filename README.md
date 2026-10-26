# AETHER — System Control

**One device. Total control.**

AETHER is a native Android app for wireless ADB connection, installed-package inspection, and system-app debloating in one place. It ships its own hand-rolled Android 11+ wireless-debugging client (TLS + SPAKE2 pairing, classic CNXN/OPEN/WRTE/CLSE ADB transport) with zero third-party ADB libraries, and a package manager backed by Android's `PackageManager` API.

- Repository: https://github.com/Endijuan33/aether-android
- applicationId: `id.mybase.aether`
- Version: 0.1.0 (versionCode 1)
- minSdk 30 (Android 11), targetSdk / compileSdk 37
- Kotlin + Jetpack Compose + Material 3, single `:app` module, Gradle Kotlin DSL
- License: Apache-2.0 (see [LICENSE](LICENSE))

## Features

- **Notification Wireless Debugging pairing** — tap Pair & Connect, open Android’s pairing-code screen, and submit the six digits through AETHER’s native notification inline reply. NSD discovers the separate pairing/connect services; shell UID 2000 is verified before the dashboard shows Connected. Android notification/local-network permission failures have explicit recovery and an in-app discovery fallback. Manual endpoints are under advanced settings. The existing TLS + SPAKE2 engine is retained; the V3 AndroidKeyStore identity corrects its raw RSA signing authorizations. A user-supplied POCO F6 / Android 16 / HyperOS trace verifies pairing, authenticated transport and real shell UID 2000. Broader hardware validation remains pending.
- **ADB shell, restricted** — classic ADB session tunnelled over TLS; shell
  execution permits only shell UID checks and fixed user-0 package queries/mutations. Remote-device endpoints are rejected.
- **Package inventory** — enumerates installed packages via `PackageManager`
  (`QUERY_ALL_PACKAGES`); shows label, version, install state, permissions,
  install times; classifies into user / system / disabled / updated-system.
- **Debloat engine** — single and multi-package selection, per-package risk
  preview (name, package id, classification, operation, risk level,
  reversibility), then executes `pm uninstall --user 0`, `pm disable-user`,
  or `pm enable`, always with explicit `--user 0`. Plans are serialized; partial failures continue, cancellation stops the plan, and successful results require state verification.
- **Safety catalogue** — 52 curated entries (15 ESSENTIAL / 17 RISKY /
  20 SAFE); packages without an entry are shown as **unknown**, never guessed.
  See [docs/catalogue-provenance.md](docs/catalogue-provenance.md).
- **Operation history** — every executed operation (success, failure, skip)
  recorded with a timestamp in a local SQLite database.
- **Inventory export** — JSON export through the Android document picker; a reference snapshot, not an APK/data backup.
- **Preferences** — DataStore-backed (theme, onboarding, last host/ports).
- **Theming** — dark-graphite default with electric cyan accent, optional
  light theme; original vector adaptive launcher icon.

## Quick start

Requirements: JDK 17, Android SDK with API 37 platform and build-tools.

```bash
./gradlew assembleDebug     # build the debug APK
./gradlew testDebugUnitTest # run unit tests
./gradlew lintDebug lintRelease # run both Android lint variants
./gradlew ktfmtCheck        # same formatting gate as CI
./gradlew assembleRelease   # unsigned, R8/resource-shrunk release
./gradlew assembleDebugAndroidTest # compile device tests
```

Continuous integration runs formatting checks (ktfmt), unit tests, lint and
the debug APK build on every push/PR to `main`; see `.github/workflows/ci.yml`.
Releases are built from `v*` tags via `.github/workflows/release.yml` —
signing happens only when the `AETHER_KEYSTORE_*` secrets are configured,
otherwise the release is honestly reported as unsigned.

## Project status

| Area | Status | Validation |
|---|---|---|
| Navigation, 15 destinations plus splash, theme | Implemented | Local validation tracked in engineering audit |
| Wireless pairing (TLS + SPAKE2, 6-digit code) | Implemented | User-supplied POCO F6 / Android 16 / HyperOS trace verifies TLS/exporter/SPAKE2/peer-info after V3; broader device/lifecycle checks pending |
| ADB session + restricted shell (fixed allowlist) | Implemented | Same physical trace verifies authenticated transport and real shell UID 2000; package mutations and broader scenarios pending |
| Package inventory & classification | Implemented | Logic unit-tested; on-device behavior pending hardware |
| Debloat operations (uninstall-for-user, disable, enable) | Implemented | Logic unit-tested; on-device behavior pending hardware |
| Safety catalogue (52 entries, r1) | Shipped | Curated; see provenance doc for limits |
| Operation history (SQLite) | Implemented | JVM history tests; platform SQLite test added, device execution pending |
| Inventory export, preferences | Implemented | Serialization/preferences unit-tested; document-provider/device validation pending |
| Debug APK via CI | Workflow configured | Baseline CI passed; hardening changes require a new remote run |
| Release APK, signing | Production signing not configured | Release APK is unsigned; the 2026-10-27 audit confirmed all four repository signing secrets absent |
| mDNS discovery + native notification input | Implemented | POCO F6 physical trace verifies actual pairing/connect NSD and native inline reply; other OEMs and instrumentation pending |

Distinction that matters: a green CI build proves the code compiles and the
tests pass. It does **not** prove pairing or `pm` operations work on a real
device. A successful POCO F6 run is recorded; broader hardware validation is tracked in
[docs/limitations.md](docs/limitations.md).

## Docs

- [docs/notification-pairing-redesign.md](docs/notification-pairing-redesign.md) — notification architecture, reference research, validation and migration

- [docs/engineering-audit.md](docs/engineering-audit.md) — findings, fixes, validation evidence and residual risks
- [docs/pre-checkpoint-validation-report.md](docs/pre-checkpoint-validation-report.md) — latest checkpoint audit, exact validation results, signing setup and artifact integrity
- [docs/architecture.md](docs/architecture.md) — modules, layers, ADB design, state models, DI, persistence
- [docs/adb-pairing-protocol.md](docs/adb-pairing-protocol.md) — pairing protocol research and library decision
- [docs/wireless-debugging-setup.md](docs/wireless-debugging-setup.md) — user guide: enable Wireless debugging, pair, connect
- [docs/limitations.md](docs/limitations.md) — platform support, privilege limits, hardware-validation status
- [docs/troubleshooting.md](docs/troubleshooting.md) — pairing/connection failures, redacted-log guidance
- [docs/catalogue-provenance.md](docs/catalogue-provenance.md) — safety catalogue taxonomy, sources, update policy
- [docs/dependencies.md](docs/dependencies.md) — every dependency, version, license, rationale
- [CONTRIBUTING.md](CONTRIBUTING.md) — build, test, style, PR expectations
- [SECURITY.md](SECURITY.md) — threat model and reporting
- [PRIVACY.md](PRIVACY.md) — data handling
- [CHANGELOG.md](CHANGELOG.md) — release notes
