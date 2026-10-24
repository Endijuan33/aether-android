package id.mybase.aether.adb

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509KeyManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * Generates the RSA client certificate presented during the pairing TLS handshake.
 *
 * Shizuku (proven on-device) presents a self-signed RSA-2048 client certificate via a KeyManager
 * during pairing. AETHER previously used a null KeyManager (no client cert), and adbd reset the
 * connection on the first application-data write — the missing client certificate is the prime
 * suspect.
 *
 * The certificate is self-signed, CN=00 (matching Shizuku), valid for 30 years. The pairing code
 * (not the certificate) is the actual authentication; the cert just needs to be present.
 */
internal object PairingClientCert {

    private const val KEY_ALIAS = "aether-pairing"
    private const val KEY_SIZE = 2048
    private const val VALIDITY_DAYS = 30 * 365L

    /**
     * Generates a new RSA-2048 keypair and self-signed certificate, stored in an in-memory
     * KeyStore. Called once per pairing attempt (cheap enough; ~100ms).
     */
    fun generateKeyStore(): KeyStore {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(KEY_SIZE, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        val now = Date()
        val expiry = Date(now.time + VALIDITY_DAYS * 24 * 60 * 60 * 1000)
        val serial = BigInteger(64, SecureRandom())
        val subject = X500Name("CN=00")

        val certBuilder =
            JcaX509v3CertificateBuilder(
                subject, // issuer = subject (self-signed)
                serial,
                now,
                expiry,
                subject,
                keyPair.public,
            )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val holder = certBuilder.build(signer)
        val cert: X509Certificate = JcaX509CertificateConverter().getCertificate(holder)

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            KEY_ALIAS,
            keyPair.private,
            CharArray(0),
            arrayOf(cert),
        )
        return keyStore
    }

    /** Creates a KeyManager presenting the generated client certificate. */
    fun createKeyManager(keyStore: KeyStore): X509KeyManager {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, CharArray(0))
        val managers: Array<KeyManager> = kmf.keyManagers
        return managers.filterIsInstance<X509KeyManager>().firstOrNull()
            ?: throw IllegalStateException("No X509KeyManager available")
    }
}
