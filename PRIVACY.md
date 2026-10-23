# Privacy

## Short version

AETHER collects no personal data. There are no accounts, no analytics, no
telemetry, no cloud sync, and no advertising. Everything the app stores
stays on the device.

## What is stored on the device

| Data | Where | Notes |
|---|---|---|
| Pairing identity (RSA-2048 private key + self-signed certificate) | AndroidKeyStore, alias `AETHER_ADB_PAIRING` (app-private file fallback where AndroidKeyStore is unavailable) | Generated on device; never transmitted except the public half during pairing |
| Last used host, pairing port, connection port | DataStore preferences (`aether_prefs`) | Non-sensitive connection metadata for convenience |
| Theme preference, onboarding completion | DataStore preferences | UI state only |
| Operation history (package name, label, operation, status, message, timestamp) | Local SQLite database (`aether_history.db`) | Local audit log; not synced anywhere |

## What never happens

- Pairing codes are used once during the pairing handshake and are never
  stored, transmitted to any third party, or written to logs. Diagnostics
  redact 6-digit runs as `[pairing-code]`.
- Private keys never leave the device in any form.
- Package inventories, export JSONs and history records are created and
  kept locally. Exported JSON files go wherever you choose to save them —
  the app does not upload them.
- No network requests are made except the TCP/TLS connections you
  explicitly start to a device's wireless-debugging endpoints.

## Device permissions

The app declares `INTERNET` (wireless-debugging sockets),
`ACCESS_NETWORK_STATE` (connection diagnostics) and `QUERY_ALL_PACKAGES`
(full package inventory). None of them are dangerous permissions; the
package listing is read-only metadata.

## Contact

Questions about data handling: open an issue at
https://github.com/Endijuan33/aether-android/issues.
