# Changelog

All notable changes to AETHER — System Control are documented here.
Format follows Keep a Changelog; versions follow SemVer.

## [Unreleased] — engineering hardening (2026-10-25)

- Add device detection, native cached application icons and Device Recommendations with offline catalogue schema 1, revision `2026-10-26-r1`. The POCO F6 profile contains conservative source-backed references; no device-verified SAFE removals are claimed.
- Unify controlled checkbox/row selection, protect individual selection, preserve selections through filters and saved state, and display visible/protected/hidden counts.
- Resolve Developer Settings to an external native system activity with launch-failure fallback and a resume-refreshed state indicator.

- Correct AOSP RSA peer-info encoding, CNXN/STLS/TLS negotiation and NUL-terminated destinations; persistent PSS-capable AndroidKeyStore identity requires re-pairing.
- Restrict complete shell command grammar and endpoints to this device. Verify UID and resulting package state, protect essential packages and AETHER, serialize plans and preserve interrupted history.
- Repair cancellation/socket cleanup, bounded output/backpressure, connection-loss reporting and activity recreation ownership.
- Repair pairing editor/success dismissal, risk acknowledgement, recovery filtering, observable storage failures, lifecycle-aware UI collection and document-picker inventory export.
- Improve text contrast, safety semantics and RTL back navigation; remove an unsupported hardware-validation claim and unused discovery implementations.
- Update Conscrypt to 2.8.0 for 16-KiB native alignment; move BC to JVM tests and update it to 1.85 for published advisories.
- Add reproducible formatting, dependency locks/checksums, runtime advisory checks and platform/UI regression tests; correct CI SDK inputs and release signing cleanup.
- Hardware validation still pending. See docs/engineering-audit.md for actual build/test results and release limitations.

## [0.1.0] — 2026-10-24

Initial release.

### Added

- Wireless-debugging pairing flow: onboarding guide, manual IP/port entry,
  6-digit pairing dialog (paste, digit filtering, validation), progress and
  error states.
- Hand-rolled Android 11+ ADB client: TLS + SPAKE2 pairing
  (`adb pair` equivalent, verified against BoringSSL oracle vectors) and
  classic CNXN/OPEN/WRTE/CLSE ADB transport over the TLS channel.
  No third-party ADB libraries.
- Restricted ADB shell: only `pm` and `cmd` commands, structured arguments,
  all package operations validated before execution.
- Package inventory via `PackageManager` (`QUERY_ALL_PACKAGES`): label,
  version, install state, permissions, install times; classification into
  user / system / disabled / updated-system.
- Debloat operations: `pm uninstall --user 0`, `pm disable-user`,
  `pm enable`; single and multi-package selection; risk preview dialog;
  strictly sequential execution with per-operation results; partial
  failures never abort the plan.
- Safety catalogue: 52 curated entries (revision `2026-10-24-r1`):
  15 ESSENTIAL, 17 RISKY, 20 SAFE. Packages without an entry are shown as
  unknown, never guessed.
- Operation history persisted to a local SQLite database.
- Inventory JSON export.
- DataStore preferences: theme, onboarding state, last host/ports.
- Dark-graphite / electric-cyan theme with optional light theme; original
  vector adaptive launcher icon and monochrome variant.
- 14 destinations: splash, onboarding, dashboard, setup guide, pairing
  dialog, diagnostics, app list, app details, debloat, progress, history,
  recovery, settings, about (+ licenses and privacy info).
- CI workflow (ktfmt format check, unit tests, lint, debug APK),
  release workflow (tag-triggered, optional apksigner signing via
  `AETHER_KEYSTORE_*` secrets), weekly dependency-check workflow.
- 152 unit tests (adb 67, packages/domain/persistence/security 60, ui 25).

### Known limitations (0.1.0)

- Wireless pairing and `pm` operations are **not yet validated against real
  hardware** — no physical device is available in CI.
- Discovery is manual IP/port entry only; no mDNS discovery.
- Release signing is unconfigured (no `AETHER_KEYSTORE_*` secrets yet), so
  any tag-built APK is reported as unsigned.

[0.1.0]: https://github.com/Endijuan33/aether-android/releases/tag/v0.1.0

## Notification pairing redesign (unreleased)

- Replace manual-first setup with native RemoteInput notification Pair & Connect and separate NSD pairing/connect discovery.
- Add a bounded, non-exported pairing foreground service with no secret redelivery; reject malformed, duplicate and stale notification replies.
- Verify authenticated transport and shell UID 2000 independently before Connected; retain the existing key, protocol and package-operation safeguards.
- Add runtime notification/local-network permission guidance, discovered in-app fallback, advanced manual setup and honest lifecycle/OEM limitations.
- Add controller, NSD-generation, socket-integration, notification-platform and Compose fallback regressions. Hardware validation still pending.
