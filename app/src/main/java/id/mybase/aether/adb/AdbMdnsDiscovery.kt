package id.mybase.aether.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Discovers ADB services via mDNS (DNS-SD), following Shizuku's AdbMdns implementation.
 *
 * Service types (verified in Shizuku and LADB source):
 * - `_adb-tls-pairing._tcp` : the pairing endpoint (user enters port + code from device dialog)
 * - `_adb-tls-connect._tcp` : the ADB connection endpoint (discovered automatically)
 *
 * Shizuku pairs with 127.0.0.1 because the pairing service runs on the local adbd. AETHER follows
 * the same model: after the user enters the pairing port + code, we discover the ADB connect port
 * via mDNS instead of asking the user to type it manually.
 */
object AdbMdnsDiscovery {

    private const val TAG = "AdbMdnsDiscovery"

    const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"
    const val SERVICE_CONNECT = "_adb-tls-connect._tcp"

    /**
     * Result of discovering an ADB service.
     *
     * @param host the IP address (typically 127.0.0.1 for local adbd)
     * @param port the service port
     * @param serviceName the mDNS service name (e.g. "POCO F6")
     */
    data class DiscoveredService(
        val host: String,
        val port: Int,
        val serviceName: String,
    )

    /**
     * Discovers services of [serviceType] and returns the first one whose host is a local interface
     * address (i.e. the service runs on this device, like Shizuku does).
     *
     * @param timeoutMs how long to wait for discovery
     * @return the discovered service, or null if none found within the timeout
     */
    suspend fun discoverLocalService(
        context: Context,
        serviceType: String,
        timeoutMs: Long = 10_000,
    ): DiscoveredService? = suspendCancellableCoroutine { cont ->
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        var resolved = false

        val resolveListener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (resolved || !cont.isActive) return
                    val host: InetAddress? = serviceInfo.host
                    val port = serviceInfo.port
                    if (host == null || port <= 0) return

                    // Accept only if the host is one of our own interface addresses
                    // (the ADB service runs on the local device).
                    if (isLocalAddress(host)) {
                        resolved = true
                        Log.i(
                            TAG,
                            "Discovered $serviceType: ${serviceInfo.serviceName} at ${host.hostAddress}:$port",
                        )
                        cont.resume(
                            DiscoveredService(
                                host = host.hostAddress ?: "127.0.0.1",
                                port = port,
                                serviceName = serviceInfo.serviceName ?: "",
                            ),
                        )
                    } else {
                        Log.d(
                            TAG,
                            "Ignoring non-local service ${serviceInfo.serviceName} at ${host.hostAddress}",
                        )
                    }
                }
            }

        val discoveryListener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "Discovery started for $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (resolved || !cont.isActive) return
                    try {
                        nsdManager.resolveService(service, resolveListener)
                    } catch (e: Exception) {
                        Log.w(TAG, "Resolve failed", e)
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "Discovery start failed for $serviceType: $errorCode")
                    if (!resolved && cont.isActive) {
                        cont.resume(null)
                    }
                }

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit
            }

        cont.invokeOnCancellation {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                // Ignore
            }
        }

        try {
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
        } catch (e: Exception) {
            Log.e(TAG, "discoverServices threw", e)
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        // Timeout
        android.os
            .Handler(android.os.Looper.getMainLooper())
            .postDelayed(
                {
                    if (!resolved && cont.isActive) {
                        Log.w(TAG, "Discovery timeout for $serviceType")
                        try {
                            nsdManager.stopServiceDiscovery(discoveryListener)
                        } catch (e: Exception) {}
                        cont.resume(null)
                    }
                },
                timeoutMs,
            )
    }

    /** Checks if [address] is one of this device's own interface addresses. */
    private fun isLocalAddress(address: InetAddress): Boolean {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    if (addresses.nextElement() == address) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enumerate interfaces", e)
        }
        // Also accept loopback explicitly
        return address.isLoopbackAddress
    }
}
