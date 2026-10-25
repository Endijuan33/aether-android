# Security policy

## Threat model

AETHER executes privileged package operations over a network (TLS) connection
to this device only. Endpoints resolve once and must be loopback or a local network interface. The design assumes:

1. **The pairing exchange is the trust root.** A 6-digit code shown by
   Android system settings is bound to the TLS session via the SPAKE2
   password material (`pairing_code || TLS_exporter("adb-label", 64B)`),
   so the key cannot be forwarded to another session. Wrong code ⇒ the
   device cannot decrypt our peer info and drops the connection.
2. **Only structured commands cross the shell.** `AdbShell.exec` accepts
   only commands in the complete fixed command grammar, and the package layer only
   ever sends `pm` with fixed subcommands and validated arguments. There is
   shell string is assembled only after every argument matches the strict grammar. Arbitrary `pm`, `cmd`, redirection and user-supplied commands are rejected.
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
  alias `AETHER_ADB_PAIRING_V3` with RSA-PSS and DIGEST_NONE/raw padding for Conscrypt's platform-supported RSA signing delegate; purposes remain SIGN/VERIFY and V1/V2 entries are preserved (no production file fallback). **Local encryption is not
  extraction-proof**: on a compromised or rooted device, stored keys and
  connection metadata can be read. Do not treat the device store as a vault.
- No root is required or used. Normal permissions are INTERNET, ACCESS_NETWORK_STATE, QUERY_ALL_PACKAGES and FOREGROUND_SERVICE. POST_NOTIFICATIONS and ACCESS_LOCAL_NETWORK are requested at runtime only on applicable Android versions for notification input and local discovery.
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

## Additional hardening and residual assumptions

Essential catalogue packages and AETHER cannot be uninstalled/disabled. All mutations explicitly target user 0 and refuse inventory from another Android user. UID 2000, expected pm output and resulting package state are verified separately. A durable IN_PROGRESS history row precedes mutation; cancellation stops the plan and uncertain outcomes are retained. Automatic backup/device transfer excludes app data.

Self-signed adbd TLS certificates are accepted for the ADB protocol, not for arbitrary web/network traffic. Pairing code plus TLS exporter authenticates pairing; later transport authorization proves the client identity to adbd. The selected local listener/OS is trusted. A malicious local process or compromised OS can spoof shell output; application-level checks do not prove peer identity against that attacker.

The custom BigInteger SPAKE2 code is not constant-time and still needs independent cryptographic review. Oracle tests establish selected mathematical compatibility, not side-channel safety. Hardware validation still pending.

## Native notification boundary

The pairing service is non-exported. RemoteInput requires a mutable PendingIntent; it has an explicit component/package and a unique session/reply identity. All other actions/content intents are immutable. Obsolete reply intents are revoked on notification replacement; controller tokens additionally reject duplicate/stale replies. The six-digit code is validated before copying or protocol dispatch and is never used as an endpoint/command argument. START_NOT_STICKY prevents input replay; process recreation requires a fresh user action. Discovery advertisements are untrusted: addresses must be local interfaces, ports are range checked, resource counts are bounded, and late resolution generations are discarded. mDNS identity is not peer authentication; the existing local-listener threat assumption and pending crypto review still apply.
