package id.mybase.aether.adb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Proves the pairing framing is self-consistent and rejects malformed input: wrong version, unknown
 * type, oversized length, and truncation.
 */
class PairingProtocolTest {

    private fun roundTrip(type: Byte, payload: ByteArray): PairingProtocol.Frame {
        val out = ByteArrayOutputStream()
        PairingProtocol.writeFrame(out, type, payload)
        return PairingProtocol.readFrame(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `spake2 frame round-trips`() {
        val payload = ByteArray(64) { it.toByte() }
        val frame = roundTrip(PairingProtocol.TYPE_SPAKE2, payload)
        assertEquals(PairingProtocol.TYPE_SPAKE2, frame.type)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun `peer-info frame round-trips`() {
        val payload = ByteArray(512) { (it * 7).toByte() }
        val frame = roundTrip(PairingProtocol.TYPE_PEER_INFO, payload)
        assertEquals(PairingProtocol.TYPE_PEER_INFO, frame.type)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun `empty payload round-trips`() {
        val frame = roundTrip(PairingProtocol.TYPE_SPAKE2, ByteArray(0))
        assertArrayEquals(ByteArray(0), frame.payload)
    }

    private fun rawFrame(version: Byte, type: Byte, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(version.toInt())
        out.write(type.toInt())
        val len = payload.size
        out.write(
            byteArrayOf(
                (len shr 24).toByte(),
                (len shr 16).toByte(),
                (len shr 8).toByte(),
                len.toByte(),
            ),
        )
        out.write(payload)
        return out.toByteArray()
    }

    @Test
    fun `wrong version is rejected`() {
        val raw = rawFrame(2, PairingProtocol.TYPE_SPAKE2, byteArrayOf(9))
        assertThrows(PairingProtocolException::class.java) {
            PairingProtocol.readFrame(ByteArrayInputStream(raw))
        }
    }

    @Test
    fun `unknown type is rejected`() {
        val raw = rawFrame(1, 7, byteArrayOf(9))
        assertThrows(PairingProtocolException::class.java) {
            PairingProtocol.readFrame(ByteArrayInputStream(raw))
        }
    }

    @Test
    fun `oversized length is rejected before allocating`() {
        val out = ByteArrayOutputStream()
        out.write(1)
        out.write(PairingProtocol.TYPE_PEER_INFO.toInt())
        out.write(byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F)) // far above the 64 KiB cap
        assertThrows(PairingProtocolException::class.java) {
            PairingProtocol.readFrame(ByteArrayInputStream(out.toByteArray()))
        }
    }

    @Test
    fun `truncated payload raises EOF`() {
        val full = rawFrame(1, PairingProtocol.TYPE_SPAKE2, ByteArray(16) { it.toByte() })
        val truncated = full.copyOf(full.size - 5) // header promises 16, stream ends early
        assertThrows(EOFException::class.java) {
            PairingProtocol.readFrame(ByteArrayInputStream(truncated))
        }
    }

    @Test
    fun `writeFrame rejects unknown type`() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingProtocol.writeFrame(ByteArrayOutputStream(), 9, ByteArray(1))
        }
    }
}
