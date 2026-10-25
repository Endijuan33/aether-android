package id.mybase.aether.adb.pairing

import java.io.Closeable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResilientNsdDiscoveryTest {
    private val advertisement = NsdAdvertisement("local", "_adb-tls-pairing._tcp.", Any())
    private val update = NsdEndpointUpdate(listOf("127.0.0.1"), 37001)

    private class Platform : NsdDiscoveryPlatform {
        override var supportsMonitoring = true
        lateinit var listener: NsdDiscoveryPlatform.DiscoveryListener
        val monitors = mutableListOf<NsdDiscoveryPlatform.UpdateListener>()
        var openDiscovery = 0
        var openMonitors = 0
        var openWifi = 0
        var resolves = 0
        var resolveFailure: Exception? = null
        var resolveHold: CompletableDeferred<Unit>? = null
        var monitorFailure: Exception? = null
        var discoveryFailure: Exception? = null
        var result = NsdEndpointUpdate(listOf("127.0.0.1"), 37001)
        lateinit var wifiLost: () -> Unit

        override fun discover(
            type: AdbServiceType,
            listener: NsdDiscoveryPlatform.DiscoveryListener,
        ): Closeable {
            discoveryFailure?.let { throw it }
            this.listener = listener
            openDiscovery++
            listener.started()
            return Closeable {
                openDiscovery--
                listener.stopped()
            }
        }

        override fun monitor(
            service: NsdAdvertisement,
            listener: NsdDiscoveryPlatform.UpdateListener,
        ): Closeable {
            monitorFailure?.let { throw it }
            monitors += listener
            openMonitors++
            return Closeable { openMonitors-- }
        }

        override suspend fun resolve(service: NsdAdvertisement): NsdEndpointUpdate {
            resolves++
            resolveHold?.await()
            resolveFailure?.let { throw it }
            return result
        }

        override fun watchWifi(onLost: () -> Unit): Closeable {
            wifiLost = onLost
            openWifi++
            return Closeable { openWifi-- }
        }
    }

    private class Fixture(scope: TestScope) {
        val platform = Platform()
        val events = mutableListOf<PairingDiagnostic>()
        val endpoints = mutableListOf<DiscoveredAdbEndpoint?>()
        var failure: Throwable? = null
        var validationHold: CompletableDeferred<Unit>? = null
        var validationCalls = 0
        val discovery =
            ResilientNsdDiscovery(
                platform,
                PairingDiagnostics { events += it },
                StandardTestDispatcher(scope.testScheduler),
                localEndpoint = { service, info ->
                    validationCalls++
                    validationHold?.await()
                    if (info.port !in 1..65535 || "127.0.0.1" !in info.addresses) null
                    else DiscoveredAdbEndpoint(service.name, "127.0.0.1", info.port)
                },
            )
        val job =
            scope.backgroundScope.launch {
                try {
                    discovery.watch(AdbServiceType.PAIRING).collect { endpoints += it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = e
                }
            }

        fun assertClosed() {
            assertEquals(0, platform.openDiscovery)
            assertEquals(0, platform.openMonitors)
            assertEquals(0, platform.openWifi)
        }
    }

    @Test
    fun delayedServiceDiscoveryResolvesAndCancellationClosesAllRegistrations() = runTest {
        val f = Fixture(this)
        runCurrent()
        assertEquals(listOf<DiscoveredAdbEndpoint?>(null), f.endpoints)
        advanceTimeBy(2_000)
        f.platform.listener.found(advertisement)
        runCurrent()
        f.platform.monitors.single().updated(update)
        runCurrent()
        assertEquals(37001, f.endpoints.last()!!.port)
        assertEquals(0, f.platform.resolves)
        f.job.cancelAndJoin()
        f.assertClosed()
    }

    @Test
    fun callbackRegistrationFailureUsesLegacyResolutionInsteadOfSearchingForever() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        f.platform.monitors.single().failed(3)
        runCurrent()
        assertEquals(1, f.platform.resolves)
        assertNotNull(f.endpoints.last())
        assertEquals(0, f.platform.openMonitors)
        assertTrue(f.events.any { it.event == PairingDiagnosticEvent.NSD_RESOLUTION_FALLBACK })
        f.job.cancelAndJoin()
        f.assertClosed()
    }

    @Test
    fun callbackThatNeverArrivesFallsBackAfterFiveSeconds() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(1, f.platform.resolves)
        assertNotNull(f.endpoints.last())
        f.job.cancelAndJoin()
        f.assertClosed()
    }

    @Test
    fun synchronousMonitorRejectionFallsBack() = runTest {
        val f = Fixture(this)
        f.platform.monitorFailure = IllegalArgumentException("untrusted fixture")
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        assertEquals(1, f.platform.resolves)
        assertNotNull(f.endpoints.last())
    }

    @Test
    fun legacyResolutionFailureRetriesAndSurfacesErrorWithCleanup() = runTest {
        val f = Fixture(this)
        f.platform.supportsMonitoring = false
        f.platform.resolveFailure = NsdPlatformException(3)
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        assertNull(f.failure)
        advanceTimeBy(251)
        runCurrent()
        assertEquals(2, f.platform.resolves)
        val failure = f.failure as AdbDiscoveryException
        assertEquals(DiscoveryFailureReason.RESOLUTION, failure.reason)
        assertEquals(3, failure.platformError)
        assertTrue(failure.recoveryMessage().contains("could not resolve"))
        f.assertClosed()
    }

    @Test
    fun legacyResolutionFailureCanRecoverOnRetry() = runTest {
        val f = Fixture(this)
        f.platform.supportsMonitoring = false
        f.platform.resolveFailure = NsdPlatformException(3)
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        f.platform.resolveFailure = null
        advanceTimeBy(251)
        runCurrent()
        assertEquals(2, f.platform.resolves)
        assertNotNull(f.endpoints.last())
        assertNull(f.failure)
    }

    @Test
    fun hungLegacyResolutionIsBoundedAndClosesSession() = runTest {
        val f = Fixture(this)
        f.platform.supportsMonitoring = false
        f.platform.resolveHold = CompletableDeferred()
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        advanceTimeBy(10_251)
        runCurrent()
        assertEquals(2, f.platform.resolves)
        assertEquals(DiscoveryFailureReason.RESOLUTION, (f.failure as AdbDiscoveryException).reason)
        f.assertClosed()
    }

    @Test
    fun lossDuringResolutionCannotPublishObsoleteEndpoint() = runTest {
        val f = Fixture(this)
        f.platform.supportsMonitoring = false
        f.platform.resolveHold = CompletableDeferred()
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        f.platform.listener.lost(advertisement)
        runCurrent()
        f.platform.resolveHold!!.complete(Unit)
        runCurrent()
        assertTrue(f.endpoints.all { it == null })
        assertNull(f.failure)
    }

    @Test
    fun serviceRestartRejectsOldCallbacksAndResolvesFreshPort() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        val old = f.platform.monitors.single()
        f.platform.listener.lost(advertisement)
        f.platform.listener.found(advertisement.copy(handle = Any()))
        runCurrent()
        old.updated(update)
        f.platform.monitors.last().updated(update.copy(port = 37002))
        runCurrent()
        assertEquals(listOf(null, DiscoveredAdbEndpoint("local", "127.0.0.1", 37002)), f.endpoints)
    }

    @Test
    fun noUsableAddressFromMonitoringFallsBackToResolution() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        f.platform.monitors.single().updated(update.copy(addresses = emptyList()))
        runCurrent()
        assertEquals(1, f.platform.resolves)
        assertNotNull(f.endpoints.last())
    }

    @Test
    fun notificationPermissionDoesNotMaskLocalNetworkDenial() = runTest {
        val f = Fixture(this)
        f.platform.discoveryFailure = SecurityException("secret fixture")
        runCurrent()
        val failure = f.failure as AdbDiscoveryException
        assertEquals(DiscoveryFailureReason.PERMISSION, failure.reason)
        assertTrue(failure.recoveryMessage().contains("notification permission is separate"))
        assertFalse(f.events.toString().contains("secret fixture"))
        f.assertClosed()
    }

    @Test
    fun discoveryFailureAndWifiLossAreDistinctAndReleaseResources() = runTest {
        for (wifi in listOf(false, true)) {
            val f = Fixture(this)
            runCurrent()
            if (wifi) f.platform.wifiLost() else f.platform.listener.failed(3)
            runCurrent()
            assertEquals(
                if (wifi) DiscoveryFailureReason.WIFI_LOST else DiscoveryFailureReason.START,
                (f.failure as AdbDiscoveryException).reason,
            )
            f.assertClosed()
        }
    }

    @Test
    fun incorrectServiceTypesAndExcessAdvertisementsAreBounded() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.platform.listener.found(advertisement.copy(type = "_adb-tls-connect._tcp."))
        repeat(40) { f.platform.listener.found(advertisement.copy(name = "fixture-$it")) }
        runCurrent()
        assertEquals(32, f.platform.monitors.size)
        f.job.cancelAndJoin()
        f.assertClosed()
    }

    @Test
    fun outOfOrderAddressValidationCannotResurrectOldPortOrTriggerFallback() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        val oldValidation = CompletableDeferred<Unit>()
        f.validationHold = oldValidation
        f.platform.monitors.single().updated(update)
        runCurrent()
        f.validationHold = null
        f.platform.monitors.single().updated(update.copy(port = 37002))
        runCurrent()
        oldValidation.complete(Unit)
        runCurrent()
        assertEquals(37002, f.endpoints.last()!!.port)
        assertEquals(0, f.platform.resolves)
        assertFalse(f.endpoints.any { it?.port == 37001 })
    }

    @Test
    fun obsoleteMonitorCallbacksCannotReplaceLegacyFallbackEndpoint() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.platform.listener.found(advertisement)
        runCurrent()
        val monitor = f.platform.monitors.single()
        monitor.failed(3)
        runCurrent()
        monitor.updated(update.copy(port = 37002))
        monitor.failed(3)
        runCurrent()
        assertEquals(1, f.platform.resolves)
        assertEquals(37001, f.endpoints.last()!!.port)
    }
}
