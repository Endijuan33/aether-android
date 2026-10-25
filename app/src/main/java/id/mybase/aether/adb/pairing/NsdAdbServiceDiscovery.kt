package id.mybase.aether.adb.pairing

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import id.mybase.aether.adb.localAdbEndpoint
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Event-driven NSD with monitored updates and bounded legacy-resolution fallback. */
class NsdAdbServiceDiscovery(context: Context) : AdbServiceDiscovery {
    private val discovery =
        ResilientNsdDiscovery(
            AndroidNsdDiscoveryPlatform(context.applicationContext),
            AndroidPairingDiagnostics,
            Dispatchers.Main.immediate,
            localEndpoint = { service, info ->
                withContext(Dispatchers.IO) {
                    if (info.port !in 1..65535) null
                    else
                        info.addresses.firstNotNullOfOrNull { host ->
                            try {
                                val local = localAdbEndpoint(host, info.port)
                                DiscoveredAdbEndpoint(
                                    service.name,
                                    local.address.hostAddress!!,
                                    info.port,
                                )
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        }
                }
            },
        )

    override fun watch(type: AdbServiceType): Flow<DiscoveredAdbEndpoint?> = discovery.watch(type)
}

/** Android callbacks carry no code or key. Cancelled legacy OS requests are serialized. */
private class AndroidNsdDiscoveryPlatform(context: Context) : NsdDiscoveryPlatform {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val executor = context.mainExecutor
    private val resolutionMutex = Mutex()
    private var outstanding: CompletableDeferred<NsdEndpointUpdate>? = null
    override val supportsMonitoring
        get() = Build.VERSION.SDK_INT >= 34

    private fun NsdServiceInfo.advertisement() = NsdAdvertisement(serviceName, serviceType, this)

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.endpoint(): NsdEndpointUpdate {
        val addresses =
            if (Build.VERSION.SDK_INT >= 34) hostAddresses.ifEmpty { listOfNotNull(host) }
            else listOfNotNull(host)
        return NsdEndpointUpdate(addresses.mapNotNull { it.hostAddress }, port)
    }

    private fun cleanup(action: () -> Unit): Closeable {
        val closed = AtomicBoolean(false)
        return Closeable {
            if (closed.compareAndSet(false, true)) {
                try {
                    action()
                } catch (_: RuntimeException) {
                    AndroidPairingDiagnostics.record(
                        PairingDiagnostic(
                            PairingDiagnosticEvent.NSD_FAILED,
                            failure = DiscoveryFailureReason.STOP,
                        ),
                    )
                }
            }
        }
    }

    override fun discover(
        type: AdbServiceType,
        listener: NsdDiscoveryPlatform.DiscoveryListener,
    ): Closeable {
        val callback =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = listener.started()

                override fun onDiscoveryStopped(serviceType: String) = listener.stopped()

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) =
                    listener.failed(errorCode)

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) =
                    listener.failed(errorCode)

                override fun onServiceFound(serviceInfo: NsdServiceInfo) =
                    listener.found(serviceInfo.advertisement())

                override fun onServiceLost(serviceInfo: NsdServiceInfo) =
                    listener.lost(serviceInfo.advertisement())
            }
        // NSD is a system-service discovery API; a raw app multicast socket/lock is not required.
        nsd.discoverServices(type.dnsName, NsdManager.PROTOCOL_DNS_SD, callback)
        return cleanup { nsd.stopServiceDiscovery(callback) }
    }

    override fun monitor(
        service: NsdAdvertisement,
        listener: NsdDiscoveryPlatform.UpdateListener,
    ): Closeable {
        if (Build.VERSION.SDK_INT < 34)
            throw UnsupportedOperationException("NSD monitoring needs API 34")
        val callback =
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) =
                    listener.failed(errorCode)

                override fun onServiceInfoCallbackUnregistered() = Unit

                override fun onServiceLost() = listener.lost()

                override fun onServiceLost(info: NsdServiceInfo) = listener.lost()

                override fun onServiceUpdated(info: NsdServiceInfo) =
                    listener.updated(info.endpoint())
            }
        nsd.registerServiceInfoCallback(service.handle as NsdServiceInfo, executor, callback)
        return cleanup { nsd.unregisterServiceInfoCallback(callback) }
    }

    @Suppress("DEPRECATION")
    override suspend fun resolve(service: NsdAdvertisement): NsdEndpointUpdate =
        resolutionMutex.withLock {
            // API 30–33 cannot cancel resolution. Waiting for an older callback is itself bounded
            // by
            // the coordinator's timeout; it cannot leave a later session searching indefinitely.
            outstanding?.join()
            val result = CompletableDeferred<NsdEndpointUpdate>()
            outstanding = result
            val callback =
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        result.completeExceptionally(NsdPlatformException(errorCode))
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        result.complete(serviceInfo.endpoint())
                    }

                    override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {
                        result.completeExceptionally(NsdPlatformException())
                    }

                    override fun onStopResolutionFailed(
                        serviceInfo: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        if (errorCode == NsdManager.FAILURE_OPERATION_NOT_RUNNING)
                            result.completeExceptionally(NsdPlatformException(errorCode))
                    }
                }
            try {
                try {
                    nsd.resolveService(service.handle as NsdServiceInfo, callback)
                } catch (e: Exception) {
                    // Synchronous rejection must not wedge the cross-session serialization gate.
                    result.completeExceptionally(e)
                    throw e
                }
                result.await()
            } finally {
                if (!result.isCompleted && Build.VERSION.SDK_INT >= 34) {
                    cleanup { nsd.stopServiceResolution(callback) }.close()
                }
            }
        }

    override fun watchWifi(onLost: () -> Unit): Closeable {
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) = onLost()
            }
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            callback,
        )
        return cleanup { connectivity.unregisterNetworkCallback(callback) }
    }
}
