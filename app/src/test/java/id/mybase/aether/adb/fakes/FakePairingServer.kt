package id.mybase.aether.adb.fakes

import id.mybase.aether.adb.HkdfSha256
import id.mybase.aether.adb.PairingCipher
import id.mybase.aether.adb.PairingCrypto
import id.mybase.aether.adb.PairingProtocol
import id.mybase.aether.adb.Spake2Bob
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.AEADBadTagException

/**
 * In-JVM fake of adbd's pairing endpoint.
 *
 * Speaks the same framing the client implements ([PairingProtocol]) in the server (Bob) role: reads
 * the client's SPAKE2 message, answers with its own, derives the peer-info key, then reads the
 * client's encrypted peer info and — when it decrypts — answers with its own encrypted peer info.
 *
 * The SPAKE2 password material is `password || tlsExporterBytes` (channel binding); the test
 * injects the same fixed exporter bytes into the client.
 *
 * A client using a different password derives a different key, so the peer-info decryption fails
 * and the server drops the connection, exactly the signal the client maps to
 * [id.mybase.aether.adb.PairingResult.InvalidCode].
 */
class FakePairingServer(
    private val password: String,
    private val tlsExporterBytes: ByteArray = ByteArray(64) { 0xA5.toByte() },
    private val hangBeforeSpakeResponse: Boolean = false,
) {
    val keys = FakeAdbKeyMaterial()
    val port: Int

    @Volatile
    var peerInfo: ByteArray? = null
        private set

    @Volatile
    var completedOk = false
        private set

    @Volatile
    var sawWrongCode = false
        private set

    private val serverSocket = ServerSocket(0)
    private val thread: Thread

    init {
        port = serverSocket.localPort
        thread =
            Thread(::acceptLoop, "fake-pairing-server").apply {
                isDaemon = true
                start()
            }
    }

    private fun acceptLoop() {
        try {
            val socket: Socket = serverSocket.accept()
            handle(socket)
        } catch (e: Exception) {
            // ServerSocket closed by close().
        }
    }

    private fun handle(socket: Socket) {
        try {
            tlsServerWrap(socket, keys).use { ssl ->
                val input = ssl.inputStream
                val out = ssl.outputStream
                // 1. Client SPAKE2 message.
                val clientSpake = PairingProtocol.readFrame(input)
                check(clientSpake.type == PairingProtocol.TYPE_SPAKE2) { "expected SPAKE2" }
                if (hangBeforeSpakeResponse) {
                    Thread.sleep(60_000)
                    return
                }
                // 2. Server SPAKE2 message.
                val passwordMaterial = password.toByteArray(Charsets.UTF_8) + tlsExporterBytes
                val bob = Spake2Bob(passwordMaterial)
                PairingProtocol.writeFrame(
                    out,
                    PairingProtocol.TYPE_SPAKE2,
                    bob.generateServerMessage(),
                )
                val keyMaterial = bob.deriveKeyMaterial(clientSpake.payload)
                val aesKey = HkdfSha256.derive(keyMaterial, null, PairingCrypto.HKDF_INFO, 16)
                val cipher = PairingCipher(aesKey)
                try {
                    // 3. Client peer info (8192-byte struct).
                    val clientInfo = PairingProtocol.readFrame(input)
                    check(clientInfo.type == PairingProtocol.TYPE_PEER_INFO) {
                        "expected peer info"
                    }
                    val clientPeerInfo =
                        try {
                            cipher.decrypt(clientInfo.payload)
                        } catch (e: AEADBadTagException) {
                            sawWrongCode = true
                            return // wrong code: drop the connection
                        }
                    if (clientPeerInfo.size != PairingCrypto.PEER_INFO_SIZE) {
                        sawWrongCode = true
                        return
                    }
                    peerInfo = clientPeerInfo
                    // 4. Server peer info (8192-byte struct).
                    val serverPeerInfo = ByteArray(PairingCrypto.PEER_INFO_SIZE)
                    serverPeerInfo[0] = PairingCrypto.PEER_INFO_RSA_PUB_KEY
                    val spki = keys.getOrCreateKeyPair().public.encoded
                    spki.copyInto(serverPeerInfo, 1)
                    PairingProtocol.writeFrame(
                        out,
                        PairingProtocol.TYPE_PEER_INFO,
                        cipher.encrypt(serverPeerInfo),
                    )
                    completedOk = true
                } finally {
                    cipher.destroy()
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
