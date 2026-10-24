package id.mybase.aether.adb

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.conscrypt.Conscrypt

/**
 * TLS transport for the ADB pairing endpoint, backed by the standalone Conscrypt library
 * (BoringSSL).
 *
 * Why Conscrypt and not the platform SSLSocket or BouncyCastle?
 * - The platform's Conscrypt (`com.android.org.conscrypt`) exposes RFC 5705 keying-material export
 *   only via a hidden API, which is blocked for apps targeting recent SDKs (verified on-device:
 *   "TLS keying material export failed").
 * - BouncyCastle's TLS 1.3 stack has an interop bug with adbd's BoringSSL: the handshake completes,
 *   but the server resets the connection on the first application-data write (verified on-device:
 *   "ConnectionResetException" at [write SPAKE2]).
 * - The standalone Conscrypt library (`org.conscrypt:conscrypt-android`) is BoringSSL-based, so it
 *   interops perfectly with adbd, AND it exposes `Conscrypt.exportKeyingMaterial` as a PUBLIC API
 *   (see javadoc for conscrypt-openjdk).
 *
 * The pairing password is the 6-digit code concatenated with 64 bytes of TLS exporter (RFC 5705,
 * label "adb-label" + NUL, matching AOSP's pairing_connection).
 */
object ConscryptPairingTransport {

    /**
     * An established pairing TLS channel. The [exporter] is the 64-byte RFC 5705 keying material
     * captured after the handshake completes.
     */
    class PairingTlsChannel(
        val input: InputStream,
        val output: OutputStream,
        val exporter: ByteArray,
        private val closeAction: () -> Unit,
    ) {
        fun close() = closeAction()
    }

    /**
     * Opens a TLS connection to the pairing endpoint and returns the channel with the 64-byte
     * exporter. Uses Conscrypt's SSLContext (BoringSSL) with a trust-all manager and a self-signed
     * RSA client certificate (like Shizuku) — adbd expects a client cert in the pairing handshake.
     */
    fun openPairingChannel(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): PairingTlsChannel {
        // Use Conscrypt's provider directly (no need to install globally).
        // Note: do NOT call SSLContext.getInstance("TLS").socketFactory here —
        // that throws "SSLContext is not initialized" on an uninitialized context.
        val sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider())
        val trustAll =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        // Present a self-signed RSA client certificate (Shizuku does this; adbd expects it).
        val keyStore = PairingClientCert.generateKeyStore()
        val keyManager = PairingClientCert.createKeyManager(keyStore)
        sslContext.init(
            arrayOf<KeyManager>(keyManager),
            arrayOf<TrustManager>(trustAll),
            SecureRandom(),
        )

        val tcp = Socket()
        tcp.connect(InetSocketAddress(host, port), timeoutMs)
        tcp.soTimeout = timeoutMs

        val sslSocket = sslContext.socketFactory.createSocket(tcp, host, port, true) as SSLSocket
        try {
            sslSocket.soTimeout = timeoutMs
            sslSocket.startHandshake()

            // Public API in standalone Conscrypt (not hidden like the platform's).
            val label = String(PairingCrypto.TLS_EXPORTER_LABEL, Charsets.UTF_8)
            val exporter =
                Conscrypt.exportKeyingMaterial(
                    sslSocket,
                    label,
                    null,
                    PairingCrypto.TLS_EXPORTER_LENGTH,
                ) ?: throw java.io.IOException("Conscrypt returned null exporter")
            require(exporter.size == PairingCrypto.TLS_EXPORTER_LENGTH) {
                "Conscrypt exported ${exporter.size} bytes, expected ${PairingCrypto.TLS_EXPORTER_LENGTH}"
            }

            return PairingTlsChannel(
                input = sslSocket.inputStream,
                output = sslSocket.outputStream,
                exporter = exporter,
                closeAction = { runCatching { sslSocket.close() } },
            )
        } catch (e: Exception) {
            runCatching { sslSocket.close() }
            throw e
        }
    }
}
