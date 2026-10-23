package id.mybase.aether.adb

/**
 * Diagnostics helpers. Pairing codes and key material must never reach logs; [redactSecrets] is
 * applied to every human-facing string this workstream produces from untrusted or sensitive input.
 */
fun AdbConnectionState.describeRedacted(): String =
    when (this) {
        AdbConnectionState.Disconnected -> "Disconnected"
        is AdbConnectionState.Pairing -> "Pairing(host=$host, port=$port)"
        AdbConnectionState.Paired -> "Paired"
        is AdbConnectionState.Connecting -> "Connecting(host=$host, port=$port)"
        is AdbConnectionState.Connected -> "Connected(host=$host, port=$port)"
        is AdbConnectionState.Failed -> "Failed(reason=${reason.redactSecrets()})"
    }

/**
 * Strips secrets from a log string:
 * - 6-digit runs (pairing codes) that are not part of a longer digit run;
 * - PEM blocks (private keys / certificates).
 *
 * This is a heuristic safety net, not a substitute for never putting secrets into log strings in
 * the first place. Ports (max 5 digits) and timeouts are unaffected by the 6-digit rule.
 */
internal fun String.redactSecrets(): String {
    var result = this
    result = result.replace(Regex("(?<!\\d)\\d{6}(?!\\d)"), "[pairing-code]")
    result =
        result.replace(
            Regex(
                "-----BEGIN [A-Z0-9 ]+-----.*?-----END [A-Z0-9 ]+-----",
                RegexOption.DOT_MATCHES_ALL,
            ),
            "[key-material]",
        )
    return result
}
