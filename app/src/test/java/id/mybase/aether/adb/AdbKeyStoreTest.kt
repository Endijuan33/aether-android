package id.mybase.aether.adb

import id.mybase.aether.adb.fakes.FakeContext
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the key stores return a stable RSA-2048 identity and matching cert. */
class AdbKeyStoreTest {

    private fun tempDir() = Files.createTempDirectory("aether-keys-test").toFile()

    @Test
    fun `file keystore returns the same keypair across calls`() {
        val dir = tempDir()
        try {
            val ks = FileAdbKeyStore(dir)
            val first = ks.getOrCreateKeyPair()
            val second = ks.getOrCreateKeyPair()
            assertArrayEquals(first.public.encoded, second.public.encoded)
            assertArrayEquals(first.private.encoded, second.private.encoded)
            assertEquals(2048, (first.public as RSAPublicKey).modulus.bitLength())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `file keystore persists across instances`() {
        val dir = tempDir()
        try {
            val before = FileAdbKeyStore(dir).getOrCreateKeyPair()
            val after = FileAdbKeyStore(dir).getOrCreateKeyPair()
            assertArrayEquals(before.public.encoded, after.public.encoded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `file keystore generates distinct keys per directory`() {
        val a = tempDir()
        val b = tempDir()
        try {
            val keyA = FileAdbKeyStore(a).getOrCreateKeyPair()
            val keyB = FileAdbKeyStore(b).getOrCreateKeyPair()
            assertTrue(!keyA.public.encoded.contentEquals(keyB.public.encoded))
        } finally {
            a.deleteRecursively()
            b.deleteRecursively()
        }
    }

    @Test
    fun `file keystore certificate matches the keypair`() {
        val dir = tempDir()
        try {
            val ks = FileAdbKeyStore(dir)
            val cert =
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(ks.certificateDer().inputStream()) as X509Certificate
            assertArrayEquals(
                (ks.getOrCreateKeyPair().public as RSAPublicKey).encoded,
                (cert.publicKey as RSAPublicKey).encoded,
            )
            cert.verify(cert.publicKey)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `android keystore fails closed without writing exportable identity files`() {
        val dir = tempDir()
        try {
            val ks = AndroidAdbKeyStore(FakeContext(dir))
            org.junit.Assert.assertThrows(java.security.KeyStoreException::class.java) {
                ks.getOrCreateKeyPair()
            }
            assertTrue(dir.walkTopDown().none { it.isFile })
        } finally {
            dir.deleteRecursively()
        }
    }
}
