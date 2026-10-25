# Privacy

## Short version

AETHER keeps inventory, connection settings and operation history on this device. It has no accounts, analytics, telemetry, advertising or app-operated cloud sync. Explicit exports go to the document provider you choose, which may have its own cloud policy.

## What is stored on the device

| Data | Where | Notes |
|---|---|---|
| Pairing identity (RSA-2048 private key + self-signed certificate) | AndroidKeyStore, alias `AETHER_ADB_PAIRING_V3` (provider failures are surfaced) | Generated on device; never transmitted except the public half during pairing |
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
- User-initiated pairing discovers Android Wireless Debugging services on the local network through Android NSD/mDNS, then opens TCP/TLS connections to this device only. Discovery is bounded and stopped at completion/cancellation.

## Device permissions

The app declares `INTERNET` (wireless-debugging sockets),
`ACCESS_NETWORK_STATE` (connection monitoring), `QUERY_ALL_PACKAGES` (read-only inventory), `FOREGROUND_SERVICE` (bounded pairing service), `POST_NOTIFICATIONS` (runtime on Android 13+ for native inline input), and `ACCESS_LOCAL_NETWORK` (runtime on Android 17/SDK 37 for discovery). Notification denial offers in-app discovery; local-network denial must be resolved through Android permissions.

## Contact

Questions about data handling: open an issue at
https://github.com/Endijuan33/aether-android/issues.

Automatic cloud backup and device transfer exclude app data. Explicit inventory export uses the system document picker; a chosen cloud document provider may upload the file under that provider’s policy. Pairing codes are not persisted; private keys remain in AndroidKeyStore.

Pairing-code replies pass through Android SystemUI RemoteInput directly to an explicit non-exported AETHER service. Codes are not saved, logged, or redelivered on process recreation. SystemUI and immutable JVM strings may temporarily retain input; AETHER clears its mutable copy and removes the received Intent payload. Notifications hide content on the lock screen.
