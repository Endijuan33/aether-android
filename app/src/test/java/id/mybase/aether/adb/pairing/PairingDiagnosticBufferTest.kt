package id.mybase.aether.adb.pairing

import org.junit.Assert.*
import org.junit.Test

class PairingDiagnosticBufferTest {
    @Test
    fun traceIsBoundedChronologicalAndSnapshotsAreIndependent() {
        val buffer = PairingDiagnosticBuffer(2)
        val first = PairingDiagnostic(PairingDiagnosticEvent.NSD_REQUESTED)
        val second = PairingDiagnostic(PairingDiagnosticEvent.NSD_STARTED)
        val third = PairingDiagnostic(PairingDiagnosticEvent.NSD_RESOLVED)
        buffer.record(first)
        val snapshot = buffer.snapshot()
        buffer.record(second)
        buffer.record(third)
        assertEquals(listOf(first), snapshot)
        assertEquals(listOf(second, third), buffer.snapshot())
        assertTrue(PairingDiagnosticBuffer().snapshot().isEmpty())
    }

    @Test
    fun diagnosticCapacityCannotGrowWithoutBound() {
        for (capacity in listOf(0, -1, 257)) assertThrows(IllegalArgumentException::class.java) {
            PairingDiagnosticBuffer(capacity)
        }
    }

    @Test
    fun exceptionDiagnosticsContainOnlyClassMetadataAndNeverMessages() {
        val buffer = PairingDiagnosticBuffer()
        val cause = java.security.InvalidKeyException("123456 private fixture")
        val error = javax.net.ssl.SSLException("private key PEM fixture", cause)
        buffer.protocolFailure(PairingProtocolStage.TLS_HANDSHAKE, error)
        val event = buffer.snapshot().single()
        assertEquals(
            listOf("javax.net.ssl.SSLException", "java.security.InvalidKeyException"),
            event.exceptionTypes,
        )
        assertEquals(PairingProtocolStage.TLS_HANDSHAKE, event.protocolStage)
        assertFalse(event.toString().contains("123456"))
        assertFalse(event.toString().contains("private fixture"))
        assertFalse(event.toString().contains("PEM"))
    }
}
