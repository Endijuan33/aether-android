package id.mybase.aether.adb.pairing

/** Generation guard for late/out-of-order NSD address resolutions. Access is serialized on Main. */
internal class DiscoveryLease {
    private var revision = 0L
    private var active = true

    fun beginUpdate(): Long = ++revision

    fun isCurrent(update: Long): Boolean = active && update == revision

    fun invalidate() {
        active = false
        revision++
    }
}
