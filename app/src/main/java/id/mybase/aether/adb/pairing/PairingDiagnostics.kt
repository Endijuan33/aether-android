package id.mybase.aether.adb.pairing

/** Only typed, non-sensitive fields may cross the diagnostic boundary. */
enum class PairingDiagnosticEvent {
    SERVICE_STARTED,
    SERVICE_STOPPED,
    NOTIFICATION_POSTED,
    NOTIFICATION_FAILED,
    REMOTE_INPUT_RECEIVED,
    REPLY_ACCEPTED,
    REPLY_REJECTED,
    NSD_STARTED,
    NSD_REQUESTED,
    NSD_FOUND,
    NSD_RESOLVED,
    NSD_LOST,
    NSD_RESOLUTION_FALLBACK,
    NSD_RETRY,
    NSD_FAILED,
    NSD_STOPPED,
    PAIRING_CLIENT_INITIALIZED,
    PAIRING_AUTHENTICATED,
    PAIRING_REJECTED,
    TRANSPORT_ESTABLISHED,
    TRANSPORT_FAILED,
    SHELL_COMMAND_EXECUTED,
    SHELL_UID_VERIFIED,
    SHELL_UID_REJECTED,
    STATE_CHANGED,
    TIMEOUT,
    CANCELLED,
    FAILED,
    PROTOCOL_STAGE,
    PROTOCOL_FAILED,
}

enum class PairingOutcome {
    SUCCESS,
    INVALID_CODE,
    TIMEOUT,
    CONNECTION_REFUSED,
    NETWORK_ERROR,
}

enum class TlsFailureReason {
    DELEGATE_ERROR,
    BAD_CERTIFICATE,
    NO_COMMON_SIGNATURE_ALGORITHMS,
    HANDSHAKE_FAILURE,
    CERTIFICATE_VERIFY_FAILED,
    NO_SHARED_CIPHER,
    WRONG_VERSION_NUMBER,
    INVALID_SIGNATURE,
}

enum class PairingProtocolStage {
    TLS_IDENTITY,
    TCP_CONNECT,
    TLS_HANDSHAKE,
    TLS_EXPORTER,
    TLS_READY,
    SPAKE2_SEND,
    SPAKE2_READ,
    SPAKE2_DERIVE,
    PEER_INFO_SEND,
    PEER_INFO_READ,
    PEER_INFO_DECRYPT,
    COMPLETE,
}

data class PairingDiagnostic(
    val event: PairingDiagnosticEvent,
    val phase: PairingPhase? = null,
    val service: AdbServiceType? = null,
    val platformError: Int? = null,
    val uid: Int? = null,
    val failure: DiscoveryFailureReason? = null,
    val outcome: PairingOutcome? = null,
    val protocolStage: PairingProtocolStage? = null,
    val exceptionTypes: List<String> = emptyList(),
    val tlsReason: TlsFailureReason? = null,
)

/** Class names are static program metadata. Never include Throwable messages or stack traces. */
fun PairingDiagnostics.protocolFailure(stage: PairingProtocolStage, error: Throwable) {
    val types = mutableListOf<String>()
    var cause: Throwable? = error
    var tlsReason: TlsFailureReason? = null
    repeat(4) {
        val current = cause ?: return@repeat
        types += current.javaClass.name.take(160)
        if (tlsReason == null)
            tlsReason =
                TlsFailureReason.entries.firstOrNull {
                    current.message?.contains(it.name) == true
                }
        cause = current.cause.takeUnless { it === current }
    }
    record(
        PairingDiagnostic(
            PairingDiagnosticEvent.PROTOCOL_FAILED,
            protocolStage = stage,
            exceptionTypes = types,
            tlsReason = tlsReason,
        ),
    )
}

fun interface PairingDiagnostics {
    fun record(diagnostic: PairingDiagnostic)

    companion object {
        val NONE = PairingDiagnostics {}
    }
}

/** Bounded process-local trace for on-device troubleshooting; never persisted. */
class PairingDiagnosticBuffer(private val capacity: Int = 128) : PairingDiagnostics {
    private val events = ArrayDeque<PairingDiagnostic>()

    init {
        require(capacity in 1..256)
    }

    @Synchronized
    override fun record(diagnostic: PairingDiagnostic) {
        if (events.size == capacity) events.removeFirst()
        events.addLast(diagnostic)
    }

    @Synchronized fun snapshot(): List<PairingDiagnostic> = events.toList()
}

/** Contains no endpoint, service name, Intent, code, key, exception text or shell output. */
object AndroidPairingDiagnostics : PairingDiagnostics {
    private val history = PairingDiagnosticBuffer()

    fun snapshot(): List<PairingDiagnostic> = history.snapshot()

    override fun record(diagnostic: PairingDiagnostic) {
        history.record(diagnostic)
        android.util.Log.i("AetherPairing", diagnostic.toString())
    }
}
