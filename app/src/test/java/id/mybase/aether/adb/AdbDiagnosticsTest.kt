package id.mybase.aether.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves secrets never survive into diagnostic strings. */
class AdbDiagnosticsTest {

    @Test
    fun `describeRedacted covers every state`() {
        assertEquals("Disconnected", AdbConnectionState.Disconnected.describeRedacted())
        assertEquals(
            "Pairing(host=192.168.1.2, port=37001)",
            AdbConnectionState.Pairing("192.168.1.2", 37001).describeRedacted(),
        )
        assertEquals("Paired", AdbConnectionState.Paired.describeRedacted())
        assertEquals(
            "Connecting(host=192.168.1.2, port=5555)",
            AdbConnectionState.Connecting("192.168.1.2", 5555).describeRedacted(),
        )
        assertEquals(
            "Connected(host=192.168.1.2, port=5555)",
            AdbConnectionState.Connected("192.168.1.2", 5555).describeRedacted(),
        )
        // Ports (<=5 digits) are never redacted.
        assertTrue(
            AdbConnectionState.Connected("h", 5555).describeRedacted().contains("5555"),
        )
    }

    @Test
    fun `describeRedacted strips a pairing code from the failure reason`() {
        val described =
            AdbConnectionState.Failed(
                    "Pairing rejected for code 123456 at 10.0.0.1",
                )
                .describeRedacted()
        assertFalse(described.contains("123456"))
        assertTrue(described.contains("[pairing-code]"))
        assertTrue(described.contains("10.0.0.1"))
    }

    @Test
    fun `redactSecrets strips a PEM block`() {
        val pem =
            "key follows: -----BEGIN PRIVATE KEY-----\nMIIEvQIBADAN\n-----END PRIVATE KEY----- done"
        val redacted = pem.redactSecrets()
        assertFalse(redacted.contains("MIIEvQIBADAN"))
        assertFalse(redacted.contains("BEGIN PRIVATE KEY"))
        assertTrue(redacted.contains("[key-material]"))
        assertTrue(redacted.startsWith("key follows: "))
        assertTrue(redacted.endsWith(" done"))
    }

    @Test
    fun `redactSecrets leaves longer digit runs alone`() {
        assertEquals("1234567", "1234567".redactSecrets())
        assertEquals("12345", "12345".redactSecrets())
    }

    @Test
    fun `redactSecrets leaves ordinary text untouched`() {
        val text = "Connection timed out after 15000ms to 192.168.1.2:5555"
        assertEquals(text, text.redactSecrets())
    }

    @Test
    fun `redactSecrets handles several codes in one string`() {
        val redacted = "codes 123456 and 654321 rejected".redactSecrets()
        assertEquals("codes [pairing-code] and [pairing-code] rejected", redacted)
    }
}
