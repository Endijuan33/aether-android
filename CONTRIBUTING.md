# Contributing to AETHER

## Build

Requirements: JDK 17 and the Android SDK (API 37 platform + build-tools;
CI installs them via `android-actions/setup-android`).

```bash
./gradlew assembleDebug
```

Validate the Gradle wrapper before trusting it:

```bash
./gradlew wrapper --validate-checksums
```

## Test

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint
```

CI additionally runs a ktfmt format check and `assembleDebug`. All four
must pass for a PR to be mergeable.

The suite currently holds 152 `@Test` functions:

- `adb` — 67 (pairing crypto/protocol, TLS pairing client, keystores,
  connection manager, diagnostics)
- `packages` / `domain` / `persistence` / `security` — 60 (validators,
  risk matrix, catalogue, executor, export, history store)
- `ui` — 25 (ViewModel state transitions)

Some tests exercise socket-based fakes (`FakeAdbServer`, `FakePairingServer`)
rather than a real device. A green test run is not proof of on-device
behavior — see [docs/limitations.md](docs/limitations.md).

Do not invent coverage claims: report the generated test report
(`app/build/reports/tests/testDebugUnitTest/`), never a percentage you did
not measure.

## Code style

- Kotlin, 4-space indent, formatted with **ktfmt** in Kotlin-language style:
  `ktfmt --kotlinlang-style`. CI fails on unformatted files — run it before
  pushing.
- Gradle Kotlin DSL. Version catalog only: `gradle/libs.versions.toml`.
  No dynamic versions (`+`, `latest`, `SNAPSHOT`), no version ranges — the
  dependency-check workflow fails on them.
- English for code, comments and commit messages.
- Small composables, explicit state models (sealed interfaces over loose
  booleans), no dead buttons, no fake progress indicators.
- New secrets never enter source or logs. Pairing codes and key material
  are redacted in diagnostics (`AdbDiagnostics.redactSecrets`).

## Pull requests

- Target `main`. Keep the change focused; one concern per PR.
- Include or update tests for behavior changes. Fix the bug — do not
  weaken or suppress a failing test to make CI green.
- Update docs when behavior changes: README, the relevant `docs/` page,
  and [CHANGELOG.md](CHANGELOG.md) for user-visible changes.
- Safety catalogue changes (`app/src/main/assets/catalogue.json`) must
  follow [docs/catalogue-provenance.md](docs/catalogue-provenance.md):
  bump `revision`, record source and date, never fabricate a rating.

## Security

See [SECURITY.md](SECURITY.md) for the threat model. Report vulnerabilities
via GitHub issues — do not open a PR that demonstrates an exploit.
