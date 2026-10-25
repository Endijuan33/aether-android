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
 * self-signed certificate. AndroidKeyStore private keys are non-exportable through the app API;
 * hardware backing depends on the device. Provider failures are surfaced, with no file fallback.
 *
 * Key material is never logged.
 */
class AndroidAdbKeyStore(appContext: Context) : AdbKeyMaterial {
    companion object {
        // V1/V2 entries remain intact. Conscrypt delegates TLS RSA signatures via private-key
        // Cipher.ENCRYPT_MODE; AndroidKeyStore converts that into SIGN with DIGEST_NONE.
        // V3 authorizes that raw operation without adding encryption/decryption purposes.
        // Existing key authorizations cannot be amended; system-mediated re-pairing is required.
        const val ALIAS = "AETHER_ADB_PAIRING_V3"
    }

    private val backing: AdbKeyMaterial by lazy { AndroidKeyStoreBacking() }

    @Synchronized override fun getOrCreateKeyPair(): KeyPair = backing.getOrCreateKeyPair()

    @Synchronized
    override fun certificateDer(): ByteArray {
        getOrCreateKeyPair()
        return backing.certificateDer()
    }

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
                    .setDigests(
                        KeyProperties.DIGEST_NONE,
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512,
                    )
                    .setSignaturePaddings(
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                    )
                    // KeyMint padding authorizations are shared by signing/cipher operations.
                    // BoringSSL encodes TLS RSA-PSS before delegating the raw RSA operation.
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
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
 * File-backed [AdbKeyMaterial] retained for isolated JVM test identities.
 *
 * SECURITY NOTE: the PKCS#8 private key is stored in the app-private directory
 * ([Context.getFilesDir], Linux UID-isolated, no world-readable bit), but unlike the
 * hardware-backed keystore it is extractable on rooted devices, from backups, or from a compromised
 * OS. Production never selects this fixture: AndroidKeyStore failures are surfaced instead of
 * falling back to files. Key material is never logged.
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
