package id.mybase.aether.adb.pairing

import org.junit.Assert.*
import org.junit.Test

/** Tests the generation guard used by the actual Android NSD adapter. */
class DiscoveryLeaseTest {
    @Test
    fun `current resolution is accepted`() {
        val lease = DiscoveryLease()
        assertTrue(lease.isCurrent(lease.beginUpdate()))
    }

    @Test
    fun `older resolution cannot overwrite newer endpoint`() {
        val lease = DiscoveryLease()
        val old = lease.beginUpdate()
        val newer = lease.beginUpdate()
        assertFalse(lease.isCurrent(old))
        assertTrue(lease.isCurrent(newer))
    }

    @Test
    fun `lost service invalidates late completion`() {
        val lease = DiscoveryLease()
        val pending = lease.beginUpdate()
        lease.invalidate()
        assertFalse(lease.isCurrent(pending))
        assertFalse(lease.isCurrent(lease.beginUpdate()))
    }

    @Test
    fun `service loss revision rejects unresolved obsolete address`() {
        val lease = DiscoveryLease()
        val pending = lease.beginUpdate()
        lease.beginUpdate()
        assertFalse(lease.isCurrent(pending))
    }
}
