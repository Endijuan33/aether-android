package id.mybase.aether.adb.pairing

import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.adb.AdbPairingClient
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.adb.PairingResult
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-scoped coordinator. No secret, endpoint or mutation is replayed after process death. */
class WirelessPairingController(
    private val pairing: AdbPairingClient,
    private val shell: AdbShell,
    private val discovery: AdbServiceDiscovery,
    private val scope: CoroutineScope,
    private val rememberConnection: suspend (String, Int) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
    private val sessionTimeoutMs: Long = 150_000,
    private val discoveryTimeoutMs: Long = 45_000,
    private val diagnostics: PairingDiagnostics = PairingDiagnostics.NONE,
) {
    private val mutableState = MutableStateFlow(WirelessPairingState())
    val state: StateFlow<WirelessPairingState> = mutableState.asStateFlow()
    private var sessionJob: Job? = null
    private val cancellationMutex = Mutex()
    private val submissions = Channel<Submission>(1, onUndeliveredElement = { it.clear() })

    private class Submission(val token: String, val digits: CharArray) {
        fun clear() {
            digits.fill('\u0000')
        }

        override fun toString() = "Pairing submission (redacted)"
    }

    init {
        scope.launch {
            shell.state.collect { transport ->
                if (state.value.ready && transport !is AdbConnectionState.Connected) {
                    update {
                        it.copy(
                            phase = PairingPhase.CONNECTION_FAILED,
                            transportVerified = false,
                            shellUid = null,
                            message = "ADB disconnected. Enable Wireless debugging and reconnect.",
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    fun permissionRequired(message: String) {
        if (!state.value.active && !state.value.ready) {
            mutableState.value =
                state.value.copy(phase = PairingPhase.WAITING_FOR_PERMISSION, message = message)
        }
    }

    @Synchronized
    fun start(reconnectOnly: Boolean = false): Boolean {
        if (state.value.active || state.value.ready || sessionJob?.isActive == true) return false
        drainSubmissions()
        val id = UUID.randomUUID().toString()
        val initialReplyToken = if (reconnectOnly) null else UUID.randomUUID().toString()
        mutableState.value =
            WirelessPairingState(
                phase = PairingPhase.PREPARING,
                sessionId = id,
                replyToken = initialReplyToken,
                paired = reconnectOnly && state.value.paired,
                connectedAt = state.value.connectedAt,
            )
        sessionJob = scope.launch {
            try {
                // Let the foreground-service observer publish the reply action before NSD starts.
                yield()
                withTimeout(sessionTimeoutMs) {
                    shell.disconnect()
                    if (!reconnectOnly) awaitPairing(initialReplyToken!!)
                    connectDiscovered()
                }
            } catch (e: TimeoutCancellationException) {
                diagnostics.record(
                    PairingDiagnostic(PairingDiagnosticEvent.TIMEOUT, state.value.phase),
                )
                fail(
                    "Timed out. Open Pair device with pairing code, check Wi-Fi, then retry. Paired devices can use Reconnect.",
                    timedOut = true,
                )
            } catch (e: AdbDiscoveryException) {
                diagnostics.record(
                    PairingDiagnostic(
                        if (e.reason == DiscoveryFailureReason.TIMEOUT)
                            PairingDiagnosticEvent.TIMEOUT
                        else PairingDiagnosticEvent.FAILED,
                        state.value.phase,
                        e.service,
                        e.platformError,
                        failure = e.reason,
                    ),
                )
                fail(e.recoveryMessage(), timedOut = e.reason == DiscoveryFailureReason.TIMEOUT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnostics.record(
                    PairingDiagnostic(PairingDiagnosticEvent.FAILED, state.value.phase),
                )
                // Provider/NSD messages may contain untrusted data; show fixed, actionable text.
                fail(
                    "Wireless debugging is unavailable. Check permissions and Wi-Fi, then retry or use advanced setup.",
                )
            } finally {
                drainSubmissions()
                if (!state.value.ready) withContext(NonCancellable) { shell.disconnect() }
            }
        }
        return true
    }

    /**
     * Transition before dispatch prevents duplicate replies; tokens bind replies to one live
     * service.
     */
    @Synchronized
    fun submit(sessionId: String?, token: String?, code: CharSequence?): Boolean {
        val current = state.value
        if (
            !current.acceptsCode ||
                sessionId != current.sessionId ||
                token == null ||
                token != current.replyToken
        ) {
            diagnostics.record(
                PairingDiagnostic(PairingDiagnosticEvent.REPLY_REJECTED, current.phase),
            )
            return false
        }
        if (!PairingCode.isValid(code)) {
            diagnostics.record(
                PairingDiagnostic(PairingDiagnosticEvent.REPLY_REJECTED, current.phase),
            )
            mutableState.value =
                current.copy(message = "Enter exactly six digits from the system pairing screen.")
            return false
        }
        val submission = Submission(token, CharArray(6) { code!![it] })
        mutableState.value =
            current.copy(
                phase =
                    if (current.pairingEndpoint == null) PairingPhase.SEARCHING_PAIRING_SERVICE
                    else PairingPhase.PAIRING_IN_PROGRESS,
                replyToken = null,
                message =
                    if (current.pairingEndpoint == null)
                        "Code received. Discovering the pairing endpoint; keep the system pairing dialog open."
                    else null,
            )
        if (submissions.trySend(submission).isFailure) {
            submission.clear()
            mutableState.value = current.copy(message = "Try submitting the current code again.")
            return false
        }
        diagnostics.record(
            PairingDiagnostic(PairingDiagnosticEvent.REPLY_ACCEPTED, state.value.phase),
        )
        return true
    }

    suspend fun cancel(message: String = "Pairing cancelled. No operations were replayed.") {
        val targetId = state.value.sessionId
        val wasReady = state.value.ready
        withContext(NonCancellable) {
            cancellationMutex.withLock {
                // A queued duplicate cancel owns the old session, even if a new start already ran.
                if (
                    state.value.sessionId != targetId ||
                        state.value.phase in
                            setOf(PairingPhase.DISCONNECTED, PairingPhase.CANCELLED)
                )
                    return@withLock
                val job =
                    synchronized(this) {
                        mutableState.value =
                            state.value.copy(phase = PairingPhase.DISCONNECTING, replyToken = null)
                        sessionJob.also { it?.cancel() }
                    }
                job?.join()
                shell.disconnect()
                drainSubmissions()
                update {
                    it.copy(
                        phase = if (wasReady) PairingPhase.DISCONNECTED else PairingPhase.CANCELLED,
                        transportVerified = false,
                        shellUid = null,
                        message = message,
                    )
                }
                diagnostics.record(
                    PairingDiagnostic(PairingDiagnosticEvent.CANCELLED, state.value.phase),
                )
            }
        }
    }

    fun reportStartFailure() =
        permissionRequired(
            "Android could not start the pairing notification. Keep AETHER open and use in-app discovery or advanced setup.",
        )

    suspend fun timeout() {
        cancel(
            "Wireless pairing exceeded its foreground-service deadline. Retry with the system pairing dialog open.",
        )
        fail(
            "Timed out. Retry with Wi-Fi enabled and the system pairing dialog open.",
            timedOut = true,
        )
    }

    suspend fun notificationFailed() {
        cancel(
            "Pairing notification could not be posted. Open AETHER and use in-app pairing or enable the pairing notification channel.",
        )
        fail(
            "Pairing notification could not be posted. Enable notifications and the pairing channel, or use in-app code input.",
        )
    }

    /** Called only after the advanced pairing client returns its real Success result. */
    @Synchronized
    fun recordManualPairing(host: String, port: Int) {
        if (state.value.active) return
        update {
            it.copy(
                phase = if (it.ready) PairingPhase.CONNECTED else PairingPhase.IDLE,
                paired = true,
                pairingEndpoint = DiscoveredAdbEndpoint("manual", host, port),
                replyToken = null,
                message = null,
            )
        }
    }

    /**
     * Advanced fallback uses the same capability verification and lifecycle as discovered targets.
     */
    @Synchronized
    fun connectManual(host: String, port: Int): Boolean {
        if (state.value.active || state.value.ready || sessionJob?.isActive == true) return false
        if (!id.mybase.aether.security.InputValidators.isValidHost(host) || port !in 1..65535)
            return false
        mutableState.value =
            state.value.copy(
                phase = PairingPhase.CONNECTING,
                sessionId = UUID.randomUUID().toString(),
                replyToken = null,
                transportVerified = false,
                shellUid = null,
                message = null,
            )
        sessionJob = scope.launch {
            try {
                withTimeout(30_000) { verifyEndpoint(DiscoveredAdbEndpoint("manual", host, port)) }
            } catch (e: TimeoutCancellationException) {
                fail("Manual connection timed out. Check the current ADB connection port.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Manual connection failed. Check Wireless debugging and local network access.")
            } finally {
                if (!state.value.ready) withContext(NonCancellable) { shell.disconnect() }
            }
        }
        return true
    }

    private suspend fun awaitPairing(initialToken: String) = coroutineScope {
        // Code and endpoint are independent inputs. This actor owns both, and never retains a
        // submitted code across endpoint replacement, timeout or cancellation.
        val endpoints = Channel<DiscoveredAdbEndpoint?>(32)
        val expired = Channel<Unit>(Channel.CONFLATED)
        var endpoint: DiscoveredAdbEndpoint? = null
        var generation = initialToken
        var pending: Submission? = null
        var attempt: Deferred<PairingResult>? = null
        fun deadline() = launch {
            delay(discoveryTimeoutMs)
            expired.send(Unit)
        }
        var deadlineJob = deadline()
        fun offerCode(message: String? = null) {
            generation = UUID.randomUUID().toString()
            update {
                it.copy(
                    phase = PairingPhase.WAITING_FOR_PAIRING_CODE,
                    pairingEndpoint = endpoint,
                    replyToken = generation,
                    message =
                        message
                            ?: if (endpoint == null)
                                "Open Wireless debugging → Pair device with pairing code. You can submit the code while discovery runs."
                            else null,
                )
            }
        }
        update {
            if (it.replyToken == null) it // An immediate notification reply may already be queued.
            else
                it.copy(
                    phase = PairingPhase.WAITING_FOR_PAIRING_CODE,
                    message =
                        "Open Pair device with pairing code, then enter the code in AETHER's notification. Discovery runs independently.",
                )
        }
        val search = launch {
            discovery.watch(AdbServiceType.PAIRING).collect { endpoints.send(it) }
        }
        try {
            var authenticated = false
            while (!authenticated) {
                select<Unit> {
                    // Prefer a completed authentication over the server withdrawing its temporary
                    // advertisement immediately after success.
                    attempt?.onAwait { result ->
                        attempt = null
                        pending = null // Cleared by the protocol job's finally block.
                        when (result) {
                            PairingResult.Success -> {
                                update {
                                    it.copy(
                                        phase = PairingPhase.PAIRING_SUCCESS,
                                        paired = true,
                                        replyToken = null,
                                        message = null,
                                    )
                                }
                                diagnostics.record(
                                    PairingDiagnostic(
                                        PairingDiagnosticEvent.PAIRING_AUTHENTICATED,
                                        state.value.phase,
                                    ),
                                )
                                authenticated = true
                            }
                            else -> {
                                diagnostics.record(
                                    PairingDiagnostic(
                                        PairingDiagnosticEvent.PAIRING_REJECTED,
                                        state.value.phase,
                                        outcome =
                                            when (result) {
                                                PairingResult.InvalidCode ->
                                                    PairingOutcome.INVALID_CODE
                                                PairingResult.Timeout -> PairingOutcome.TIMEOUT
                                                PairingResult.ConnectionRefused ->
                                                    PairingOutcome.CONNECTION_REFUSED
                                                else -> PairingOutcome.NETWORK_ERROR
                                            },
                                    ),
                                )
                                offerCode(
                                    when (result) {
                                        PairingResult.InvalidCode ->
                                            "Code rejected or expired. Check the current six-digit code and retry."
                                        PairingResult.Timeout ->
                                            "Pairing timed out. Reopen the system pairing screen and retry."
                                        PairingResult.ConnectionRefused ->
                                            "Pairing endpoint closed. Reopen the system pairing screen."
                                        else ->
                                            "Pairing failed. Check Wireless debugging and Wi-Fi, then retry."
                                    },
                                )
                            }
                        }
                    }
                    endpoints.onReceive { next ->
                        if (next != endpoint) {
                            val replacing = endpoint != null
                            if (replacing) {
                                attempt?.cancelAndJoin()
                                attempt = null
                                pending?.clear()
                                pending = null
                                synchronized(this@WirelessPairingController) {
                                    drainSubmissions()
                                    endpoint = next
                                    offerCode(
                                        "Pairing endpoint changed. Enter the current system pairing code again.",
                                    )
                                }
                            } else {
                                endpoint = next
                                // First resolution must not revoke an inline reply the user is
                                // already typing. Only replacement/loss rotates the generation.
                                update {
                                    it.copy(
                                        pairingEndpoint = next,
                                        message = if (it.replyToken != null) null else it.message,
                                    )
                                }
                            }
                            deadlineJob.cancelAndJoin()
                            while (expired.tryReceive().isSuccess) {}
                            if (next == null) deadlineJob = deadline()
                        }
                    }
                    submissions.onReceive { submission ->
                        if (submission.token != generation) submission.clear()
                        else pending = submission
                    }
                    expired.onReceive {
                        throw AdbDiscoveryException(
                            DiscoveryFailureReason.TIMEOUT,
                            AdbServiceType.PAIRING,
                        )
                    }
                }
                val code = pending
                val target = endpoint
                if (!authenticated && attempt == null && code != null && target != null) {
                    update {
                        it.copy(
                            phase = PairingPhase.PAIRING_IN_PROGRESS,
                            replyToken = null,
                            message = null,
                        )
                    }
                    attempt = async {
                        diagnostics.record(
                            PairingDiagnostic(
                                PairingDiagnosticEvent.PAIRING_CLIENT_INITIALIZED,
                                state.value.phase,
                            ),
                        )
                        try {
                            pairing.pair(target.host, target.port, String(code.digits))
                        } finally {
                            code.clear()
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                search.cancelAndJoin()
                deadlineJob.cancelAndJoin()
                attempt?.cancelAndJoin()
                pending?.clear()
                endpoints.cancel()
                expired.cancel()
            }
        }
    }

    private suspend fun connectDiscovered() = coroutineScope {
        update {
            it.copy(
                phase = PairingPhase.DISCOVERING_CONNECT_ENDPOINT,
                replyToken = null,
                message = "Finding the separate ADB connection service…",
            )
        }
        // Start a fresh discovery: never reuse a cached pairing or connection port.
        val verified = CompletableDeferred<Unit>()
        val connect = launch {
            discovery.watch(AdbServiceType.CONNECT).collectLatest { endpoint ->
                if (verified.isCompleted) return@collectLatest
                if (endpoint == null) {
                    shell.disconnect()
                    update {
                        it.copy(
                            phase = PairingPhase.DISCOVERING_CONNECT_ENDPOINT,
                            connectionEndpoint = null,
                            transportVerified = false,
                            shellUid = null,
                        )
                    }
                    return@collectLatest
                }
                verifyEndpoint(endpoint)
                verified.complete(Unit)
            }
        }
        try {
            try {
                withTimeout(discoveryTimeoutMs) { verified.await() }
            } catch (e: TimeoutCancellationException) {
                throw AdbDiscoveryException(DiscoveryFailureReason.TIMEOUT, AdbServiceType.CONNECT)
            }
        } finally {
            connect.cancelAndJoin()
        }
    }

    private fun fail(message: String, timedOut: Boolean = false) = update {
        it.copy(
            phase =
                if (timedOut) PairingPhase.TIMED_OUT
                else if (
                    it.paired ||
                        it.phase in
                            setOf(
                                PairingPhase.DISCOVERING_CONNECT_ENDPOINT,
                                PairingPhase.CONNECTING,
                                PairingPhase.VERIFYING_SHELL,
                            )
                )
                    PairingPhase.CONNECTION_FAILED
                else PairingPhase.PAIRING_FAILED,
            replyToken = null,
            transportVerified = false,
            shellUid = null,
            message = message,
        )
    }

    private suspend fun verifyEndpoint(endpoint: DiscoveredAdbEndpoint) {
        update {
            it.copy(phase = PairingPhase.CONNECTING, connectionEndpoint = endpoint, message = null)
        }
        if (shell.connect(endpoint.host, endpoint.port).isFailure) {
            diagnostics.record(
                PairingDiagnostic(PairingDiagnosticEvent.TRANSPORT_FAILED, state.value.phase),
            )
            fail(
                "ADB connection failed. Enable Wireless debugging and retry Reconnect; pairing identity is retained.",
            )
            return
        }
        update { it.copy(phase = PairingPhase.VERIFYING_SHELL, transportVerified = true) }
        diagnostics.record(
            PairingDiagnostic(PairingDiagnosticEvent.TRANSPORT_ESTABLISHED, state.value.phase),
        )
        val uid = shell.exec("id", "-u", timeoutMs = 10_000).getOrNull()?.trim()
        diagnostics.record(
            PairingDiagnostic(PairingDiagnosticEvent.SHELL_COMMAND_EXECUTED, state.value.phase),
        )
        currentCoroutineContext().ensureActive()
        // UID must be exact, not a substring from an error or partial output.
        if (uid != "2000" || shell.state.value !is AdbConnectionState.Connected) {
            diagnostics.record(
                PairingDiagnostic(PairingDiagnosticEvent.SHELL_UID_REJECTED, state.value.phase),
            )
            shell.disconnect()
            fail(
                "ADB opened, but Android shell UID 2000 could not be verified. System management remains unavailable.",
            )
        } else {
            rememberConnection(endpoint.host, endpoint.port)
            currentCoroutineContext().ensureActive()
            // Persistence suspends: the reader may have lost transport while preferences were
            // written.
            if (shell.state.value !is AdbConnectionState.Connected) {
                shell.disconnect()
                fail(
                    "ADB disconnected during shell verification. Reconnect to rediscover the current endpoint.",
                )
                return
            }
            update {
                it.copy(
                    phase = PairingPhase.CONNECTED,
                    shellUid = 2000,
                    connectedAt = now(),
                    message = null,
                )
            }
            diagnostics.record(
                PairingDiagnostic(
                    PairingDiagnosticEvent.SHELL_UID_VERIFIED,
                    state.value.phase,
                    uid = 2000,
                ),
            )
        }
    }

    @Synchronized
    private fun update(transform: (WirelessPairingState) -> WirelessPairingState) {
        val next = transform(state.value)
        check(PairingTransitions.permits(state.value.phase, next.phase)) {
            "Illegal pairing state transition: ${state.value.phase} → ${next.phase}"
        }
        check(
            next.phase != PairingPhase.CONNECTED ||
                (next.transportVerified && next.shellUid == 2000),
        )
        check(
            next.phase != PairingPhase.WAITING_FOR_PAIRING_CODE || next.replyToken != null,
        )
        mutableState.value = next
        diagnostics.record(PairingDiagnostic(PairingDiagnosticEvent.STATE_CHANGED, next.phase))
    }

    private fun drainSubmissions() {
        while (true) (submissions.tryReceive().getOrNull() ?: break).clear()
    }
}
