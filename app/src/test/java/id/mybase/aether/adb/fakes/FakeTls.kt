package id.mybase.aether.adb.fakes

import id.mybase.aether.adb.AdbKeyMaterial
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

/** Trust-all manager for fakes: test servers are self-signed by construction. */
internal fun fakeTrustAll(): X509TrustManager =
    object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

/** Key manager serving a single [AdbKeyMaterial] identity (test-only). */
internal class FakeKeyManager(private val keys: AdbKeyMaterial) : X509ExtendedKeyManager() {
    private val chain: Array<X509Certificate> by lazy {
        val factory = CertificateFactory.getInstance("X.509")
        arrayOf(factory.generateCertificate(keys.certificateDer().inputStream()) as X509Certificate)
    }

    override fun getClientAliases(keyType: String, issuers: Array<Principal>): Array<String>? =
        arrayOf("fake")

    override fun chooseClientAlias(
        keyType: Array<String>,
        issuers: Array<Principal>?,
        socket: Socket?,
    ): String = "fake"

    override fun chooseEngineClientAlias(
        keyType: Array<String>,
        issuers: Array<Principal>?,
        engine: SSLEngine?,
    ): String = "fake"

    override fun getServerAliases(keyType: String, issuers: Array<Principal>): Array<String>? =
        arrayOf("fake")

    override fun chooseServerAlias(
        keyType: String,
        issuers: Array<Principal>?,
        socket: Socket?,
    ): String = "fake"

    override fun chooseEngineServerAlias(
        keyType: String,
        issuers: Array<Principal>?,
        engine: SSLEngine?,
    ): String = "fake"

    override fun getCertificateChain(alias: String): Array<X509Certificate>? = chain

    override fun getPrivateKey(alias: String): PrivateKey? = keys.getOrCreateKeyPair().private
}

/** Server-side TLS context for fakes, optionally requiring a client certificate. */
internal fun fakeTlsServerContext(keys: AdbKeyMaterial): SSLContext =
    SSLContext.getInstance("TLS").apply {
        init(arrayOf(FakeKeyManager(keys)), arrayOf(fakeTrustAll()), SecureRandom())
    }

/** Wraps an accepted plain socket in a server-mode TLS socket and handshakes. */
internal fun tlsServerWrap(
    socket: Socket,
    keys: AdbKeyMaterial,
    needClientAuth: Boolean = false,
): SSLSocket {
    val ssl =
        fakeTlsServerContext(keys)
            .socketFactory
            .createSocket(
                socket,
                socket.inetAddress.hostAddress,
                socket.port,
                true,
            ) as SSLSocket
    ssl.useClientMode = false
    ssl.needClientAuth = needClientAuth
    ssl.startHandshake()
    return ssl
}
