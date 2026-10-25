package id.mybase.aether.adb.pairing

import id.mybase.aether.adb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WirelessPairingControllerTest {
    private val pairingEndpoint = DiscoveredAdbEndpoint("local-pair", "127.0.0.1", 37001)
    private val connectionEndpoint = DiscoveredAdbEndpoint("local-connect", "127.0.0.1", 39001)

    private class Discovery : AdbServiceDiscovery {
        val pairing = MutableStateFlow<DiscoveredAdbEndpoint?>(null)
        val connection = MutableStateFlow<DiscoveredAdbEndpoint?>(null)
        val sessions = mutableListOf<AdbServiceType>()
        var active = 0
        var failure: Exception? = null

        override fun watch(type: AdbServiceType): Flow<DiscoveredAdbEndpoint?> = flow {
            sessions += type
            active++
            try {
                failure?.let { throw it }
                emitAll(if (type == AdbServiceType.PAIRING) pairing else connection)
            } finally {
                active--
            }
        }
    }

    private class Pairer : AdbPairingClient {
        var calls = 0
        var result: PairingResult = PairingResult.Success
        var hold: CompletableDeferred<Unit>? = null
        var cancellations = 0
        var endpoint: Pair<String, Int>? = null

        override suspend fun pair(
            host: String,
            port: Int,
            pairingCode: String,
            timeoutMs: Long,
        ): PairingResult {
            assertTrue(PairingCode.isValid(pairingCode))
            calls++
            endpoint = host to port
            try {
                hold?.await()
                return result
            } catch (e: CancellationException) {
                cancellations++
                throw e
            }
        }
    }

    private class Shell : AdbShell {
        override val state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
        var uid = Result.success("2000\n")
        var connectResult = Result.success(Unit)
        var connection: Pair<String, Int>? = null
        var connects = 0
        var disconnects = 0
        var execs = 0
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun connect(host: String, port: Int, timeoutMs: Long): Result<Unit> {
            connects++
            connection = host to port
            state.value =
                if (connectResult.isSuccess) AdbConnectionState.Connected(host, port)
                else AdbConnectionState.Failed("fixture")
            return connectResult
        }

        override suspend fun disconnect() {
            disconnects++
            state.value = AdbConnectionState.Disconnected
        }

        override suspend fun exec(vararg args: String, timeoutMs: Long): Result<String> {
            assertEquals(listOf("id", "-u"), args.toList())
            execs++
            hold?.await()
            return uid
        }
    }

    private class Fixture(scope: CoroutineScope) {
        val discovery = Discovery()
        val pairer = Pairer()
        val shell = Shell()
        val remembered = mutableListOf<Pair<String, Int>>()
        var whileRemembering: suspend () -> Unit = {}
        val diagnostics = mutableListOf<PairingDiagnostic>()
        val controller =
            WirelessPairingController(
                pairer,
                shell,
                discovery,
                scope,
                rememberConnection = { host, port ->
                    remembered += host to port
                    whileRemembering()
                },
                now = { 1234L },
                diagnostics = PairingDiagnostics { diagnostics += it },
            )

        fun submit(code: CharSequence? = "123456") =
            controller.state.value.let {
                controller.submit(it.sessionId, it.replyToken, code)
            }
    }

    private fun TestScope.fixture() = Fixture(backgroundScope)

    private fun TestScope.findPairing(f: Fixture) {
        assertTrue(f.controller.start())
        runCurrent()
        f.discovery.pairing.value = pairingEndpoint
        runCurrent()
        assertEquals(PairingPhase.WAITING_FOR_PAIRING_CODE, f.controller.state.value.phase)
    }

    private fun TestScope.completePairing(f: Fixture) {
        findPairing(f)
        assertTrue(f.submit())
        runCurrent()
    }

    @Test
    fun codeCanBeSubmittedBeforeDiscoveryAndPairsOnlyAfterResolution() = runTest {
        val f = fixture()
        assertTrue(f.controller.start())
        runCurrent()
        assertTrue(f.controller.state.value.notificationContent().acceptsCode)
        assertTrue(f.submit())
        assertFalse(f.submit())
        runCurrent()
        assertEquals(0, f.pairer.calls)
        advanceTimeBy(5_000)
        f.discovery.pairing.value = pairingEndpoint
        runCurrent()
        assertEquals(1, f.pairer.calls)
        assertTrue(f.controller.state.value.paired)
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        assertEquals(2000, f.controller.state.value.shellUid)
        assertTrue(f.controller.state.value.ready)
    }

    @Test
    fun unresolvedPairingServiceStopsAtDiscoveryDeadline() = runTest {
        val f = fixture()
        f.controller.start()
        runCurrent()
        advanceTimeBy(45_001)
        runCurrent()
        assertFalse(f.controller.state.value.active)
        assertEquals(0, f.discovery.active)
        assertEquals(0, f.pairer.calls)
        assertTrue(f.controller.state.value.message.orEmpty().contains("Timed out"))
    }

    @Test
    fun immediateReplyBeforeTheSessionCoroutineStartsIsRetainedUntilResolution() = runTest {
        val f = fixture()
        f.controller.start()
        assertTrue(f.controller.state.value.acceptsCode)
        assertTrue(f.submit())
        runCurrent()
        assertEquals(0, f.pairer.calls)
        f.discovery.pairing.value = pairingEndpoint
        runCurrent()
        assertTrue(f.controller.state.value.paired)
        assertEquals(1, f.pairer.calls)
    }

    @Test
    fun firstResolutionDoesNotInvalidateAnInlineReplyAlreadyBeingTyped() = runTest {
        val f = fixture()
        f.controller.start()
        runCurrent()
        val typing = f.controller.state.value
        f.discovery.pairing.value = pairingEndpoint
        runCurrent()
        assertEquals(typing.replyToken, f.controller.state.value.replyToken)
        assertTrue(f.controller.submit(typing.sessionId, typing.replyToken, "123456"))
        runCurrent()
        assertTrue(f.controller.state.value.paired)
    }

    @Test
    fun earlyCodeIsDiscardedAfterDiscoveryTimeoutAndCannotBeReplayed() = runTest {
        val f = fixture()
        f.controller.start()
        runCurrent()
        val old = f.controller.state.value
        assertTrue(f.submit())
        advanceTimeBy(45_001)
        runCurrent()
        assertEquals(PairingPhase.TIMED_OUT, f.controller.state.value.phase)
        assertNull(f.controller.state.value.replyToken)
        assertFalse(f.controller.submit(old.sessionId, old.replyToken, "123456"))
        f.discovery.pairing.value = pairingEndpoint
        assertTrue(f.controller.start())
        runCurrent()
        assertEquals(0, f.pairer.calls)
        assertTrue(f.controller.state.value.acceptsCode)
    }

    @Test
    fun uiCollectorRecreationDoesNotOwnOrCancelDiscovery() = runTest {
        val f = fixture()
        f.controller.start()
        val oldUi = backgroundScope.launch { f.controller.state.collect {} }
        runCurrent()
        oldUi.cancelAndJoin()
        val newUi = backgroundScope.launch { f.controller.state.collect {} }
        runCurrent()
        assertEquals(1, f.discovery.active)
        assertTrue(f.submit())
        f.discovery.pairing.value = pairingEndpoint
        runCurrent()
        assertTrue(f.controller.state.value.paired)
        newUi.cancelAndJoin()
    }

    @Test
    fun invalidCodeBeforeDiscoveryNeverDispatchesOrPersistsIt() = runTest {
        val f = fixture()
        f.controller.start()
        runCurrent()
        val token = f.controller.state.value.replyToken
        assertFalse(f.submit("123 56"))
        assertFalse(f.submit(null))
        assertEquals(token, f.controller.state.value.replyToken)
        f.discovery.pairing.value = pairingEndpoint
        runCurrent()
        assertEquals(0, f.pairer.calls)
        assertFalse(f.diagnostics.toString().contains("123 56"))
    }

    @Test
    fun discoveryResolutionErrorAfterEarlyCodeFailsActionablyAndAllowsRetry() = runTest {
        val f = fixture()
        f.discovery.failure =
            AdbDiscoveryException(DiscoveryFailureReason.RESOLUTION, AdbServiceType.PAIRING, 3)
        f.controller.start()
        assertTrue(f.submit())
        runCurrent()
        assertEquals(PairingPhase.PAIRING_FAILED, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.message!!.contains("could not resolve"))
        assertEquals(0, f.pairer.calls)
        assertEquals(0, f.discovery.active)
        assertFalse(f.diagnostics.toString().contains("123456"))
        f.discovery.failure = null
        assertTrue(f.controller.start())
    }

    @Test
    fun diagnosticSuccessIsEmittedOnlyAfterExactShellUidVerification() = runTest {
        val f = fixture()
        completePairing(f)
        assertTrue(f.diagnostics.any { it.event == PairingDiagnosticEvent.PAIRING_AUTHENTICATED })
        assertFalse(f.diagnostics.any { it.event == PairingDiagnosticEvent.SHELL_UID_VERIFIED })
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        assertEquals(
            2000,
            f.diagnostics.single { it.event == PairingDiagnosticEvent.SHELL_UID_VERIFIED }.uid,
        )
        assertFalse(f.diagnostics.toString().contains("123456"))
    }

    @Test
    fun foregroundTimeoutAndNotificationFailureStopAllDiscovery() = runTest {
        for (timeout in listOf(false, true)) {
            val f = fixture()
            f.controller.start()
            runCurrent()
            if (timeout) f.controller.timeout() else f.controller.notificationFailed()
            assertEquals(
                if (timeout) PairingPhase.TIMED_OUT else PairingPhase.PAIRING_FAILED,
                f.controller.state.value.phase,
            )
            assertEquals(0, f.discovery.active)
            assertFalse(f.controller.state.value.active)
            assertTrue(f.controller.start())
        }
    }

    @Test
    fun `strict numeric code validation accepts leading zero`() {
        assertTrue(PairingCode.isValid("000001"))
        listOf(null, "", "12345", "1234567", "123 56", "12-3456", "１２３４５６", "123456\n", "12345x")
            .forEach {
                assertFalse("Rejected malformed fixture", PairingCode.isValid(it))
            }
    }

    @Test
    fun `start discovers pairing without connect and duplicate starts are ignored`() = runTest {
        val f = fixture()
        f.controller.start()
        runCurrent()
        assertFalse(f.controller.start())
        assertEquals(listOf(AdbServiceType.PAIRING), f.discovery.sessions)
        assertEquals(0, f.shell.connects)
        assertEquals(PairingPhase.WAITING_FOR_PAIRING_CODE, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.acceptsCode)
    }

    @Test
    fun `invalid and empty replies do not dispatch authentication`() = runTest {
        val f = fixture()
        findPairing(f)
        assertFalse(f.submit("123 56"))
        assertFalse(f.submit(null))
        runCurrent()
        assertEquals(0, f.pairer.calls)
        assertEquals(PairingPhase.WAITING_FOR_PAIRING_CODE, f.controller.state.value.phase)
        assertNotNull(f.controller.state.value.message)
    }

    @Test
    fun `submission phase changes atomically and duplicate is rejected`() = runTest {
        val f = fixture()
        findPairing(f)
        f.pairer.hold = CompletableDeferred()
        assertTrue(f.submit())
        assertFalse(f.submit())
        runCurrent()
        assertEquals(1, f.pairer.calls)
        assertEquals(PairingPhase.PAIRING_IN_PROGRESS, f.controller.state.value.phase)
    }

    @Test
    fun `old endpoint reply token is rejected after service restart`() = runTest {
        val f = fixture()
        findPairing(f)
        val old = f.controller.state.value
        f.discovery.pairing.value = null
        runCurrent()
        f.discovery.pairing.value = pairingEndpoint.copy(port = 37002)
        runCurrent()
        assertFalse(f.controller.submit(old.sessionId, old.replyToken, "123456"))
        assertEquals(0, f.pairer.calls)
    }

    @Test
    fun `service loss cancels in flight pairing and releases secret`() = runTest {
        val f = fixture()
        findPairing(f)
        f.pairer.hold = CompletableDeferred()
        f.submit()
        runCurrent()
        f.discovery.pairing.value = null
        runCurrent()
        assertEquals(1, f.pairer.cancellations)
        assertEquals(PairingPhase.WAITING_FOR_PAIRING_CODE, f.controller.state.value.phase)
        assertNotNull(f.controller.state.value.replyToken)
        assertNull(f.controller.state.value.pairingEndpoint)
        assertFalse(f.controller.state.value.paired)
    }

    @Test
    fun `pairing success discovers separate connection endpoint`() = runTest {
        val f = fixture()
        completePairing(f)
        assertTrue(f.controller.state.value.paired)
        assertEquals(PairingPhase.DISCOVERING_CONNECT_ENDPOINT, f.controller.state.value.phase)
        assertEquals(listOf(AdbServiceType.PAIRING, AdbServiceType.CONNECT), f.discovery.sessions)
        assertEquals(0, f.shell.connects)
        assertEquals("127.0.0.1" to 37001, f.pairer.endpoint)
    }

    @Test
    fun `connected requires transport and separate shell uid verification`() = runTest {
        val f = fixture()
        completePairing(f)
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        assertTrue(f.controller.state.value.ready)
        assertEquals(1, f.shell.execs)
        assertEquals("127.0.0.1" to 39001, f.shell.connection)
        assertEquals(listOf("127.0.0.1" to 39001), f.remembered)
        assertEquals(1234L, f.controller.state.value.connectedAt)
        assertEquals(0, f.discovery.active)
        assertFalse(f.controller.state.value.toString().contains("123456"))
    }

    @Test
    fun `unverified transport is never shown connected while shell check waits`() = runTest {
        val f = fixture()
        completePairing(f)
        f.shell.hold = CompletableDeferred()
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        assertEquals(PairingPhase.VERIFYING_SHELL, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.transportVerified)
        assertFalse(f.controller.state.value.ready)
    }

    @Test
    fun `wrong uid and misleading uid output fail closed`() = runTest {
        for (uid in listOf("0", "10000", "error 2000", "2000\nFailure", "")) {
            val f = fixture()
            completePairing(f)
            f.shell.uid = Result.success(uid)
            f.discovery.connection.value = connectionEndpoint
            runCurrent()
            assertEquals(PairingPhase.CONNECTION_FAILED, f.controller.state.value.phase)
            assertFalse(f.controller.state.value.ready)
            assertTrue(f.remembered.isEmpty())
            assertTrue(f.shell.state.value is AdbConnectionState.Disconnected)
        }
    }

    @Test
    fun `shell execution failure disconnects and retains pairing`() = runTest {
        val f = fixture()
        completePairing(f)
        f.shell.uid = Result.failure(Exception("fixture"))
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        assertFalse(f.controller.state.value.ready)
        assertTrue(f.controller.state.value.paired)
    }

    @Test
    fun `connection refusal preserves real pairing without fabricating connection`() = runTest {
        val f = fixture()
        completePairing(f)
        f.shell.connectResult = Result.failure(Exception("refused"))
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        assertTrue(f.controller.state.value.paired)
        assertFalse(f.controller.state.value.transportVerified)
        assertEquals(PairingPhase.CONNECTION_FAILED, f.controller.state.value.phase)
    }

    @Test
    fun `rejected code allows fresh reply without persisting secret`() = runTest {
        val f = fixture()
        findPairing(f)
        val old = f.controller.state.value
        f.pairer.result = PairingResult.InvalidCode
        f.submit()
        runCurrent()
        val retry = f.controller.state.value
        assertEquals(PairingPhase.WAITING_FOR_PAIRING_CODE, retry.phase)
        assertNotEquals(old.replyToken, retry.replyToken)
        assertFalse(retry.paired)
        assertTrue(retry.message!!.contains("expired"))
        f.pairer.result = PairingResult.Success
        assertTrue(f.submit())
        runCurrent()
        assertTrue(f.controller.state.value.paired)
    }

    @Test
    fun `pairing failures map to actionable non secret messages`() = runTest {
        for (result in
            listOf(
                PairingResult.Timeout,
                PairingResult.ConnectionRefused,
                PairingResult.NetworkError("123456 private fixture"),
            )) {
            val f = fixture()
            findPairing(f)
            f.pairer.result = result
            f.submit()
            runCurrent()
            assertFalse(f.controller.state.value.paired)
            assertNotNull(f.controller.state.value.message)
            assertFalse(f.controller.state.value.toString().contains("123456"))
        }
    }

    @Test
    fun `pairing discovery timeout releases subscriptions`() = runTest {
        val f = fixture()
        f.controller.start()
        runCurrent()
        advanceTimeBy(150_001)
        runCurrent()
        assertEquals(PairingPhase.TIMED_OUT, f.controller.state.value.phase)
        assertEquals(0, f.discovery.active)
    }

    @Test
    fun `connect discovery timeout retains pairing but never connected`() = runTest {
        val f = fixture()
        completePairing(f)
        advanceTimeBy(45_001)
        runCurrent()
        assertEquals(PairingPhase.TIMED_OUT, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.paired)
        assertFalse(f.controller.state.value.ready)
        assertEquals(0, f.discovery.active)
    }

    @Test
    fun `cancel ends discovery and rejects late submissions`() = runTest {
        val f = fixture()
        findPairing(f)
        val old = f.controller.state.value
        f.controller.cancel()
        runCurrent()
        assertEquals(PairingPhase.CANCELLED, f.controller.state.value.phase)
        assertEquals(0, f.discovery.active)
        assertFalse(f.controller.submit(old.sessionId, old.replyToken, "123456"))
    }

    @Test
    fun `cancel pairing prevents automatic connection`() = runTest {
        val f = fixture()
        findPairing(f)
        f.pairer.hold = CompletableDeferred()
        f.submit()
        runCurrent()
        f.controller.cancel()
        runCurrent()
        assertEquals(0, f.shell.connects)
        assertFalse(f.controller.state.value.paired)
    }

    @Test
    fun `process recreation rejects prior notification and does not auto start`() = runTest {
        val f = fixture()
        findPairing(f)
        val old = f.controller.state.value
        val recreated = fixture()
        assertFalse(recreated.controller.submit(old.sessionId, old.replyToken, "123456"))
        assertEquals(PairingPhase.IDLE, recreated.controller.state.value.phase)
        assertTrue(recreated.discovery.sessions.isEmpty())
    }

    @Test
    fun `notification permission denial leaves usable idle recovery state`() = runTest {
        val f = fixture()
        f.controller.permissionRequired("Notifications denied; use in-app discovery")
        assertEquals(PairingPhase.WAITING_FOR_PERMISSION, f.controller.state.value.phase)
        assertTrue(f.discovery.sessions.isEmpty())
        assertTrue(f.controller.start())
        runCurrent()
        assertEquals(PairingPhase.WAITING_FOR_PAIRING_CODE, f.controller.state.value.phase)
    }

    @Test
    fun `reconnect starts fresh connect discovery without pairing code`() = runTest {
        val f = fixture()
        assertTrue(f.controller.start(reconnectOnly = true))
        runCurrent()
        assertEquals(listOf(AdbServiceType.CONNECT), f.discovery.sessions)
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        assertTrue(f.controller.state.value.ready)
        assertEquals(0, f.pairer.calls)
    }

    @Test
    fun `connection loss removes ready capability and enables reconnect`() = runTest {
        val f = fixture()
        completePairing(f)
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        f.shell.state.value = AdbConnectionState.Failed("network loss")
        runCurrent()
        assertFalse(f.controller.state.value.ready)
        assertTrue(f.controller.start(reconnectOnly = true))
        runCurrent()
        assertTrue(f.controller.state.value.ready)
        assertEquals(1, f.pairer.calls)
    }

    @Test
    fun `discovery failure and provider exception are actionable and release resources`() =
        runTest {
            val f = fixture()
            f.discovery.failure = SecurityException("fixture 123456")
            f.controller.start()
            runCurrent()
            assertEquals(PairingPhase.PAIRING_FAILED, f.controller.state.value.phase)
            assertEquals(0, f.discovery.active)
            assertFalse(f.controller.state.value.toString().contains("123456"))
        }

    @Test
    fun `connection endpoint loss during verification cancels obsolete command`() = runTest {
        val f = fixture()
        completePairing(f)
        f.shell.hold = CompletableDeferred()
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        f.discovery.connection.value = null
        runCurrent()
        assertFalse(f.controller.state.value.ready)
        assertEquals(PairingPhase.DISCOVERING_CONNECT_ENDPOINT, f.controller.state.value.phase)
        assertTrue(f.remembered.isEmpty())
    }

    @Test
    fun `notification models expose inline input only for live waiting state`() {
        PairingPhase.entries.forEach { phase ->
            assertEquals(
                phase in
                    setOf(
                        PairingPhase.PREPARING,
                        PairingPhase.WAITING_FOR_PAIRING_CODE,
                        PairingPhase.SEARCHING_PAIRING_SERVICE,
                    ),
                WirelessPairingState(phase = phase, replyToken = "test-token")
                    .notificationContent()
                    .acceptsCode,
            )
        }
        val connecting = WirelessPairingState(phase = PairingPhase.CONNECTING).notificationContent()
        assertFalse(connecting.text.contains("ready"))
    }

    @Test
    fun `invalid discovered ports are rejected`() {
        for (port in listOf(-1, 0, 65536)) {
            assertThrows(IllegalArgumentException::class.java) {
                DiscoveredAdbEndpoint("fixture", "127.0.0.1", port)
            }
        }
    }

    @Test
    fun `illegal state shortcuts to connected are rejected`() {
        assertFalse(
            PairingTransitions.permits(
                PairingPhase.SEARCHING_PAIRING_SERVICE,
                PairingPhase.CONNECTED,
            ),
        )
        assertFalse(
            PairingTransitions.permits(PairingPhase.PAIRING_IN_PROGRESS, PairingPhase.CONNECTED),
        )
        assertTrue(PairingTransitions.permits(PairingPhase.VERIFYING_SHELL, PairingPhase.CONNECTED))
    }

    @Test
    fun `manual fallback verifies capabilities and updates shared dashboard state`() = runTest {
        val f = fixture()
        assertTrue(f.controller.connectManual("127.0.0.1", 39001))
        runCurrent()
        assertTrue(f.controller.state.value.ready)
        assertEquals(0, f.pairer.calls)
        assertEquals(1, f.shell.execs)
        assertTrue(f.discovery.sessions.isEmpty())
    }

    @Test
    fun `manual fallback is blocked while notification session is active`() = runTest {
        val f = fixture()
        findPairing(f)
        assertFalse(f.controller.connectManual("127.0.0.1", 39001))
        assertEquals(0, f.shell.connects)
    }

    @Test
    fun `transport loss during suspending persistence never produces connected badge`() = runTest {
        val f = fixture()
        completePairing(f)
        f.whileRemembering = {
            f.shell.state.value = AdbConnectionState.Failed("fixture lost during persistence")
        }
        f.discovery.connection.value = connectionEndpoint
        runCurrent()
        assertFalse(f.controller.state.value.ready)
        assertEquals(PairingPhase.CONNECTION_FAILED, f.controller.state.value.phase)
        assertNull(f.controller.state.value.connectedAt)
    }

    @Test
    fun `queued reply cancellation prevents authentication before receiver resumes`() = runTest {
        val f = fixture()
        findPairing(f)
        assertTrue(f.submit())
        // No dispatcher advance: cancel while the Channel receive continuation is still queued.
        f.controller.cancel()
        runCurrent()
        assertEquals(0, f.pairer.calls)
        assertEquals(PairingPhase.CANCELLED, f.controller.state.value.phase)
    }

    @Test
    fun `advanced pairing records authentication without fabricating transport or shell`() =
        runTest {
            val f = fixture()
            f.controller.recordManualPairing("127.0.0.1", 37001)
            assertTrue(f.controller.state.value.paired)
            assertEquals(pairingEndpoint.port, f.controller.state.value.pairingEndpoint?.port)
            assertFalse(f.controller.state.value.ready)
            assertNull(f.controller.state.value.shellUid)
            assertFalse(f.controller.state.value.transportVerified)
        }

    @Test
    fun cancelledOldSessionCannotCloseNewSession() = runTest {
        val f = fixture()
        findPairing(f)
        val original = f.controller.state.value.sessionId
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.controller.state.first { it.phase == PairingPhase.CANCELLED }
            assertTrue(f.controller.start(reconnectOnly = true))
        }
        val firstCancel = backgroundScope.async { f.controller.cancel() }
        val duplicateCancel = backgroundScope.async { f.controller.cancel() }
        runCurrent()
        firstCancel.await()
        duplicateCancel.await()
        runCurrent()
        assertNotEquals(original, f.controller.state.value.sessionId)
        assertEquals(PairingPhase.DISCOVERING_CONNECT_ENDPOINT, f.controller.state.value.phase)
    }

    @Test
    fun successfulManualPairingClearsObsoleteFailureState() = runTest {
        val f = fixture()
        f.discovery.failure = IllegalStateException("fixture discovery failure")
        f.controller.start()
        runCurrent()
        assertEquals(PairingPhase.PAIRING_FAILED, f.controller.state.value.phase)
        f.controller.recordManualPairing("127.0.0.1", 37001)
        assertEquals(PairingPhase.IDLE, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.paired)
        assertNull(f.controller.state.value.message)
        assertFalse(f.controller.state.value.ready)
    }
}
