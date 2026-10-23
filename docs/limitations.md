# Known limitations

Honest accounting of what 0.1.0 does not do, cannot do, or has not yet
proven on hardware.

## Hardware validation: pending

The highest-impact limitation. CI has **no physical Android device**, so
the following are implemented and unit-tested (including socket-based
protocol fakes and BoringSSL oracle-vector verification) but **not yet
validated against a real `adbd`**:

- The full TLS + SPAKE2 pairing handshake (highest risk: the TLS exporter
  via Conscrypt reflection; pairing fails closed if it is unavailable —
  no fake success is possible).
- The RSA public-key encoding inside the 8192-byte peer-info struct
  (we send X.509 SPKI DER; AOSP's exact expectation is unverified).
- The classic ADB session over the TLS tunnel.
- `pm uninstall --user 0`, `pm disable-user`, `pm enable` behavior across
  OEM skins and Android versions.

A green CI build proves compilation and test passage, not on-device
behavior.

## Platform support

- **Android 11+ (API 30) required** for the wireless-debugging workflow —
  TLS-based pairing does not exist on older releases. The app will not
  install below API 30.
- Target / compile SDK 37. OEM skins (Samsung, Xiaomi, etc.) may behave
  differently; the safety catalogue explicitly varies by manufacturer,
  firmware and Android version (see
  [catalogue-provenance.md](catalogue-provenance.md)).

## Discovery

**Manual IP/port entry only.** Wireless debugging does not advertise a
documented, stable mDNS service for the pairing endpoint that can be
relied upon; the pairing port is ephemeral and shown in system settings
alongside the 6-digit code. `NsdManager` discovery was deliberately not
implemented to avoid depending on unverified advertisement behavior.

## Privileges: no root, by design

- AETHER does not require or use root. It is an ordinary APK; installing
  it grants no shell privileges by itself.
- All package operations run through the wireless-debugging ADB session,
  i.e. the `shell` user — the same privilege level as `adb shell` from a
  paired workstation.
- What `pm` can do at that level: `uninstall --user 0` (removes the app
  for the current user; system partition untouched), `disable-user`,
  `enable`, `clear`. What it cannot do: touch `/system` partitions,
  remove preinstalled APKs permanently, or grant itself new capabilities.

## Operations and recovery

- `pm uninstall --user 0` is **not fully reversible by this app**: the
  app's data is dropped, and reinstalling requires the original APK
  (Play Store, OEM source, or your own backup). The preview dialog states
  this before confirmation.
- `disable-user` / `enable` are symmetric toggles.
- Operation history records what was done, not a restore point. Export
  the inventory JSON before debloating if you want a reference.

## Release signing

Signing secrets (`AETHER_KEYSTORE_*`) are not configured, so tag builds
currently produce **unsigned** release APKs; the release workflow says so
in its summary rather than pretending otherwise.

## Catalogue

52 entries, revision `2026-10-24-r1`, curated from AOSP/Play/OEM sources.
Packages without an entry are shown as **unknown**, never auto-classified.
The catalogue is a starting reference, not an authority — see
[catalogue-provenance.md](catalogue-provenance.md).
