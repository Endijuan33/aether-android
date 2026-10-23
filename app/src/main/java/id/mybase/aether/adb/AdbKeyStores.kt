package id.mybase.aether.adb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * [AdbKeyMaterial] backed by the platform AndroidKeyStore.
 *
 * The RSA-2048 key is generated *inside* the keystore under [ALIAS] with a platform-minted
 * self-signed certificate, so the private key is non-exportable on devices with hardware-backed
 * keystore. On runtimes without the AndroidKeyStore provider (plain JVM, some test environments)
 * this degrades to [FileAdbKeyStore]; the backing is chosen once per instance so
 * [getOrCreateKeyPair] and [certificateDer] can never disagree.
 *
 * Key material is never logged.
 */
class AndroidAdbKeyStore(appContext: Context) : AdbKeyMaterial {
    companion object {
        const val ALIAS = "AETHER_ADB_PAIRING"
        private const val DIR_NAME = "aether-adb"
    }

    private val backing: AdbKeyMaterial =
        try {
            AndroidKeyStoreBacking().also { it.getOrCreateKeyPair() }
        } catch (e: Exception) {
            FileAdbKeyStore(File(appContext.filesDir, DIR_NAME))
        }

    override fun getOrCreateKeyPair(): KeyPair = backing.getOrCreateKeyPair()

    override fun certificateDer(): ByteArray = backing.certificateDer()

    private class AndroidKeyStoreBacking : AdbKeyMaterial {
        override fun getOrCreateKeyPair(): KeyPair {
            val ks = androidKeyStore()
            (ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let {
                return KeyPair(it.certificate.publicKey, it.privateKey)
            }
            val keyGen =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + VALIDITY_MS)
            keyGen.initialize(
                KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                    )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(X500Principal("CN=AETHER ADB Pairing"))
                    .setCertificateSerialNumber(BigInteger(64, SecureRandom()))
                    .setCertificateNotBefore(notBefore)
                    .setCertificateNotAfter(notAfter)
                    .build(),
            )
            keyGen.generateKeyPair()
            val created =
                ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
                    ?: throw KeyStoreException(
                        "AndroidKeyStore entry $ALIAS missing after generation",
                    )
            return KeyPair(created.certificate.publicKey, created.privateKey)
        }

        override fun certificateDer(): ByteArray {
            val ks = androidKeyStore()
            return ks.getCertificate(ALIAS)?.encoded
                ?: throw KeyStoreException("AndroidKeyStore has no certificate for $ALIAS")
        }

        private fun androidKeyStore(): KeyStore =
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        companion object {
            private const val VALIDITY_MS = 25L * 365 * 24 * 3600 * 1000
        }
    }
}

/**
 * File-backed [AdbKeyMaterial] used when the AndroidKeyStore is unavailable.
 *
 * SECURITY NOTE: the PKCS#8 private key is stored in the app-private directory
 * ([Context.getFilesDir], Linux UID-isolated, no world-readable bit), but unlike the
 * hardware-backed keystore it is extractable on rooted devices, from backups, or from a compromised
 * OS. This fallback exists for robustness; the AndroidKeyStore path is always preferred when
 * present. Key material is never logged.
 */
internal class FileAdbKeyStore(private val directory: File) : AdbKeyMaterial {
    private val lock = Any()
    private var cached: Pair<KeyPair, ByteArray>? = null

    override fun getOrCreateKeyPair(): KeyPair =
        synchronized(lock) {
            cached?.first ?: load().also { cached = it }.first
        }

    override fun certificateDer(): ByteArray =
        synchronized(lock) {
            (cached ?: load().also { cached = it }).second
        }

    private fun load(): Pair<KeyPair, ByteArray> {
        val keyFile = File(directory, KEY_FILE)
        val certFile = File(directory, CERT_FILE)
        if (keyFile.isFile && certFile.isFile) {
            try {
                val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyFile.readBytes())
                val private = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec)
                val certBytes = certFile.readBytes()
                val cert =
                    java.security.cert.CertificateFactory.getInstance("X.509")
                        .generateCertificate(certBytes.inputStream())
                return KeyPair(cert.publicKey, private) to certBytes
            } catch (e: Exception) {
                // Corrupt store: fall through and regenerate.
                keyFile.delete()
                certFile.delete()
            }
        }
        return generate()
    }

    private fun generate(): Pair<KeyPair, ByteArray> {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        val keyPair = keyGen.generateKeyPair()
        val certDer =
            DerCertificates.selfSigned(
                publicKey = keyPair.public as RSAPublicKey,
                signer = keyPair.private,
            )
        directory.mkdirs()
        File(directory, KEY_FILE).writeBytes(keyPair.private.encoded)
        File(directory, CERT_FILE).writeBytes(certDer)
        return keyPair to certDer
    }

    companion object {
        internal const val KEY_FILE = "adb_pairing.key"
        internal const val CERT_FILE = "adb_pairing.crt"
    }
}
