package id.mybase.aether.adb

import app.cash.turbine.test
import id.mybase.aether.adb.fakes.FakeAdbKeyMaterial
import id.mybase.aether.adb.fakes.FakeAdbServer
import id.mybase.aether.adb.fakes.FakePairingServer
import java.net.ServerSocket
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the [AdbShell] state machine, the exec allowlist, cancellation, and the end-to-end
 * pair+connect flow against in-JVM fake servers.
 */
class AdbConnectionManagerTest {

    private val exporterBytes = ByteArray(64) { 0xA5.toByte() }

    private fun manager(): AdbConnectionManager {
        val keys = FakeAdbKeyMaterial()
        val pairingClient = TlsPairingClient(keys, tlsExporter = { exporterBytes.copyOf() })
        return AdbConnectionManager(keys, pairingClient)
    }

    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `initial state is Disconnected`() = runTest {
        manager().state.test {
            assertEquals(AdbConnectionState.Disconnected, awaitItem())
        }
    }

    @Test
    fun `connect refused transitions through Connecting to Failed`() = runTest {
        val m = manager()
        val port = closedPort()
        m.state.test {
            assertEquals(AdbConnectionState.Disconnected, awaitItem())
            val result = m.connect("127.0.0.1", port, 5_000)
            assertTrue(result.isFailure)
            assertEquals(AdbConnectionState.Connecting("127.0.0.1", port), awaitItem())
            val failed = awaitItem()
            assertTrue(failed is AdbConnectionState.Failed)
        }
    }

    @Test
    fun `connect with invalid host fails fast`() = runTest {
        val m = manager()
        val result = m.connect("not a host!!", 5555, 5_000)
        assertTrue(result.isFailure)
        assertTrue(m.state.value is AdbConnectionState.Failed)
    }

