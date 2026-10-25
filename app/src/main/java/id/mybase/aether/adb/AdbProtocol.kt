package id.mybase.aether.adb

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hand-rolled classic ADB transport framing (the inner protocol spoken over the TLS channel once
 * wireless debugging is paired).
 *
 * Every message is a 24-byte little-endian header followed by the payload:
 * ```
 * command | arg0 | arg1 | data_length | data_checksum | magic
 *  uint32   uint32  uint32   uint32        uint32       uint32
 * ```
 * - `data_checksum` is the sum of the payload bytes as unsigned values.
 * - `magic` is `command xor 0xFFFFFFFF`.
 *
 * Constants follow AOSP's protocol (see docs/adb-pairing-protocol.md for sources). In TLS mode the
 * AUTH exchange is not performed: the TLS handshake with our client certificate already proves
 * possession of the authorized key, so adbd proceeds straight from CNXN to OPEN.
 */
internal object AdbProtocol {
    const val CMD_CNXN = 0x4E584E43 // "CNXN"
    const val CMD_AUTH = 0x48545541 // "AUTH"
    const val CMD_STLS = 0x534c5453 // "STLS"
    const val STLS_VERSION = 0x01000000
    const val CMD_OPEN = 0x4E45504F // "OPEN"
    const val CMD_WRTE = 0x45545257 // "WRTE"
    const val CMD_OKAY = 0x59414B4F // "OKAY"
    const val CMD_CLSE = 0x45534c43 // "CLSE"

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    /** Legacy checksum-bearing version announced in CNXN; AOSP negotiates the lower version. */
    const val ADB_VERSION = 0x01000000

    /** Maximum payload we advertise and accept. */
    const val MAX_PAYLOAD = 256 * 1024

    /** Identity string announced in the CNXN payload. */
    val CNXN_PAYLOAD = "host::aether\u0000".toByteArray(Charsets.UTF_8)

    val EMPTY = ByteArray(0)

    data class Message(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    fun writeMessage(out: OutputStream, command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        require(payload.size <= MAX_PAYLOAD) { "ADB payload too large: ${payload.size}" }
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(checksum(payload))
        header.putInt(command.inv())
        out.write(header.array())
        out.write(payload)
        out.flush()
    }

    /**
     * Reads one message, blocking. Throws [EOFException] on end of stream and
     * [AdbProtocolException] on framing violations.
     */
    fun readMessage(input: InputStream): Message {
        val header = readFully(input, 24)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val dataLength = buf.int
        val dataChecksum = buf.int
        val magic = buf.int
        if (magic != command.inv()) {
            throw AdbProtocolException("bad magic for command ${command.toString(16)}")
        }
        if (dataLength < 0 || dataLength > MAX_PAYLOAD) {
            throw AdbProtocolException("invalid ADB payload length: $dataLength")
        }
        val payload = readFully(input, dataLength)
        if (checksum(payload) != dataChecksum) {
            throw AdbProtocolException("ADB payload checksum mismatch")
        }
        return Message(command, arg0, arg1, payload)
    }

    private fun checksum(payload: ByteArray): Int {
        var sum = 0
        for (b in payload) sum += b.toInt() and 0xFF
        return sum
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

/** ADB-level failure (framing violation, rejected OPEN, unexpected AUTH, …). */
internal class AdbException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal class AdbProtocolException(message: String) : IOException(message)
