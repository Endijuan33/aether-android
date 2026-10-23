package id.mybase.aether.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Best-effort mDNS discovery of wireless-debugging endpoints via [NsdManager].
 *
 * Wireless debugging advertises two documented service types (see docs/adb-pairing-protocol.md for
 * the AOSP source):
 * - [_adb-tls-pairing._tcp][PAIRING_SERVICE_TYPE]: the pairing endpoint, advertised only while the
 *   user has the "Pair with pairing code" dialog open — the advertisement is short-lived;
 * - [_adb-tls-connect._tcp][CONNECT_SERVICE_TYPE]: the ADB connection endpoint, advertised whenever
 *   wireless debugging is enabled.
 *
 * Discovery needs no special permissions and works for on-device (loopback) pairing: the device
 * advertises its own services on the local network. mDNS does not cross subnets/VPNs, and a
 * congested network may simply yield nothing — every method here returns null instead of throwing,
 * so callers fall back to manual host/port entry. Manual entry remains the supported path in 0.1.0;
 * discovery is opportunistic.
 */
class AdbPairingDiscovery(private val appContext: Context) {

    companion object {
        const val PAIRING_SERVICE_TYPE = "_adb-tls-pairing._tcp"
        const val CONNECT_SERVICE_TYPE = "_adb-tls-connect._tcp"
    }

    /** Returns host to pairing port, or null when nothing was found in time. */
    suspend fun discoverPairingEndpoint(timeoutMs: Long = 10_000): Pair<String, Int>? =
        discover(PAIRING_SERVICE_TYPE, timeoutMs)

    /** Returns host to ADB connection port, or null when nothing was found in time. */
    suspend fun discoverConnectEndpoint(timeoutMs: Long = 10_000): Pair<String, Int>? =
        discover(CONNECT_SERVICE_TYPE, timeoutMs)

    private suspend fun discover(serviceType: String, timeoutMs: Long): Pair<String, Int>? {
        val nsd = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    lateinit var discoveryListener: NsdManager.DiscoveryListener

                    fun finish(result: Pair<String, Int>?) {
                        runCatching { nsd.stopServiceDiscovery(discoveryListener) }
                        // resumeWith is a member of Continuation: no extension import needed.
                        if (cont.isActive) cont.resumeWith(Result.success(result))
                    }

                    val resolveListener =
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(
                                serviceInfo: NsdServiceInfo?,
                                errorCode: Int,
                            ) {
                                // Keep listening for other instances.
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val address = serviceInfo.host?.hostAddress
                                if (address != null) finish(address to serviceInfo.port)
                            }
                        }
                    discoveryListener =
                        object : NsdManager.DiscoveryListener {
                            override fun onDiscoveryStarted(serviceType: String?) = Unit

                            override fun onDiscoveryStopped(serviceType: String?) = Unit

                            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                                if (serviceInfo.serviceType.startsWith(serviceType)) {
                                    runCatching { nsd.resolveService(serviceInfo, resolveListener) }
                                }
                            }

                            override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit

                            override fun onStartDiscoveryFailed(
                                serviceType: String?,
                                errorCode: Int,
                            ) {
                                finish(null)
                            }

                            override fun onStopDiscoveryFailed(
                                serviceType: String?,
                                errorCode: Int,
                            ) = Unit
                        }
                    cont.invokeOnCancellation {
                        runCatching { nsd.stopServiceDiscovery(discoveryListener) }
                    }
                    try {
                        nsd.discoverServices(
                            serviceType,
                            NsdManager.PROTOCOL_DNS_SD,
                            discoveryListener,
                        )
                    } catch (e: Exception) {
                        finish(null)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
