package id.mybase.aether.adb.pairing

enum class DiscoveryFailureReason {
    PERMISSION,
    START,
    STOP,
    RESOLUTION,
    NO_LOCAL_ENDPOINT,
    WIFI_LOST,
    TIMEOUT,
}

/** Platform failures remain distinct without displaying untrusted NSD/provider error text. */
class AdbDiscoveryException(
    val reason: DiscoveryFailureReason,
    val service: AdbServiceType,
    val platformError: Int? = null,
) : Exception("Wireless discovery failed: $reason") {
    fun recoveryMessage(): String =
        when (reason) {
            DiscoveryFailureReason.PERMISSION ->
                "Android denied local network discovery. Allow local network access in app permissions where available; notification permission is separate."
            DiscoveryFailureReason.START,
            DiscoveryFailureReason.STOP ->
                "Android NSD could not run. Keep Wi-Fi and Wireless debugging enabled, cancel other pairing attempts, then retry."
            DiscoveryFailureReason.RESOLUTION ->
                "Android found an ADB service but could not resolve it. Reopen Pair device with pairing code and retry; check Wi-Fi and local network restrictions."
            DiscoveryFailureReason.NO_LOCAL_ENDPOINT ->
                "ADB advertisements did not resolve to this device. Check this device's Wi-Fi and Wireless debugging, then retry. Remote devices are not supported."
            DiscoveryFailureReason.WIFI_LOST ->
                "Wi-Fi disconnected. Reconnect Wi-Fi, reopen Wireless debugging, then retry."
            DiscoveryFailureReason.TIMEOUT ->
                if (service == AdbServiceType.PAIRING)
                    "Timed out discovering the pairing service. Keep Pair device with pairing code open, check Wi-Fi/local network restrictions, then retry."
                else
                    "Timed out discovering the separate ADB connection service. Keep Wireless debugging enabled and retry Reconnect; pairing identity is retained."
        }
}
