package id.mybase.aether.adb

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import org.bouncycastle.tls.Certificate
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsClientContext
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.crypto.TlsCrypto
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto

/**
 * TLS transport for ADB pairing built on BouncyCastle (no hidden platform APIs).
 *
 * Background: the pairing channel binding requires 64 bytes of TLS keying material (RFC 5705, label
 * "adb-label\0"). The platform Conscrypt exposes this via
 * `com.android.org.conscrypt.Conscrypt.exportKeyingMaterial`, which is a hidden API — reflection is
 * blocked for apps targeting recent SDKs (verified failing on-device). BouncyCastle's
 * [org.bouncycastle.tls.TlsContext.exportKeyingMaterial] is public API.
 *
 * Only the pairing connection uses this transport. The post-pairing ADB connection
 * ([AdbTlsTransport.openAdbChannel]) keeps the platform TLS stack with the client certificate — it
 * never needs keying-material export.
 */
object BcTlsPairingTransport {

    /**
     * An established pairing TLS channel. [exporter] holds the 64-byte RFC 5705 keying material
     * (label "adb-label\0", see [PairingCrypto.TLS_EXPORTER_LABEL]). Call [close] to release the
     * TCP socket.
     */
    class PairingTlsChannel(
        val input: InputStream,
        val output: OutputStream,
        val exporter: ByteArray,
        private val closeAction: () -> Unit,
    ) : AutoCloseable {
        override fun close() = closeAction()
    }

    /**
     * Opens a TLS pairing channel to host:port.
     *
     * @param timeoutMs TCP connect timeout and SO_TIMEOUT for the handshake, in milliseconds.
     * @throws java.io.IOException on TCP/TLS failure.
     */
    fun openPairingChannel(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): PairingTlsChannel {
        val tcp = Socket()
        tcp.connect(InetSocketAddress(host, port), timeoutMs)
        // Bound the handshake reads; cleared after the handshake completes.
        tcp.soTimeout = timeoutMs
        try {
            val crypto: TlsCrypto = BcTlsCrypto()
            val protocol = TlsClientProtocol(tcp.inputStream, tcp.outputStream)
            val client = PairingTlsClient(crypto)
            protocol.connect(client)
            // Keep soTimeout for the pairing protocol exchange (SPAKE2 + peer info);
            // the caller bounds the overall operation with a coroutine timeout.
            // Exporter was captured in notifyHandshakeComplete() during connect().
            val exporter = client.exporter
            return PairingTlsChannel(
                input = protocol.inputStream,
                output = protocol.outputStream,
                exporter = exporter,
            ) {
                runCatching { protocol.close() }
                runCatching { tcp.close() }
            }
        } catch (e: Throwable) {
            runCatching { tcp.close() }
            throw e
        }
    }

    /**
     * BouncyCastle TLS client for pairing: trusts any server certificate (the pairing server uses a
     * self-signed cert; authentication is the 6-digit pairing code via SPAKE2, not the
     * certificate).
     */
    private class PairingTlsClient(crypto: TlsCrypto) : DefaultTlsClient(crypto) {
        private lateinit var clientContext: TlsClientContext

        /** 64-byte RFC 5705 keying material, captured at handshake completion. */
        lateinit var exporter: ByteArray
            private set

        override fun init(context: TlsClientContext) {
            super.init(context)
            clientContext = context
        }

        override fun getAuthentication(): TlsAuthentication =
            object : TlsAuthentication {
                override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) {
                    // Trust-all: the pairing endpoint presents a self-signed
                    // certificate; the pairing code is the authentication.
                    @Suppress("UNUSED_PARAMETER")
                    val ignored: Certificate? = serverCertificate.certificate
                }

                override fun getClientCredentials(
                    certificateRequest: org.bouncycastle.tls.CertificateRequest,
                ): org.bouncycastle.tls.TlsCredentials? = null
            }

        override fun notifyHandshakeComplete() {
            super.notifyHandshakeComplete()
            // BC only permits keying-material export from within this callback.
            val label = String(PairingCrypto.TLS_EXPORTER_LABEL, Charsets.UTF_8)
            val out =
                clientContext.exportKeyingMaterial(
                    label,
                    null,
                    PairingCrypto.TLS_EXPORTER_LENGTH,
                )
            require(out.size == PairingCrypto.TLS_EXPORTER_LENGTH) {
                "BC exported ${out.size} keying bytes, expected ${PairingCrypto.TLS_EXPORTER_LENGTH}"
            }
            exporter = out
        }
    }
}
