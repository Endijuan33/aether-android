# Troubleshooting

## Pairing failures

| Symptom | Likely cause | Action |
|---|---|---|
| `InvalidCode` | Wrong or expired 6-digit code | The code expires. Get a fresh one: **Developer options → Wireless debugging → Pair device with pairing code**, and enter it promptly. |
| `Timeout` (30 s) | Device unreachable, firewall, or wrong IP/port | Verify the IP/port shown in the system pairing dialog; confirm both devices are on the same Wi-Fi. |
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
stale. Re-read the current IP and ports from system settings and update
them in AETHER (the app remembers the last-used values in DataStore for
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

Diagnostics redact secrets automatically: 6-digit runs become
`[pairing-code]`, PEM blocks become `[key-material]`. If you paste logs
into a GitHub issue, double-check that no code, key or certificate
survived redaction before posting. Never post a private key — AETHER's
identity lives in AndroidKeyStore and there is no legitimate reason to
export it.

## Reporting

Open an issue at https://github.com/Endijuan33/aether-android/issues with:
AETHER version, Android version + OEM, which step failed, and the redacted
diagnostic text. Screenshots of the system pairing dialog are fine —
they contain the code, which expires within minutes.
