package id.mybase.aether.adb

import android.content.Context
import java.security.KeyPair
import kotlinx.coroutines.flow.StateFlow

/**
 * Result of a single wireless-debugging pairing attempt against the device's pairing endpoint
 * (Android 11+, `adb pair` equivalent).
 */
sealed interface PairingResult {
    /** The 6-digit code was accepted; this client's key is now authorized by adbd. */
    data object Success : PairingResult

    /** The pairing code was malformed or rejected by the device. */
    data object InvalidCode : PairingResult

    /** No definitive answer arrived within [AdbPairingClient.pair]'s timeout. */
    data object Timeout : PairingResult

    /** TCP connection to the pairing port was actively refused. */
    data object ConnectionRefused : PairingResult

    /** Any other transport failure (DNS, TLS, I/O, invalid host/port input). */
    data class NetworkError(val message: String) : PairingResult
}

/**
 * Observable lifecycle of the ADB session owned by [AdbShell].
 *
 * The [Pairing]/[Paired] states are emitted by [AdbConnectionManager.pairAndConnect] while it
 * drives the pairing handshake before opening the ADB session.
 */
sealed interface AdbConnectionState {
    data object Disconnected : AdbConnectionState

    data class Pairing(val host: String, val port: Int) : AdbConnectionState

    data object Paired : AdbConnectionState

    data class Connecting(val host: String, val port: Int) : AdbConnectionState

    data class Connected(val host: String, val port: Int) : AdbConnectionState

    data class Failed(val reason: String) : AdbConnectionState
}

/**
 * Performs the Android 11+ wireless-debugging pairing handshake (TLS + SPAKE2 with the 6-digit code
 * shown in system settings).
 */
interface AdbPairingClient {
    suspend fun pair(
        host: String,
        port: Int,
        pairingCode: String,
        timeoutMs: Long = 30_000,
    ): PairingResult
}

/**
 * Classic ADB session (CNXN/OPEN/WRTE/CLSE) tunnelled over the TLS channel that wireless debugging
 * requires. Structured command execution only.
 */
interface AdbShell {
    val state: StateFlow<AdbConnectionState>

    suspend fun connect(host: String, port: Int, timeoutMs: Long = 15_000): Result<Unit>

    suspend fun disconnect()

    suspend fun exec(vararg args: String, timeoutMs: Long = 30_000): Result<String>
}

/**
 * Owns the RSA-2048 identity this app presents to adbd, both for pairing (SPAKE2 peer-info) and for
 * TLS client-certificate authentication afterwards.
 *
 * Implementations must never log key material.
 */
interface AdbKeyStore {
    fun getOrCreateKeyPair(): KeyPair
}

/**
 * Extends [AdbKeyStore] with the self-signed X.509 certificate (DER) that wraps the public key.
 * adbd authorizes the *public key*; the certificate is the carrier used by the pairing peer-info
 * exchange and the TLS handshake.
 */
interface AdbKeyMaterial : AdbKeyStore {
    fun certificateDer(): ByteArray
}

/**
 * Wiring for the adb workstream. The [Context] is used for the AndroidKeyStore lookup (alias
 * [AndroidAdbKeyStore.ALIAS]) and, as a fallback, for an app-private directory holding the key
 * files.
 */
object AdbModule {
    fun createPairingClient(context: Context): AdbPairingClient {
        val keys = AndroidAdbKeyStore(context.applicationContext)
        return TlsPairingClient(keys)
    }

    fun createShell(context: Context): AdbShell {
        val keys = AndroidAdbKeyStore(context.applicationContext)
        return AdbConnectionManager(keys, TlsPairingClient(keys))
    }
}
