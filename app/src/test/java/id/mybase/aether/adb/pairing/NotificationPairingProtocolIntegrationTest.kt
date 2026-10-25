package id.mybase.aether.adb.pairing

import id.mybase.aether.adb.*
import id.mybase.aether.adb.fakes.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Real protocol code over JVM TLS/socket fixtures, with a controlled NSD boundary. Not adbd
 * hardware.
 */
class NotificationPairingProtocolIntegrationTest {
    @Test
    fun `notification controller pairs connects and verifies uid through actual protocol classes`() =
        verifyFlow(false)

    @Test fun earlyNotificationReplyWaitsForDiscoveryThenUsesRealProtocolAndUid() = verifyFlow(true)

    private fun verifyFlow(earlyReply: Boolean) = runBlocking {
        val exporter = ByteArray(64) { 0xA5.toByte() }
        val pairingServer = FakePairingServer("123456", exporter)
        val adbServer =
            FakeAdbServer(
                openHandler = { destination ->
                    assertEquals("shell:id -u", destination)
                    FakeAdbServer.OpenResponse(listOf("2000\n".toByteArray()))
                },
            )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val keys = FakeAdbKeyMaterial()
        val client =
            TlsPairingClient(
                keys,
                openChannel = { host, port, timeout ->
                    val real = BcTlsPairingTransport.openPairingChannel(host, port, timeout)
                    ConscryptPairingTransport.PairingTlsChannel(
                        real.input,
                        real.output,
                        exporter.copyOf(),
                    ) {
                        real.close()
                    }
                },
            )
        val shell = AdbConnectionManager(keys, client)
        val advertisementReady = CompletableDeferred<Unit>()
        if (!earlyReply) advertisementReady.complete(Unit)
        val discovery =
            object : AdbServiceDiscovery {
                override fun watch(type: AdbServiceType): Flow<DiscoveredAdbEndpoint?> = flow {
                    if (type == AdbServiceType.PAIRING) {
                        emit(null)
                        advertisementReady.await()
                    }
                    emit(
                        DiscoveredAdbEndpoint(
                            type.name,
                            "127.0.0.1",
                            if (type == AdbServiceType.PAIRING) pairingServer.port
                            else adbServer.port,
                        ),
                    )
                    awaitCancellation()
                }
            }
        val controller = WirelessPairingController(client, shell, discovery, scope)
        try {
            assertTrue(controller.start())
            val waiting =
                withTimeout(15_000) {
                    controller.state.first {
                        it.acceptsCode && (earlyReply || it.pairingEndpoint != null)
                    }
                }
            assertTrue(controller.submit(waiting.sessionId, waiting.replyToken, "123456"))
            if (earlyReply) {
                assertNull(controller.state.value.pairingEndpoint)
                assertFalse(pairingServer.completedOk)
                advertisementReady.complete(Unit)
            }
            val completed =
                withTimeout(30_000) {
                    controller.state.first {
                        it.ready ||
                            it.phase == PairingPhase.CONNECTION_FAILED ||
                            it.phase == PairingPhase.PAIRING_FAILED
                    }
                }
            assertTrue(completed.message, completed.ready)
            assertTrue(pairingServer.completedOk)
            assertEquals(2000, completed.shellUid)
            assertEquals(pairingServer.port, completed.pairingEndpoint?.port)
            assertEquals(adbServer.port, completed.connectionEndpoint?.port)
            assertEquals(listOf("shell:id -u"), adbServer.openedDestinations.toList())
        } finally {
            controller.cancel()
            scope.cancel()
            shell.disconnect()
            pairingServer.close()
            adbServer.close()
        }
    }
}
