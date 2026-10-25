package id.mybase.aether.adb.pairing

enum class PairingPhase {
    IDLE,
    WAITING_FOR_PERMISSION,
    PREPARING,
    SEARCHING_PAIRING_SERVICE,
    WAITING_FOR_PAIRING_CODE,
    PAIRING_IN_PROGRESS,
    PAIRING_SUCCESS,
    DISCOVERING_CONNECT_ENDPOINT,
    CONNECTING,
    VERIFYING_SHELL,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    PAIRING_FAILED,
    CONNECTION_FAILED,
    TIMED_OUT,
    CANCELLED,
}

data class DiscoveredAdbEndpoint(val serviceName: String, val host: String, val port: Int) {
    init {
        require(host.isNotBlank() && port in 1..65535)
    }
}

enum class AdbServiceType(val dnsName: String) {
    PAIRING("_adb-tls-pairing._tcp."),
    CONNECT("_adb-tls-connect._tcp."),
}

data class WirelessPairingState(
    val phase: PairingPhase = PairingPhase.IDLE,
    val sessionId: String? = null,
    val replyToken: String? = null,
    val pairingEndpoint: DiscoveredAdbEndpoint? = null,
    val connectionEndpoint: DiscoveredAdbEndpoint? = null,
    val paired: Boolean = false,
    val transportVerified: Boolean = false,
    val shellUid: Int? = null,
    val connectedAt: Long? = null,
    val message: String? = null,
) {
    val active: Boolean
        get() =
            phase in
                setOf(
                    PairingPhase.PREPARING,
                    PairingPhase.SEARCHING_PAIRING_SERVICE,
                    PairingPhase.WAITING_FOR_PAIRING_CODE,
                    PairingPhase.PAIRING_IN_PROGRESS,
                    PairingPhase.PAIRING_SUCCESS,
                    PairingPhase.DISCOVERING_CONNECT_ENDPOINT,
                    PairingPhase.CONNECTING,
                    PairingPhase.VERIFYING_SHELL,
                    PairingPhase.DISCONNECTING,
                )

    val ready: Boolean
        get() = phase == PairingPhase.CONNECTED && transportVerified && shellUid == 2000

    val acceptsCode: Boolean
        get() =
            replyToken != null &&
                phase in
                    setOf(
                        PairingPhase.PREPARING,
                        PairingPhase.WAITING_FOR_PAIRING_CODE,
                        PairingPhase.SEARCHING_PAIRING_SERVICE,
                    )
}

/** Strict validation; malformed input is never filtered into a different, valid secret. */
object PairingCode {
    fun isValid(value: CharSequence?): Boolean =
        value != null && value.length == 6 && value.all { it in '0'..'9' }
}