    @Test
    fun `connect timeout transitions to Failed`() = runTest {
        val server = FakeAdbServer(acceptOnly = true)
        val m = manager()
        try {
            m.state.test {
                assertEquals(AdbConnectionState.Disconnected, awaitItem())
                val result = m.connect("127.0.0.1", server.port, 1_500)
                assertTrue(result.isFailure)
                assertEquals(AdbConnectionState.Connecting("127.0.0.1", server.port), awaitItem())
                val failed = awaitItem() as AdbConnectionState.Failed
                assertTrue(failed.reason.contains("timed out", ignoreCase = true))
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `connect then disconnect returns to Disconnected`() = runTest {
        val server = FakeAdbServer()
        val m = manager()
        try {
            m.state.test {
                assertEquals(AdbConnectionState.Disconnected, awaitItem())
                assertTrue(m.connect("127.0.0.1", server.port, 10_000).isSuccess)
                assertEquals(AdbConnectionState.Connecting("127.0.0.1", server.port), awaitItem())
                assertEquals(AdbConnectionState.Connected("127.0.0.1", server.port), awaitItem())
                m.disconnect()
                assertEquals(AdbConnectionState.Disconnected, awaitItem())
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `exec when disconnected fails`() = runTest {
        val result = manager().exec("pm", "list", "packages")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `exec rejects non-allowlisted command`() = runTest {
        val server = FakeAdbServer()
        val m = manager()
        try {
            assertTrue(m.connect("127.0.0.1", server.port, 10_000).isSuccess)
            val result = m.exec("rm", "-rf", "/")
            assertTrue(result.isFailure)
            assertTrue(server.openedDestinations.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun `exec rejects shell metacharacter injection`() = runTest {
        val server = FakeAdbServer()
        val m = manager()
        try {
            assertTrue(m.connect("127.0.0.1", server.port, 10_000).isSuccess)
            for (evil in
                listOf("list;reboot", "list|reboot", "a&b", "\$HOME", "`id`", "a\nb", "a\rb")) {
                val result = m.exec("pm", evil)
                assertTrue("injection via [$evil] must be rejected", result.isFailure)
            }
            assertTrue(server.openedDestinations.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun `exec rejects blank args and empty argv`() = runTest {
        val server = FakeAdbServer()
        val m = manager()
        try {
            assertTrue(m.connect("127.0.0.1", server.port, 10_000).isSuccess)
            assertTrue(m.exec("pm", "  ").isFailure)
            assertTrue(m.exec().isFailure)
            assertTrue(server.openedDestinations.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun `exec sends structured shell command and returns output`() = runTest {
        val server =
            FakeAdbServer(
                openHandler = { _ ->
                    FakeAdbServer.OpenResponse(
                        listOf("first\n".toByteArray(), "second\n".toByteArray()),
                    )
                },
            )
        val m = manager()
        try {
            assertTrue(m.connect("127.0.0.1", server.port, 10_000).isSuccess)
            val result = m.exec("pm", "list", "packages", timeoutMs = 10_000)
            assertTrue(result.isSuccess)
            assertEquals("first\nsecond\n", result.getOrNull())
            // Structured argv: one OPEN with the joined, validated command.
            assertEquals(listOf("shell:pm list packages"), server.openedDestinations)
        } finally {
            server.close()
        }
    }

    @Test
    fun `cancelling a hung exec does not kill the session`() = runTest {
        // Only the first OPEN hangs (never closed); subsequent OPENs complete
        // normally so we can verify the session survived.
        val openCount = java.util.concurrent.atomic.AtomicInteger(0)
        val server =
            FakeAdbServer(
                openHandler = { _ ->
                    if (openCount.getAndIncrement() == 0) {
                        FakeAdbServer.OpenResponse(emptyList(), sendClose = false)
                    } else {
                        FakeAdbServer.OpenResponse(listOf("v1\n".toByteArray()))
                    }
                },
            )
        val m = manager()
        try {
            assertTrue(m.connect("127.0.0.1", server.port, 10_000).isSuccess)
            val hung = async { m.exec("pm", "list", "packages", timeoutMs = 30_000) }
            // Let the OPEN reach the fake, then cancel the exec only.
            kotlinx.coroutines.delay(500)
            hung.cancel()
            try {
                hung.await()
                throw AssertionError("hung exec should have been cancelled")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // expected
            }
            // The session survived: a fresh exec still works.
            val again = m.exec("pm", "version", timeoutMs = 10_000)
            assertTrue(again.isSuccess)
            assertEquals(AdbConnectionState.Connected("127.0.0.1", server.port), m.state.value)
        } finally {
            server.close()
        }
    }

    @Test
    fun `pairAndConnect emits Pairing Paired Connecting Connected`() = runTest {
        val pairing = FakePairingServer(password = "123456", tlsExporterBytes = exporterBytes)
        val adb = FakeAdbServer()
        val m = manager()
        try {
            m.state.test {
                assertEquals(AdbConnectionState.Disconnected, awaitItem())
                val result =
                    m.pairAndConnect(
                        "127.0.0.1",
                        pairing.port,
                        "123456",
                        adb.port,
                        15_000,
                    )
                assertTrue(result.isSuccess)
                assertEquals(AdbConnectionState.Pairing("127.0.0.1", pairing.port), awaitItem())
                assertEquals(AdbConnectionState.Paired, awaitItem())
                assertEquals(AdbConnectionState.Connecting("127.0.0.1", adb.port), awaitItem())
                assertEquals(AdbConnectionState.Connected("127.0.0.1", adb.port), awaitItem())
            }
        } finally {
            pairing.close()
            adb.close()
        }
    }

    @Test
    fun `pairAndConnect with wrong code fails without connecting`() = runTest {
        val pairing = FakePairingServer(password = "654321", tlsExporterBytes = exporterBytes)
        val adb = FakeAdbServer()
        val m = manager()
        try {
            val result =
                m.pairAndConnect(
                    "127.0.0.1",
                    pairing.port,
                    "123456",
                    adb.port,
                    15_000,
                )
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is PairingFailedException)
            val state = m.state.value
            assertTrue(state is AdbConnectionState.Failed)
            assertTrue(adb.openedDestinations.isEmpty())
        } finally {
            pairing.close()
            adb.close()
        }
    }

    @Test
    fun `reconnect is disconnect then connect`() = runTest {
        val server = FakeAdbServer()
        val m = manager()
        try {
            assertTrue(m.connect("127.0.0.1", server.port, 10_000).isSuccess)
            m.disconnect()
            assertEquals(AdbConnectionState.Disconnected, m.state.value)
            assertTrue(m.connect("127.0.0.1", server.port, 10_000).isSuccess)
            assertEquals(
                AdbConnectionState.Connected("127.0.0.1", server.port),
                m.state.value,
            )
        } finally {
            server.close()
        }
    }
}
