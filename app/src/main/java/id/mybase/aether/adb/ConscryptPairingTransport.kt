package id.mybase.aether.adb

import id.mybase.aether.adb.pairing.*
import java.io.InputStream
import java.io.OutputStream
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
 *   via a hidden framework API; production avoids hidden-API access. The standalone provider
 *   exposes that API publicly.
 * - BouncyCastle is confined to JVM fixtures. Those tests substitute exporter bytes and cannot
 *   prove Android/adbd interoperability. No universal compatibility is asserted here. See
 *   docs/limitations.md for actual device validation.
 * - Standalone Conscrypt delegates non-exportable RSA signing to AndroidKeyStore; the V3 identity
 *   authorizes DIGEST_NONE/raw padding for that operation while retaining SIGN/VERIFY purposes.
 *   Authentication still requires the real TLS/exporter/SPAKE2/peer-info exchange.
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
    suspend fun openPairingChannel(
        host: String,
        port: Int,
        timeoutMs: Int,
        keys: AdbKeyMaterial,
    ): PairingTlsChannel {
        var stage = PairingProtocolStage.TLS_IDENTITY
        fun mark(next: PairingProtocolStage) {
            stage = next
            AndroidPairingDiagnostics.record(
                PairingDiagnostic(PairingDiagnosticEvent.PROTOCOL_STAGE, protocolStage = stage),
            )
        }
        mark(PairingProtocolStage.TLS_IDENTITY)
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
        val keyManager = AdbTlsTransport.SingleKeyManager(keys)
        sslContext.init(
            arrayOf<KeyManager>(keyManager),
            arrayOf<TrustManager>(trustAll),
            SecureRandom(),
        )

        val tcp = Socket()
        var sslSocket: SSLSocket? = null
        try {
            return withSocketGuard(tcp) {
                mark(PairingProtocolStage.TCP_CONNECT)
                tcp.connect(localAdbEndpoint(host, port), timeoutMs)
                tcp.soTimeout = timeoutMs
                val tls = sslContext.socketFactory.createSocket(tcp, host, port, true) as SSLSocket
                sslSocket = tls
                tls.enabledProtocols =
                    tls.supportedProtocols.filter { it == "TLSv1.3" }.toTypedArray()
                tls.soTimeout = timeoutMs
                mark(PairingProtocolStage.TLS_HANDSHAKE)
                tls.startHandshake()

                // Public API in standalone Conscrypt (not hidden like the platform's).
                val label = String(PairingCrypto.TLS_EXPORTER_LABEL, Charsets.UTF_8)
                mark(PairingProtocolStage.TLS_EXPORTER)
                val exporter =
                    Conscrypt.exportKeyingMaterial(
                        tls,
                        label,
                        null,
                        PairingCrypto.TLS_EXPORTER_LENGTH,
                    ) ?: throw java.io.IOException("Conscrypt returned null exporter")
                require(exporter.size == PairingCrypto.TLS_EXPORTER_LENGTH) {
                    "Conscrypt exported ${exporter.size} bytes, expected ${PairingCrypto.TLS_EXPORTER_LENGTH}"
                }

                mark(PairingProtocolStage.TLS_READY)
                PairingTlsChannel(
                    input = tls.inputStream,
                    output = tls.outputStream,
                    exporter = exporter,
                    closeAction = { tls.close() },
                )
            }
        } catch (e: Throwable) {
            AndroidPairingDiagnostics.protocolFailure(stage, e)
            runCatching { sslSocket?.close() }
            runCatching { tcp.close() }
            throw e
        }
    }
}
