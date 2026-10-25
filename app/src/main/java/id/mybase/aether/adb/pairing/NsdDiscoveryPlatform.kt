package id.mybase.aether.adb.pairing

import java.io.Closeable

/** Small platform boundary. Fakes exercise the same production discovery/retry coordinator. */
internal data class NsdAdvertisement(val name: String, val type: String, val handle: Any)

internal data class NsdEndpointUpdate(val addresses: List<String>, val port: Int)

internal class NsdPlatformException(val errorCode: Int? = null) : Exception("NSD platform failure")

internal interface NsdDiscoveryPlatform {
    val supportsMonitoring: Boolean

    interface DiscoveryListener {
        fun started()

        fun found(service: NsdAdvertisement)

        fun lost(service: NsdAdvertisement)

        fun failed(errorCode: Int)

        fun stopped()
    }

    interface UpdateListener {
        fun updated(endpoint: NsdEndpointUpdate)

        fun lost()

        fun failed(errorCode: Int)
    }

    fun discover(type: AdbServiceType, listener: DiscoveryListener): Closeable

    fun monitor(service: NsdAdvertisement, listener: UpdateListener): Closeable

    suspend fun resolve(service: NsdAdvertisement): NsdEndpointUpdate

    fun watchWifi(onLost: () -> Unit): Closeable
}
