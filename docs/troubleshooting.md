# Troubleshooting

## Notification pairing and automatic discovery

| Symptom | Recovery |
|---|---|
| Notification missing | Check Android notification permission and the Wireless Debugging pairing channel; use in-app discovery if inline input is unavailable. |
| Local-network permission denied | On SDK 37 grant it through Android app permissions. Manual setup cannot bypass it. |
| Waiting for pairing code | Enter the code from Android's open Pair device with pairing code dialog. AETHER accepts one code while discovery runs; no IP/port is needed. |
| Searching for pairing service after code submission | Keep Android's pairing dialog open and check Wi-Fi. Discovery expires after 45 seconds if no local endpoint resolves, then allows retry. |
| Service found but resolution failed | AETHER retries legacy resolution after a failed/stalled monitored callback. If both attempts fail, reopen the system dialog and retry. Copy diagnostics to identify the failed stage. |
| Code rejected or expired | Enter the current six ASCII digits without spaces or punctuation. Reopen the system dialog if needed. |
| Paired but not connected | Enable Wireless debugging and use Reconnect. The connect endpoint is separate and discovered afresh. |
| Shell verification failed | UID 2000 was not verified; management stays unavailable. Check Android debugging authorization and permissions. |
| Notification shade cannot reply | Keep AETHER open and use discovered in-app input; split screen can keep the system dialog visible. |
| Wi-Fi changed/service stopped/process killed | Restart discovery or reconnect explicitly. No stale endpoints or submitted codes are replayed. |

OEM notification/background restrictions vary. Shizuku’s guide describes MIUI notification-style issues and background restrictions, but these are upstream reports, not AETHER device validation. Prefer capability checks and the in-app fallback; do not weaken Android security settings to force success.

Manual IP/port troubleshooting is under Settings → Advanced manual pairing & connection.

## Pairing failures

| Symptom | Likely cause | Action |
|---|---|---|
| `InvalidCode` | Wrong or expired 6-digit code | The code expires. Get a fresh one: **Developer options → Wireless debugging → Pair device with pairing code**, and enter it promptly. |
| `Timeout` (30 s) | Device unreachable, firewall, or wrong IP/port | Verify the IP/port shown in the system pairing dialog; check this device’s Wi-Fi and current Wireless debugging endpoint. |
| `ConnectionRefused` | Pairing dialog closed or wrong port | The pairing port is only live while the system pairing dialog (or the pairing screen) is active. Use the **pairing** port, not the ADB connection port. |
| `NetworkError` | DNS/TLS/IO failure, invalid host input | Check the host format (IPv4 or hostname) and that the network allows direct TCP between the devices. |

## Connection failures (after pairing)

- **Connection refused on the ADB port** — pairing authorizes the key, but
  the ADB endpoint is separate. Use the connection port from
  **Wireless debugging** settings, not the pairing port.
- **Auth failure / device re-prompts** — the device's paired-devices list
  may have been cleared (system reset, "revoke authorizations"). Pair
  again to re-authorize this app's key.
- **Intermittent drops** — Wi-Fi roaming, AP isolation, or the device
  sleeping. Disable battery optimization interference where the OEM
  allows it; reconnect from the dashboard.

## Network changes

If the device's IP changes (DHCP renew, network switch), saved ports are
stale. Use Reconnect to discover a fresh connection endpoint; advanced manual setup can use the current IP and ports (the app remembers the last-used values in DataStore for
convenience — they are hints, not guarantees).

## Package operation failures

- `pm` returns non-zero (e.g. `Failure [...]`) — the executor records the
  device's message verbatim in the operation history and continues with
  the remaining plan. Common causes: package is a protected system
  component, already uninstalled for the user, or the ADB session dropped
  mid-plan.
- If the session dropped, reconnect first; the history shows exactly which
  operations completed so you can re-run the remainder.

## Redacted logs

The notification workflow emits structured `AetherPairing` events. These contain enum-valued stages/service types/results, NSD error numbers, verified shell UID and bounded static exception class names. TLS reason strings are matched against a fixed enum and only that enum is recorded. No pairing codes, service names, network addresses, keys, Intent payloads, exception messages or shell output are logged. `NOTIFICATION_POSTED` means Android accepted the post, not proof that a particular SystemUI skin rendered the inline input.

Without an authorized workstation, open AETHER → Connection diagnostics → **Copy diagnostics** after the attempt. The copied text includes the current pairing/transport/shell verification state and the latest 128 structured events, held only in process memory. Keep AETHER alive until copying: process death loses the trace. The older connection summary may include the last local endpoint; omit that line if you do not want to share the address.

On an already authorized development workstation:

```bash
adb logcat -d -s AetherPairing:I '*:S'
```

Expected progression includes `SERVICE_STARTED`, `NOTIFICATION_POSTED`, `NSD_REQUESTED`, `NSD_STARTED`, `REMOTE_INPUT_RECEIVED`, `REPLY_ACCEPTED`, `NSD_FOUND`, `NSD_RESOLVED`, `PAIRING_AUTHENTICATED`, fresh NSD events with `service=CONNECT`, `TRANSPORT_ESTABLISHED`, `SHELL_COMMAND_EXECUTED` and `SHELL_UID_VERIFIED` with `uid=2000`. Code submission and discovery events may interleave. `NSD_RESOLUTION_FALLBACK`, `NSD_RETRY`, `NSD_FAILED`, `TIMEOUT` and `CANCELLED` identify recovery paths. Missing `NSD_FOUND` and failed resolution are different failures; do not infer one from a searching screen alone.

`PROTOCOL_STAGE` separates TLS identity/connect/handshake/exporter from SPAKE2 and peer-info. `PAIRING_REJECTED outcome=NETWORK_ERROR` at TLS_HANDSHAKE is a TLS/provider failure, not evidence of an incorrect code. V3 changes the immutable signing policy, so use a fresh system-mediated Pair & Connect attempt after upgrading; V1/V2 identities remain preserved but cannot gain the new authorizations by retrying an old key.

Android 16 local-network protection is opt-in and temporarily uses NEARBY_WIFI_DEVICES; Android 17/API 37 enforces ACCESS_LOCAL_NETWORK for SDK-37-targeting apps. Notification authorization grants neither. Do not assume API-37 runtime enforcement is the cause of an Android-16 failure without evidence of an enabled compatibility restriction.

Diagnostics redact secrets automatically: 6-digit runs become
`[pairing-code]`, PEM blocks become `[key-material]`. If you paste logs
into a GitHub issue, double-check that no code, key or certificate
survived redaction before posting. Never post a private key — AETHER's
identity lives in AndroidKeyStore and there is no legitimate reason to
export it.

## Reporting

Open an issue at https://github.com/Endijuan33/aether-android/issues with:
AETHER version, Android version + OEM, which step failed, and the redacted
diagnostic text. Remove pairing codes, private keys and personal information from diagnostics or screenshots before posting.
