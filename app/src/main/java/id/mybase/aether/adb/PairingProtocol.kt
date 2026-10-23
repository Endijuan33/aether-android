package id.mybase.aether.adb

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Framing of the Android 11+ wireless-debugging pairing protocol, as implemented by AOSP's pairing
 * connection (client and adbd sides).
 *
 * Every pairing message on the wire is:
 * ```
 * +---------+------+---------------+------------------+
 * | version | type | payload length| payload          |
 * | 1 byte  |1 byte | 4 bytes       | variable         |
 * +---------+------+---------------+------------------+
 * ```
 * - `version` is always 1.
 * - `type` is 0 for a SPAKE2 key-exchange message, 1 for a peer-info message.
 * - `payload length` is the byte length of the payload that follows.
 *
 * Endianness of the length field is big-endian per Kadb (verified against AOSP's pairing flow). The
 * peer-info plaintext is the 8192-byte struct (1 type byte + 8191 data bytes) per AOSP
 * pairing_connection.h.
 */
internal object PairingProtocol {
    const val VERSION: Byte = 1
    const val TYPE_SPAKE2: Byte = 0
    const val TYPE_PEER_INFO: Byte = 1

    /** Maximum accepted pairing payload; guards against corrupt length fields. */
    const val MAX_PAYLOAD_BYTES = 64 * 1024

    data class Frame(val type: Byte, val payload: ByteArray)

    fun writeFrame(out: OutputStream, type: Byte, payload: ByteArray) {
        require(type == TYPE_SPAKE2 || type == TYPE_PEER_INFO) {
            "unknown pairing frame type: $type"
        }
        require(payload.size <= MAX_PAYLOAD_BYTES) { "pairing payload too large: ${payload.size}" }
        val header = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        header.put(VERSION)
        header.put(type)
        header.putInt(payload.size)
        out.write(header.array())
        out.write(payload)
        out.flush()
    }

    /**
     * Reads one frame, blocking. Throws [EOFException] on a clean end of stream and
     * [PairingProtocolException] on malformed data.
     */
    fun readFrame(input: InputStream): Frame {
        val header = readFully(input, 6)
        val version = header[0]
        if (version != VERSION) {
            throw PairingProtocolException("unsupported pairing protocol version: $version")
        }
        val type = header[1]
        if (type != TYPE_SPAKE2 && type != TYPE_PEER_INFO) {
            throw PairingProtocolException("unknown pairing frame type: $type")
        }
        val length = ByteBuffer.wrap(header, 2, 4).order(ByteOrder.BIG_ENDIAN).int
        if (length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw PairingProtocolException("invalid pairing payload length: $length")
        }
        return Frame(type, readFully(input, length))
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        // NB: InputStream.readNBytes() is API 33+; minSdk here is 30, so loop manually.
        val buf = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buf, offset, count - offset)
            if (read == -1) throw EOFException("end of stream after $offset of $count bytes")
            offset += read
        }
        return buf
    }
}

/** Malformed pairing-protocol data (not a transport failure). */
internal class PairingProtocolException(message: String) : Exception(message)
