# AETHER — System Control

**One device. Total control.**

AETHER is a native Android app for wireless ADB connection, installed-package inspection, and system-app debloating in one place. It ships its own hand-rolled Android 11+ wireless-debugging client (TLS + SPAKE2 pairing, classic CNXN/OPEN/WRTE/CLSE ADB transport) with zero third-party ADB libraries, and a package manager backed by Android's `PackageManager` API.

- Repository: https://github.com/Endijuan33/aether-android
- applicationId: `id.mybase.aether`
- Version: 0.1.0 (versionCode 1)
- minSdk 30 (Android 11), targetSdk / compileSdk 37
- Kotlin + Jetpack Compose + Material 3, single `:app` module, Gradle Kotlin DSL
- License: Apache-2.0 (see [LICENSE](LICENSE))

> **Screenshots.** No screenshots are bundled yet. The placeholder below marks
> where real device screenshots go once hardware validation happens.
>
> ![Dashboard](docs/screenshots/dashboard.png) *placeholder — not yet captured*

## Features

- **Wireless-debugging pairing** — guided onboarding, 6-digit pairing dialog
  (paste, digit filtering, validation), progress and error states. TLS + SPAKE2
  pairing with per-client RSA-2048 identity stored in AndroidKeyStore.
- **ADB shell, restricted** — classic ADB session tunnelled over TLS; shell
  execution is limited to `pm` and `cmd` with structured arguments only.
- **Package inventory** — enumerates installed packages via `PackageManager`
  (`QUERY_ALL_PACKAGES`); shows label, version, install state, permissions,
  install times; classifies into user / system / disabled / updated-system.
- **Debloat engine** — single and multi-package selection, per-package risk
  preview (name, package id, classification, operation, risk level,
  reversibility), then executes `pm uninstall --user 0`, `pm disable-user`,
  or `pm enable`. Strictly sequential, partial failures never abort the plan.
- **Safety catalogue** — 52 curated entries (15 ESSENTIAL / 17 RISKY /
  20 SAFE); packages without an entry are shown as **unknown**, never guessed.
  See [docs/catalogue-provenance.md](docs/catalogue-provenance.md).
- **Operation history** — every executed operation (success, failure, skip)
  recorded with a timestamp in a local SQLite database.
- **Inventory export** — JSON export of the package snapshot for backup.
- **Preferences** — DataStore-backed (theme, onboarding, last host/ports).
- **Theming** — dark-graphite default with electric cyan accent, optional
  light theme; original vector adaptive launcher icon.

## Quick start

Requirements: JDK 17, Android SDK with API 37 platform and build-tools.

```bash
./gradlew assembleDebug     # build the debug APK
./gradlew testDebugUnitTest # run unit tests
./gradlew lintDebug         # run Android lint
```

Continuous integration runs formatting checks (ktfmt), unit tests, lint and
the debug APK build on every push/PR to `main`; see `.github/workflows/ci.yml`.
Releases are built from `v*` tags via `.github/workflows/release.yml` —
signing happens only when the `AETHER_KEYSTORE_*` secrets are configured,
otherwise the release is honestly reported as unsigned.

## Project status

| Area | Status | Validation |
|---|---|---|
| Project scaffold, navigation, 14 screens, theme | Done | Compiles; CI lint/format enforced |
| Wireless pairing (TLS + SPAKE2, 6-digit code) | Implemented | **Not validated against real hardware** — no physical device in CI; SPAKE2 verified against BoringSSL oracle vectors |
| ADB session + restricted shell (`pm`/`cmd` only) | Implemented | Same as above — transport exercised only via socket-based test fakes |
| Package inventory & classification | Implemented | Logic unit-tested; on-device behavior pending hardware |
| Debloat operations (uninstall-for-user, disable, enable) | Implemented | Logic unit-tested; on-device behavior pending hardware |
| Safety catalogue (52 entries, r1) | Shipped | Curated; see provenance doc for limits |
| Operation history (SQLite) | Implemented | SQLiteOpenHelper path unit-tested |
| Inventory export, preferences | Implemented | Unit-tested |
| Debug APK via CI | Done | `assembleDebug` in CI on every PR |
| Release APK, signing | Workflow ready | Signing **unconfigured** — no `AETHER_KEYSTORE_*` secrets yet |
| mDNS discovery | **Not implemented** | Deliberately excluded; manual IP/port entry only |

Distinction that matters: a green CI build proves the code compiles and the
tests pass. It does **not** prove pairing or `pm` operations work on a real
device. Hardware validation is pending and tracked in
[docs/limitations.md](docs/limitations.md).

## Docs

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
