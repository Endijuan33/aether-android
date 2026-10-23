# Changelog

All notable changes to AETHER — System Control are documented here.
Format follows Keep a Changelog; versions follow SemVer.

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
