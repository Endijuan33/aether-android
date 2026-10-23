package id.mybase.aether.ui.connection

import app.cash.turbine.test
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.adb.PairingResult
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.ui.fakes.FakeAdbShell
import id.mybase.aether.ui.fakes.FakeContext
import id.mybase.aether.ui.fakes.FakePairingClient
import id.mybase.aether.ui.fakes.MainDispatcherRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [ConnectionViewModel] behavior: pairing validation, result mapping, preference persistence and
 * state-flow wiring. Uses hand fakes and Turbine.
 */
class ConnectionViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var shell: FakeAdbShell
    private lateinit var client: FakePairingClient
    private lateinit var prefs: AetherPreferences
    private lateinit var vm: ConnectionViewModel

    @Before
    fun setUp() {
        shell = FakeAdbShell()
        client = FakePairingClient()
        prefs = AetherPreferences(FakeContext())
        vm = ConnectionViewModel(shell, client, prefs)
    }

    @Test
    fun `pair success persists host and port and clears error`() = runBlocking {
        client.result = PairingResult.Success

        vm.pair("192.168.1.5", 37001, "123456")

        assertEquals(1, client.calls)
        assertEquals(Triple("192.168.1.5", 37001, "123456"), client.lastArgs)
        withTimeout(10_000) {
            prefs.lastHost.first { it == "192.168.1.5" }
            prefs.lastPairingPort.first { it == 37001 }
        }
        assertNull(vm.pairingError.value)
    }

    @Test
    fun `pair filters non-digit characters from pasted codes`() {
        client.result = PairingResult.Success

        vm.pair("192.168.1.5", 37001, "12-34 56")

        assertEquals(1, client.calls)
        assertEquals("123456", client.lastArgs?.third)
    }

    @Test
    fun `invalid pairing code surfaces error`() = runBlocking {
        client.result = PairingResult.InvalidCode

        vm.pairingError.test(timeout = 5.seconds) {
            assertNull(awaitItem())
            vm.pair("192.168.1.5", 37001, "000000")
            assertEquals(
                "Wrong pairing code. Check the code shown on your device and try again.",
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, client.calls)
    }

    @Test
    fun `pairing timeout surfaces error`() = runBlocking {
        client.result = PairingResult.Timeout

        vm.pairingError.test(timeout = 5.seconds) {
            assertNull(awaitItem())
            vm.pair("192.168.1.5", 37001, "123456")
            assertTrue(awaitItem()!!.contains("timed out"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network error surfaces message`() = runBlocking {
        client.result = PairingResult.NetworkError("unreachable")

        vm.pairingError.test(timeout = 5.seconds) {
            assertNull(awaitItem())
            vm.pair("192.168.1.5", 37001, "123456")
            assertEquals("Network error: unreachable", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pair rejects invalid host without calling client`() {
        vm.pair("not a host!!", 37001, "123456")

        assertEquals(0, client.calls)
        assertEquals(
            "Invalid host address. Use the IP shown under Wireless debugging.",
            vm.pairingError.value,
        )
    }

    @Test
    fun `pair rejects short code without calling client`() {
        vm.pair("192.168.1.5", 37001, "123")

        assertEquals(0, client.calls)
        assertNotNull(vm.pairingError.value)
    }

    @Test
    fun `state flow mirrors shell state`() = runBlocking {
        vm.state.test(timeout = 5.seconds) {
            assertTrue(awaitItem() is AdbConnectionState.Disconnected)
            shell.emit(AdbConnectionState.Paired)
            assertTrue(awaitItem() is AdbConnectionState.Paired)
            shell.emit(AdbConnectionState.Connected("192.168.1.5", 5555))
            val connected = awaitItem()
            assertTrue(connected is AdbConnectionState.Connected)
            assertEquals("192.168.1.5", (connected as AdbConnectionState.Connected).host)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connect rejects invalid host without calling shell`() {
        vm.connect("bad host!!", 5555)

        assertEquals(0, shell.connectCalls)
        assertNotNull(vm.connectError.value)
    }

    @Test
    fun `connect success persists connection port`() = runTest {
        // Override Dispatchers.Main with the runTest dispatcher so viewModelScope
        // uses it. The MainDispatcherRule sets UnconfinedTestDispatcher, which
        // conflicts with runTest's scheduler — so reset it first.
        Dispatchers.resetMain()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val testShell = FakeAdbShell()
        val testClient = FakePairingClient()
        val testPrefs = AetherPreferences(FakeContext())
        val testVm = ConnectionViewModel(testShell, testClient, testPrefs)

        testVm.connect("192.168.1.5", 5555)
        // Let the viewModelScope coroutine run. DataStore does real file IO on
        // Dispatchers.IO (not the test scheduler), so poll with real time.
        testScheduler.advanceUntilIdle()
        val deadline = System.currentTimeMillis() + 10_000
        var port: Int? = null
        while (System.currentTimeMillis() < deadline) {
            port = testPrefs.lastConnectionPort.first()
            if (port == 5555) break
            // Real thread sleep (not coroutine delay): DataStore IO is on real Dispatchers.IO.
            Thread.sleep(100)
            // Re-run pending test-scheduler tasks after the IO had a chance to complete.
            testScheduler.advanceUntilIdle()
        }

        assertEquals(1, testShell.connectCalls)
        assertEquals("192.168.1.5" to 5555, testShell.lastConnectArgs)
        assertEquals(5555, port)
        assertNull(testVm.connectError.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `connect failure surfaces error`() {
        shell.connectResult = Result.failure(RuntimeException("refused"))

        vm.connect("192.168.1.5", 5555)

        assertEquals(1, shell.connectCalls)
        assertEquals("refused", vm.connectError.value)
    }

    @Test
    fun `disconnect delegates to shell`() {
        vm.disconnect()

        assertEquals(1, shell.disconnectCalls)
    }

    @Test
    fun `redacted diagnostics never contains the pairing code`() {
        val code = "987654"
        client.result = PairingResult.Success
        vm.pair("192.168.1.5", 37001, code)

        val diagnostics = vm.redactedDiagnostics()

        assertTrue(!diagnostics.contains(code))
        assertTrue(diagnostics.contains("state="))
    }
}
