package id.mybase.aether.adb

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/** Inventory and mutations refer to this Android device; a remote shell cannot be substituted. */
internal fun localAdbEndpoint(host: String, port: Int): InetSocketAddress {
    val address = InetAddress.getByName(host)
    require(address.isLoopbackAddress || NetworkInterface.getByInetAddress(address) != null) {
        "Use this device's Wireless debugging address. Remote-device operations are unsupported."
    }
    return InetSocketAddress(address, port)
}
