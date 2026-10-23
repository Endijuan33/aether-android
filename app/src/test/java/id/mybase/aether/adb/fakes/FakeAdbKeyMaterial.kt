package id.mybase.aether.adb.fakes

import id.mybase.aether.adb.AdbKeyMaterial
import id.mybase.aether.adb.DerCertificates
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey

/** Hand-written fake [AdbKeyMaterial]: fresh RSA-2048 keypair + self-signed cert. */
class FakeAdbKeyMaterial : AdbKeyMaterial {
    private val keyPair: KeyPair =
        KeyPairGenerator.getInstance("RSA")
            .apply {
                initialize(2048, SecureRandom())
            }
            .generateKeyPair()
    private val certDer: ByteArray =
        DerCertificates.selfSigned(
            publicKey = keyPair.public as RSAPublicKey,
            signer = keyPair.private,
            commonName = "Fake ADB",
        )

    override fun getOrCreateKeyPair(): KeyPair = keyPair

    override fun certificateDer(): ByteArray = certDer
}
