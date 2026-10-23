package id.mybase.aether.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorsTest {

    // -- pairing digits -------------------------------------------------------

    @Test
    fun `filterPairingDigits keeps only digits and caps at 6`() {
        assertEquals("123456", InputValidators.filterPairingDigits("12a34b5678"))
        assertEquals("123456", InputValidators.filterPairingDigits("1234567"))
        assertEquals("", InputValidators.filterPairingDigits("abcdef"))
        assertEquals("", InputValidators.filterPairingDigits(""))
        assertEquals("007", InputValidators.filterPairingDigits(" 0 0 7 "))
    }

    @Test
    fun `isValidPairingCode accepts exactly six digits`() {
        assertTrue(InputValidators.isValidPairingCode("123456"))
        assertTrue(InputValidators.isValidPairingCode("000000"))
        assertFalse(InputValidators.isValidPairingCode("12345"))
        assertFalse(InputValidators.isValidPairingCode("1234567"))
        assertFalse(InputValidators.isValidPairingCode("12a456"))
        assertFalse(InputValidators.isValidPairingCode(""))
        assertFalse(InputValidators.isValidPairingCode(" 12345"))
    }

    // -- hosts ----------------------------------------------------------------

    @Test
    fun `isValidHost accepts IPv4`() {
        assertTrue(InputValidators.isValidHost("192.168.1.1"))
        assertTrue(InputValidators.isValidHost("8.8.8.8"))
        assertTrue(InputValidators.isValidHost("255.255.255.255"))
        assertTrue(InputValidators.isValidHost(" 10.0.0.2 "))
    }

    @Test
    fun `dotted numerics that are not valid IPv4 still pass as hostnames`() {
        // The validator is syntactic: a dot-separated numeric string is also a
        // legal hostname (numeric labels are allowed by the hostname grammar),
        // so these are accepted here and fail later at DNS/connect time.
        assertTrue(InputValidators.isValidHost("256.1.1.1"))
        assertTrue(InputValidators.isValidHost("1.2.3"))
        assertTrue(InputValidators.isValidHost("999.999.999.999"))
        assertTrue(InputValidators.isValidHost("1.2.3.4.5"))
    }

    @Test
    fun `isValidHost accepts hostnames`() {
        assertTrue(InputValidators.isValidHost("example.com"))
        assertTrue(InputValidators.isValidHost("my-device.local"))
        assertTrue(InputValidators.isValidHost("a"))
        assertTrue(InputValidators.isValidHost("xn--nxasmq6b.example"))
    }

    @Test
    fun `isValidHost rejects bad hostnames and injection`() {
        assertFalse(InputValidators.isValidHost(""))
        assertFalse(InputValidators.isValidHost("   "))
        assertFalse(InputValidators.isValidHost("-bad.com"))
        assertFalse(InputValidators.isValidHost("bad-.com"))
        assertFalse(InputValidators.isValidHost("exa mple.com"))
        assertFalse(InputValidators.isValidHost("com.evil;rm -rf"))
        assertFalse(InputValidators.isValidHost("host|reboot"))
        assertFalse(InputValidators.isValidHost("a".repeat(64) + ".com"))
        assertFalse(InputValidators.isValidHost("x".repeat(254)))
    }

    // -- ports -----------------------------------------------------------------

    @Test
    fun `isValidPort enforces 1 to 65535`() {
        assertTrue(InputValidators.isValidPort(1))
        assertTrue(InputValidators.isValidPort(5555))
        assertTrue(InputValidators.isValidPort(65535))
        assertFalse(InputValidators.isValidPort(0))
        assertFalse(InputValidators.isValidPort(-1))
        assertFalse(InputValidators.isValidPort(65536))
        assertFalse(InputValidators.isValidPort(Int.MAX_VALUE))
    }

    @Test
    fun `parsePort trims and rejects out-of-range or non-numeric`() {
        assertEquals(8080, InputValidators.parsePort(" 8080 "))
        assertEquals(1, InputValidators.parsePort("1"))
        assertNull(InputValidators.parsePort("0"))
        assertNull(InputValidators.parsePort("65536"))
        assertNull(InputValidators.parsePort("abc"))
        assertNull(InputValidators.parsePort(""))
        assertNull(InputValidators.parsePort("12.5"))
        assertNull(InputValidators.parsePort("0x50"))
    }

    // -- package names ----------------------------------------------------------

    @Test
    fun `isValidPackageName accepts well-formed ids`() {
        assertTrue(InputValidators.isValidPackageName("com.example.app"))
        assertTrue(InputValidators.isValidPackageName("com.google.android.gms"))
        assertTrue(InputValidators.isValidPackageName("a.b"))
        assertTrue(InputValidators.isValidPackageName("com.example_app.v2"))
    }

    @Test
    fun `isValidPackageName rejects injection strings`() {
        assertFalse(InputValidators.isValidPackageName("com.evil;rm -rf"))
        assertFalse(InputValidators.isValidPackageName("com.evil\$(id)"))
        assertFalse(InputValidators.isValidPackageName("com.evil|reboot"))
        assertFalse(InputValidators.isValidPackageName("com.evil&&id"))
        assertFalse(InputValidators.isValidPackageName("com.evil\npm uninstall"))
        assertFalse(InputValidators.isValidPackageName("`id`.evil.com"))
    }

    @Test
    fun `isValidPackageName rejects malformed ids`() {
        assertFalse(InputValidators.isValidPackageName(""))
        assertFalse(InputValidators.isValidPackageName("nodots"))
        assertFalse(InputValidators.isValidPackageName("1com.app"))
        // NOTE: the spec'd regex uses `[A-Za-z0-9_]*` (star) for trailing
        // segments, so empty segments ("com..app", "com.app.") match. That is
        // kept verbatim from the shared contract: the validator is a shell-
        // injection filter (no metacharacters possible), not a strict
        // Android-ID grammar check; `pm` itself rejects unknown packages.
        assertFalse(InputValidators.isValidPackageName(".com.app"))
        assertFalse(InputValidators.isValidPackageName("com.ex ample.app"))
        assertFalse(InputValidators.isValidPackageName("com.app-1.x"))
        assertFalse(InputValidators.isValidPackageName("a".repeat(254) + ".b"))
    }
}
