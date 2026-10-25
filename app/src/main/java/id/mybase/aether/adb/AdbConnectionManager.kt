package id.mybase.aether.adb

import id.mybase.aether.security.InputValidators
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [AdbShell] implementation: an explicit state machine over one [AdbSession].
 *
 * State transitions:
 * - [AdbConnectionState.Disconnected] → [AdbConnectionState.Connecting] →
 *   [AdbConnectionState.Connected] | [AdbConnectionState.Failed]
 * - any state → [AdbConnectionState.Disconnected] via [disconnect]
 * - [pairAndConnect] additionally emits [AdbConnectionState.Pairing] → [AdbConnectionState.Paired]
 *   before the Connecting leg.
 *
 * [connect] on an already-connected manager reconnects: the old session is torn down first
 * ("Reconnect = disconnect() then connect()").
 *
 * Command execution is allowlisted for 0.1.0: only `pm` and `cmd` may run. Rationale (least
 * privilege): this workstream exists to let the app manage packages; a general shell would widen
 * the blast radius of any caller bug or confused-deputy issue far beyond package management. Every
 * argument is additionally validated (non-blank, no shell metacharacters), and commands are passed
 * as structured argv — never concatenated into a shell string from user input beyond the validated
 * join.
 */
class AdbConnectionManager(
    private val keys: AdbKeyMaterial,
    private val pairingClient: AdbPairingClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AdbShell {

    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
    override val state: StateFlow<AdbConnectionState> = _state.asStateFlow()

    private val managerScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val sessionMutex = Mutex()
    private var session: AdbSession? = null

    override suspend fun connect(host: String, port: Int, timeoutMs: Long): Result<Unit> {
        val cleanHost = host.trim()
        if (!InputValidators.isValidHost(cleanHost)) {
            _state.value = AdbConnectionState.Failed("Invalid host")
            return Result.failure(IllegalArgumentException("Invalid host: '$cleanHost'"))
        }
        if (!InputValidators.isValidPort(port)) {
            _state.value = AdbConnectionState.Failed("Invalid port: $port")
            return Result.failure(IllegalArgumentException("Invalid port: $port"))
        }
        sessionMutex.withLock {
            closeSessionLocked()
            _state.value = AdbConnectionState.Connecting(cleanHost, port)
            try {
                // The handshake timeout is enforced by AdbSession.open via
                // socket.soTimeout (real wall-clock time). A coroutine
                // withTimeout here would run on the caller's Delay — under
                // kotlinx-coroutines-test's virtual time it fires spuriously
                // while blocking I/O runs on real threads — so there is
                // deliberately no withTimeout at this layer.
                session =
                    AdbSession.open(
                        cleanHost,
                        port,
                        keys,
                        timeoutMs,
                        managerScope,
                        ioDispatcher,
                    )
                val active = checkNotNull(session)
                managerScope.launch {
                    val failure = active.termination.await()
                    sessionMutex.withLock {
                        if (session === active) {
                            closeSessionLocked()
                            _state.value =
                                AdbConnectionState.Failed(
                                    "Connection lost: ${failure.message?.redactSecrets() ?: "transport closed"}",
                                )
                        }
                    }
                }
                _state.value = AdbConnectionState.Connected(cleanHost, port)
                return Result.success(Unit)
            } catch (e: SocketTimeoutException) {
                // Thrown by AdbSession.open when the TLS/CNXN handshake exceeds
                // the socket.soTimeout (real wall-clock time).
                closeSessionLocked()
                _state.value =
                    AdbConnectionState.Failed("Connection timed out after ${timeoutMs}ms")
                return Result.failure(e)
            } catch (e: CancellationException) {
                closeSessionLocked()
                _state.value = AdbConnectionState.Disconnected
                throw e
            } catch (e: ConnectException) {
                closeSessionLocked()
                _state.value = AdbConnectionState.Failed("Connection refused by $cleanHost:$port")
                return Result.failure(e)
            } catch (e: Exception) {
                closeSessionLocked()
                if (currentJob()?.isCancelled == true) {
                    // Cancelled while blocked in I/O: withSocketGuard closed the
                    // socket, turning the cancellation into an IOException.
                    _state.value = AdbConnectionState.Disconnected
                    throw CancellationException("Connection cancelled")
                }
                _state.value =
                    AdbConnectionState.Failed(
                        "Connection failed: ${e.message?.redactSecrets() ?: e.javaClass.simpleName}",
                    )
                return Result.failure(e)
            }
        }
    }

    override suspend fun disconnect() {
        sessionMutex.withLock {
            closeSessionLocked()
            _state.value = AdbConnectionState.Disconnected
        }
    }

    override suspend fun exec(vararg args: String, timeoutMs: Long): Result<String> {
        val active =
            sessionMutex.withLock { session }
                ?: return Result.failure(IllegalStateException("Not connected"))
        val argv =
            try {
                validateArgs(args)
            } catch (e: IllegalArgumentException) {
                return Result.failure(e)
            }
        return try {
            // Structured argv only: args were individually validated above, so
            // joining them cannot introduce shell metacharacters. Blocking
            // socket I/O stays on the IO dispatcher, never the caller.
            val output =
                withContext(ioDispatcher) {
                    active.execShell(argv.joinToString(" "), timeoutMs)
                }
            Result.success(output)
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (currentJob()?.isCancelled == true) throw CancellationException("Exec cancelled")
            Result.failure(e)
        }
    }

    /**
     * Pairs with the device's pairing endpoint, then opens the ADB session on the connection
     * endpoint. The pairing port and the connection port are separate values (wireless debugging
     * assigns them independently).
     */
    suspend fun pairAndConnect(
        host: String,
        pairingPort: Int,
        pairingCode: String,
        connectPort: Int,
        timeoutMs: Long = 30_000,
    ): Result<Unit> {
        val cleanHost = host.trim()
        if (!InputValidators.isValidHost(cleanHost)) {
            return Result.failure(IllegalArgumentException("Invalid host: '$cleanHost'"))
        }
        if (!InputValidators.isValidPort(pairingPort)) {
            return Result.failure(IllegalArgumentException("Invalid pairing port: $pairingPort"))
        }
        if (!InputValidators.isValidPort(connectPort)) {
            return Result.failure(IllegalArgumentException("Invalid connect port: $connectPort"))
        }
        _state.value = AdbConnectionState.Pairing(cleanHost, pairingPort)
        return when (
            val result = pairingClient.pair(cleanHost, pairingPort, pairingCode, timeoutMs)
        ) {
            PairingResult.Success -> {
                _state.value = AdbConnectionState.Paired
                connect(cleanHost, connectPort, timeoutMs)
            }
            PairingResult.InvalidCode -> {
                _state.value = AdbConnectionState.Failed("Pairing rejected: invalid code")
                Result.failure(PairingFailedException("Invalid pairing code"))
            }
            PairingResult.Timeout -> {
                _state.value = AdbConnectionState.Failed("Pairing timed out after ${timeoutMs}ms")
                Result.failure(PairingFailedException("Pairing timed out"))
            }
            PairingResult.ConnectionRefused -> {
                _state.value =
                    AdbConnectionState.Failed(
                        "Pairing connection refused by $cleanHost:$pairingPort",
                    )
                Result.failure(PairingFailedException("Pairing connection refused"))
            }
            is PairingResult.NetworkError -> {
                _state.value = AdbConnectionState.Failed("Pairing failed: ${result.message}")
                Result.failure(PairingFailedException(result.message))
            }
        }
    }

    private fun closeSessionLocked() {
        session?.close()
        session = null
    }

    companion object {

        /**
         * Returns the validated argv. Throws [IllegalArgumentException] on any violation: empty
         * argv, non-allowlisted command, blank argument, or shell metacharacters.
         */
        fun validateArgs(args: Array<out String>): List<String> {
            val argv = args.toList()
            val mutation =
                argv.size == 5 &&
                    argv[0] == "pm" &&
                    argv[1] in setOf("uninstall", "disable-user", "enable") &&
                    argv[2] == "--user" &&
                    argv[3] == "0" &&
                    InputValidators.isValidPackageName(argv[4])
            val readPrefix = listOf("pm", "list", "packages")
            val suffix = if (argv.take(3) == readPrefix) argv.drop(3) else null
            val read =
                suffix != null &&
                    (suffix.isEmpty() ||
                        suffix == listOf("--user", "0") ||
                        (suffix.size == 3 &&
                            suffix.take(2) == listOf("--user", "0") &&
                            InputValidators.isValidPackageName(suffix[2])) ||
                        (suffix.size == 4 &&
                            suffix[0] in setOf("-d", "-e") &&
                            suffix.subList(1, 3) == listOf("--user", "0") &&
                            InputValidators.isValidPackageName(suffix[3])))
            require(mutation || read || argv == listOf("id", "-u")) {
                "Only validated user-0 package operations and shell identity checks are allowed"
            }
            return argv
        }
    }
}

/** Pairing did not result in an authorized key. */
class PairingFailedException(message: String) : Exception(message)
