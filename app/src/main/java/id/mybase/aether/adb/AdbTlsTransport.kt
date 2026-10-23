package id.mybase.aether.adb

import java.net.InetSocketAddress
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * TLS channels for wireless debugging.
 *
 * Both the pairing endpoint and the ADB endpoint are spoken to over TLS from the first byte (there
 * is no plaintext preamble). adbd presents a self-signed certificate that the client cannot
 * validate against any trust anchor ahead of time, so the trust manager below accepts the server
 * certificate unconditionally. This mirrors AOSP's own adb client and is sound because
 * authentication does not rest on TLS PKI here:
 * - during pairing, the SPAKE2 password (6-digit code) authenticates the peer;
 * - afterwards, adbd authorizes *our* client certificate's public key, and the TLS handshake proves
 *   possession of the matching private key.
 */
internal object AdbTlsTransport {

    /**
     * TLS socket to the ADB endpoint, presenting our client certificate so adbd can authorize the
     * connection against the key registered at pairing time. Cancellation-safe: if the caller is
     * cancelled while the TCP connect or TLS handshake is blocked, the socket is closed to unblock
     * the I/O thread (no leaks).
     *
     * Note: the pairing endpoint uses [BcTlsPairingTransport] (BouncyCastle), not this object,
     * because pairing needs RFC 5705 keying-material export which the platform Conscrypt only
     * exposes via a hidden API.
     *
     * @param handshakeTimeoutMs OS-level read timeout (soTimeout) for the TLS handshake, in
     *   milliseconds. This is independent of the coroutine Delay, so it fires in real wall-clock
     *   time even when the caller runs under kotlinx-coroutines-test's virtual time (where a
     *   coroutine withTimeout around blocking I/O would never fire). 0 (default) disables the
     *   timeout.
     */
    suspend fun openAdbChannel(
        host: String,
        port: Int,
        keys: AdbKeyMaterial,
        connectTimeoutMs: Int,
        handshakeTimeoutMs: Int = 0,
    ): SSLSocket {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf(SingleKeyManager(keys)), arrayOf(trustAllManager()), SecureRandom())
        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        if (handshakeTimeoutMs > 0) socket.soTimeout = handshakeTimeoutMs
        try {
            withSocketGuard(socket) {
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.startHandshake()
            }
            // Handshake done: back to blocking mode. The caller (AdbSession.open)
            // applies its own soTimeout for the CNXN exchange.
            socket.soTimeout = 0
            return socket
        } catch (e: Throwable) {
            runCatching { socket.close() }
            throw e
        }
    }

    private fun trustAllManager(): X509TrustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

    /**
     * Key manager serving the single ADB identity. The chain holds our self-signed certificate;
     * adbd matches its public key against the keys authorized during pairing.
     */
    private class SingleKeyManager(private val keys: AdbKeyMaterial) : X509ExtendedKeyManager() {
        companion object {
            private const val ALIAS = "aether-adb"
        }

        private val chain: Array<X509Certificate> by lazy {
            val factory = CertificateFactory.getInstance("X.509")
            val cert =
                factory.generateCertificate(keys.certificateDer().inputStream()) as X509Certificate
            arrayOf(cert)
        }

        override fun getClientAliases(keyType: String, issuers: Array<Principal>): Array<String>? =
            arrayOf(ALIAS)

        override fun chooseClientAlias(
            keyType: Array<String>,
            issuers: Array<Principal>?,
            socket: Socket?,
        ): String = ALIAS

        override fun chooseEngineClientAlias(
            keyType: Array<String>,
            issuers: Array<Principal>?,
            engine: SSLEngine?,
        ): String = ALIAS

        override fun getServerAliases(keyType: String, issuers: Array<Principal>): Array<String>? =
            emptyArray()

        override fun chooseServerAlias(
            keyType: String,
            issuers: Array<Principal>?,
            socket: Socket?,
        ): String? = null

        override fun chooseEngineServerAlias(
            keyType: String,
            issuers: Array<Principal>?,
            engine: SSLEngine?,
        ): String? = null

        override fun getCertificateChain(alias: String): Array<X509Certificate>? = chain

        override fun getPrivateKey(alias: String): PrivateKey? = keys.getOrCreateKeyPair().private
    }
}

/**
 * The current coroutine's [Job], captured via the continuation's context.
 *
 * (Bare `coroutineContext` only resolves inside a [kotlinx.coroutines.CoroutineScope]; these are
 * top-level suspend functions. `kotlin.coroutines.currentCoroutineContext()` is not present in the
 * kotlin-stdlib 2.4.21 artifact served by Maven Central — verified absent from the jar bytecode on
 * 2026-10-24, in both the sandbox copy and a fresh re-download — so it cannot be used here.
 * `Continuation.context` is a plain property access and works everywhere.)
 */
internal suspend fun currentJob(): Job? = suspendCancellableCoroutine { cont ->
    cont.resumeWith(Result.success(cont.context[Job]))
}

/**
 * Runs blocking socket I/O, closing [socket] if the calling coroutine is cancelled while blocked.
 * Closing the socket unblocks the I/O thread, so a cancelled connect/handshake/read can never leak
 * a thread or a socket.
 *
 * Callers must still distinguish genuine I/O failures from cancellation: when the current coroutine
 * is no longer active, an [java.io.IOException] from the block should be treated as cancellation,
 * not as a transport failure (see usages).
 */
internal suspend fun <T> withSocketGuard(socket: Socket, block: suspend () -> T): T {
    val hook =
        currentJob()?.invokeOnCompletion { cause ->
            if (cause is CancellationException) runCatching { socket.close() }
        }
    try {
        return block()
    } finally {
        hook?.dispose()
    }
}
