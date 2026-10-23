# Security policy

## Threat model

AETHER executes privileged package operations over a network (TLS) connection
it establishes itself. The design assumes:

1. **The pairing exchange is the trust root.** A 6-digit code shown by
   Android system settings is bound to the TLS session via the SPAKE2
   password material (`pairing_code || TLS_exporter("adb-label", 64B)`),
   so the key cannot be forwarded to another session. Wrong code ⇒ the
   device cannot decrypt our peer info and drops the connection.
2. **Only structured commands cross the shell.** `AdbShell.exec` accepts
   only commands in the allowlist `{pm, cmd}`, and the package layer only
   ever sends `pm` with fixed subcommands and validated arguments. There is
   no string-interpolated shell — arguments are structured arrays.
3. **Inputs are validated before execution.** Package ids, hosts, ports and
   pairing codes are checked by `InputValidators` (regex + range checks);
   invalid package ids become SKIPPED results and never reach the shell.
4. **Operations are sequential.** The executor never runs conflicting `pm`
   operations concurrently.

## What is protected — and what is not

- Pairing codes and private key material are never logged. Diagnostics pass
  through `redactSecrets()` (6-digit runs ⇒ `[pairing-code]`, PEM blocks ⇒
  `[key-material]`) as a safety net; the primary rule is to never put them
  into log strings at all.
- The RSA-2048 pairing identity is generated inside AndroidKeyStore under
  alias `AETHER_ADB_PAIRING` (falls back to app-private files where
  AndroidKeyStore is unavailable). **Local encryption is not
  extraction-proof**: on a compromised or rooted device, stored keys and
  connection metadata can be read. Do not treat the device store as a vault.
- No root is required or used; no dangerous permissions are requested.
  Declared permissions: `INTERNET`, `ACCESS_NETWORK_STATE`,
  `QUERY_ALL_PACKAGES` (normal permission; package listing is read-only).
- No analytics or telemetry. No accounts, no cloud sync, nothing leaves
  the device except the TLS connections you explicitly initiate.
- **Uninstall-for-user is not fully reversible.** `pm uninstall --user 0`
  drops the app's data; restoring the app later requires the original APK
  (e.g. from the Play Store or a backup). The preview dialog says this
  before you confirm.

## Reporting a vulnerability

Open a GitHub issue at
https://github.com/Endijuan33/aether-android/issues describing the issue.
Do not include secrets, key material, or exploit payloads that target real
devices. If the issue involves a specific device, include the Android
version, OEM and AETHER version only.

## Supported versions

| Version | Supported |
|---|---|
| 0.1.0 | Yes — security reports accepted |
