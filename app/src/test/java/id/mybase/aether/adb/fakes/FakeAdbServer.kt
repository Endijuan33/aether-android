package id.mybase.aether.adb.fakes

import id.mybase.aether.adb.AdbProtocol
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-JVM fake of adbd's ADB-over-TLS endpoint.
 *
 * Performs the TLS handshake (requiring a client certificate, like the real endpoint), answers CNXN
 * with CNXN, then serves OPEN requests: each `shell:<command>` stream gets an OKAY, the scripted
 * WRTE payloads, and a CLSE. Every opened destination is recorded for assertions.
 */
class FakeAdbServer(
    private val openHandler: (String) -> OpenResponse = {
        OpenResponse(listOf("fake-output".toByteArray()))
    },
    private val acceptOnly: Boolean = false,
) {
    data class OpenResponse(
        /** Payloads sent as WRTE before closing. */
        val chunks: List<ByteArray>,
        /** When false, the stream is left open (client must time out/cancel). */
        val sendClose: Boolean = true,
    )

    val keys = FakeAdbKeyMaterial()
    val port: Int
    val openedDestinations: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val serverSocket = ServerSocket(0)
    private val thread: Thread
    private val nextServerId = AtomicInteger(1000)

    init {
        port = serverSocket.localPort
        thread =
            Thread(::acceptLoop, "fake-adb-server").apply {
                isDaemon = true
                start()
            }
    }

    private fun acceptLoop() {
        try {
            // Accept multiple connections (the reconnect test connects, disconnects,
            // then connects again). Each connection is handled on its own thread so
            // a long-lived connection never blocks the next accept.
            while (true) {
                val socket: Socket = serverSocket.accept()
                Thread({ handle(socket) }, "fake-adb-conn").apply {
                    isDaemon = true
                    start()
                }
            }
        } catch (e: Exception) {
            // ServerSocket closed by close().
        }
    }

    private fun handle(socket: Socket) {
        try {
            if (acceptOnly) {
                Thread.sleep(60_000)
                return
            }
            tlsServerWrap(socket, keys, needClientAuth = true).use { ssl ->
                val input = ssl.inputStream
                val out = ssl.outputStream
                val cnxn = AdbProtocol.readMessage(input)
                check(cnxn.command == AdbProtocol.CMD_CNXN) { "expected CNXN" }
                AdbProtocol.writeMessage(
                    out,
                    AdbProtocol.CMD_CNXN,
                    AdbProtocol.ADB_VERSION,
                    AdbProtocol.MAX_PAYLOAD,
                    "device::fake".toByteArray(Charsets.UTF_8),
                )
                val streams = mutableMapOf<Int, Int>() // client local id -> server id
                while (true) {
                    val msg = AdbProtocol.readMessage(input)
                    when (msg.command) {
                        AdbProtocol.CMD_OPEN -> {
                            val destination = msg.payload.toString(Charsets.UTF_8)
                            openedDestinations.add(destination)
                            val serverId = nextServerId.getAndIncrement()
                            streams[msg.arg0] = serverId
                            AdbProtocol.writeMessage(
                                out,
                                AdbProtocol.CMD_OKAY,
                                serverId,
                                msg.arg0,
                                AdbProtocol.EMPTY,
                            )
                            val response = openHandler(destination)
                            // The client acks each WRTE asynchronously; fire and forget.
                            for (chunk in response.chunks) {
                                AdbProtocol.writeMessage(
                                    out,
                                    AdbProtocol.CMD_WRTE,
                                    serverId,
                                    msg.arg0,
                                    chunk,
                                )
                            }
                            if (response.sendClose) {
                                AdbProtocol.writeMessage(
                                    out,
                                    AdbProtocol.CMD_CLSE,
                                    serverId,
                                    msg.arg0,
                                    AdbProtocol.EMPTY,
                                )
                            }
                        }
                        AdbProtocol.CMD_CLSE -> {
                            streams.remove(msg.arg1)
                        }
                        AdbProtocol.CMD_WRTE -> {
                            // Polite ack; the shell flow never sends these.
                            AdbProtocol.writeMessage(
                                out,
                                AdbProtocol.CMD_OKAY,
                                msg.arg1,
                                msg.arg0,
                                AdbProtocol.EMPTY,
                            )
                        }
                        else -> Unit
                    }
                }
            }
        } catch (e: Exception) {
            // Client went away; nothing to do.
        }
    }

    fun close() {
        runCatching { serverSocket.close() }
        thread.join(5_000)
    }
}
