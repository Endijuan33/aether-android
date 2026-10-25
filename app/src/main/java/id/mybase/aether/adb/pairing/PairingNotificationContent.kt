package id.mybase.aether.adb.pairing

data class PairingNotificationContent(
    val title: String,
    val text: String,
    val acceptsCode: Boolean = false,
)

fun WirelessPairingState.notificationContent(): PairingNotificationContent =
    when (phase) {
        PairingPhase.WAITING_FOR_PAIRING_CODE ->
            PairingNotificationContent(
                if (pairingEndpoint == null) "AETHER — Waiting for Pairing Code"
                else "AETHER — Pairing Service Found",
                message ?: "Enter the six-digit pairing code to securely pair this device.",
                acceptsCode,
            )
        PairingPhase.PAIRING_IN_PROGRESS ->
            PairingNotificationContent("AETHER — Pairing", "Authenticating Wireless Debugging…")
        PairingPhase.PAIRING_SUCCESS ->
            PairingNotificationContent(
                "AETHER — Pairing Successful",
                "Wireless Debugging authentication completed successfully.",
            )
        PairingPhase.DISCOVERING_CONNECT_ENDPOINT ->
            PairingNotificationContent(
                "AETHER — Finding ADB",
                "Finding the separate ADB connection endpoint…",
            )
        PairingPhase.CONNECTING ->
            PairingNotificationContent(
                "AETHER — Connecting",
                "Establishing the authenticated ADB transport…",
            )
        PairingPhase.VERIFYING_SHELL ->
            PairingNotificationContent(
                "AETHER — Verifying Shell",
                "Checking shell access before enabling system management…",
            )
        PairingPhase.CONNECTED ->
            PairingNotificationContent(
                "AETHER — Connected",
                "ADB transport and shell UID 2000 verified. Open AETHER for current status and system management.",
            )
        PairingPhase.PAIRING_FAILED,
        PairingPhase.CONNECTION_FAILED ->
            PairingNotificationContent(
                "AETHER — Wireless Debugging Failed",
                message ?: "Open AETHER to retry.",
            )
        PairingPhase.DISCONNECTING,
        PairingPhase.DISCONNECTED ->
            PairingNotificationContent(
                "AETHER — Disconnected",
                message ?: "Wireless debugging session stopped.",
            )
        PairingPhase.TIMED_OUT ->
            PairingNotificationContent(
                "AETHER — Timed Out",
                message ?: "Open AETHER to retry discovery.",
            )
        PairingPhase.CANCELLED ->
            PairingNotificationContent(
                "AETHER — Pairing Cancelled",
                message ?: "Open AETHER to start a new attempt.",
            )
        PairingPhase.PREPARING ->
            PairingNotificationContent(
                "AETHER — Wireless Pairing",
                "Open Pair device with pairing code and enter the code here. Discovery starts automatically.",
                acceptsCode,
            )
        PairingPhase.SEARCHING_PAIRING_SERVICE ->
            PairingNotificationContent(
                "AETHER — Discovering Pairing Service",
                message
                    ?: "Code received. Keep Pair device with pairing code open while discovery runs.",
                acceptsCode,
            )
        else ->
            PairingNotificationContent(
                "AETHER — Wireless Pairing",
                "Searching for Android Wireless Debugging pairing service…",
            )
    }
