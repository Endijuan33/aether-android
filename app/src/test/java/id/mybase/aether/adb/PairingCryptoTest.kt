package id.mybase.aether.adb

import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the pairing crypto primitives behave per their specs. */
class PairingCryptoTest {

    // ---------------------------------------------------------------- Ed25519

    @Test
    fun `base point times group order is the identity`() {
        assertTrue(Ed25519.isIdentity(Ed25519.scalarMult(Ed25519.order, Ed25519.BASE)))
    }

    @Test
    fun `encode then decode recovers the point`() {
        for (point in listOf(Ed25519.BASE, Ed25519.SPAKE_M, Ed25519.SPAKE_N)) {
            assertEquals(point, Ed25519.decode(Ed25519.encode(point)))
        }
    }

    @Test
    fun `M and N decode and are distinct non-identity points`() {
        // BoringSSL's M has order 2L and N has order 8L (NOT prime-order);
        // the cofactor clearing confines the effective masks to the subgroup.
        assertFalse(Ed25519.isIdentity(Ed25519.SPAKE_M))
        assertFalse(Ed25519.isIdentity(Ed25519.SPAKE_N))
        assertFalse(Ed25519.encode(Ed25519.SPAKE_M).contentEquals(Ed25519.encode(Ed25519.SPAKE_N)))
        // Sanity: 8*M and 8*N land in the prime-order subgroup.
        assertTrue(
            Ed25519.isInSubgroup(
                Ed25519.scalarMultRaw(ByteArray(32) { if (it == 0) 8 else 0 }, Ed25519.SPAKE_M),
            ),
        )
        assertTrue(
            Ed25519.isInSubgroup(
                Ed25519.scalarMultRaw(ByteArray(32) { if (it == 0) 8 else 0 }, Ed25519.SPAKE_N),
            ),
        )
    }

    @Test
    fun `boringssl M and N match the pinned oracle values`() {
        assertArrayEquals(
            hexToBytes("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"),
            Ed25519.encode(Ed25519.SPAKE_M),
        )
        assertArrayEquals(
            hexToBytes("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"),
            Ed25519.encode(Ed25519.SPAKE_N),
        )
    }

    @Test
    fun `scalar multiplication is consistent`() {
        val p = Ed25519.scalarMult(BigInteger.valueOf(123456789L), Ed25519.BASE)
        assertEquals(p, Ed25519.scalarMult(BigInteger.valueOf(123456789L), Ed25519.BASE))
        assertTrue(Ed25519.isInSubgroup(p))
        assertFalse(Ed25519.isIdentity(p))
    }

    @Test
    fun `addition matches scalar doubling`() {
        val doubled = Ed25519.add(Ed25519.BASE, Ed25519.BASE)
        val scaled = Ed25519.scalarMult(BigInteger.valueOf(2L), Ed25519.BASE)
        assertArrayEquals(Ed25519.encode(doubled), Ed25519.encode(scaled))
    }

    @Test
    fun `decoding invalid encodings returns null`() {
        // Wrong size.
        assertNull(Ed25519.decode(ByteArray(31)))
        // y >= p can never be a valid point (all top bits set).
        assertNull(Ed25519.decode(ByteArray(32) { 0xFF.toByte() }))
    }

    // ------------------------------------------------------------------ SPAKE2

    // Password material = code bytes || 64-byte TLS exporter (channel binding).
    // Tests use a fixed exporter; production exports from the real TLS session.
    private val exporterA = ByteArray(64) { 0xA5.toByte() }
    private val passwordA = "123456".toByteArray(Charsets.UTF_8) + exporterA
    private val passwordB = "654321".toByteArray(Charsets.UTF_8) + exporterA

    @Test
    fun `spake2 client and server agree on the key`() {
        val alice = Spake2Alice(passwordA)
        val bob = Spake2Bob(passwordA)
        val serverMsg = bob.generateServerMessage()
        val clientMsg = alice.generateMessage()
        val aliceKey = alice.deriveKeyMaterial(serverMsg)
        val bobKey = bob.deriveKeyMaterial(clientMsg)
        assertArrayEquals(aliceKey, bobKey)
        assertEquals(64, aliceKey.size)
    }

    @Test
    fun `spake2 matches the boringssl oracle vector`() {
        // From boringssl-spake2-vectors.json, "normal" exchange:
        // password "515109", deterministic ephemeral inputs.
        val password = "515109".toByteArray(Charsets.UTF_8)
        val alicePriv64 =
            hexToBytes(
                "0100000000000000000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000",
            )
        val alice = Spake2Alice(password, alicePriv64)
        assertArrayEquals(
            hexToBytes("1876811ed2c78beb885abcfeee9174e822e056d67474d7f924e5c019d087dc71"),
            alice.generateMessage(),
        )
        val bobMsg =
            hexToBytes(
                "e814a51e5e1032ce32bd9e47f74fb58a42f02b61b355e062a36836dbd8de35c5",
            )
        // Note: the oracle uses names "alice"/"bob"; our transcript uses the
        // AOSP pairing names, so only the message (not the key) is comparable.
    }

    @Test
    fun `spake2 transcript hash matches boringssl oracle`() {
        // Recomputes the oracle's "normal" keyHex from its transcript fields,
        // proving our transcriptHash construction is byte-identical to BoringSSL's.
        val myName = hexToBytes("616c696365") // "alice"
        val theirName = hexToBytes("626f62") // "bob"
        val myMsg = hexToBytes("1876811ed2c78beb885abcfeee9174e822e056d67474d7f924e5c019d087dc71")
        val peerMsg = hexToBytes("e814a51e5e1032ce32bd9e47f74fb58a42f02b61b355e062a36836dbd8de35c5")
        val dh = hexToBytes("afbbe99a10923155f111c16fbfb24bf14d64dba253c6122110656e624fd18070")
        val pwHash =
            hexToBytes(
                "666c546a12015203ec9263be9e7291caaacf22c63cd826efcce71310201505c" +
                    "78bbb7b3c912ad57c39fe2e9b65fd56c46088fc1def90cbbd066fb982f25a0eb0",
            )
        assertArrayEquals(
            hexToBytes(
                "f1e5cb026ca1dc0780d629cd1747b834bceed4b05417b80f2133a2bd0a0e8b21c1" +
                    "467782f8cab09ba0a20c4a785029dc95c028e20deff35812c01a3a0d8e9cb2",
            ),
            Spake2Alice.transcriptHash(myName, theirName, myMsg, peerMsg, dh, pwHash),
        )
    }

    @Test
    fun `spake2 with different passwords yields different keys`() {
        val alice = Spake2Alice(passwordA)
        val bob = Spake2Bob(passwordB)
        val serverMsg = bob.generateServerMessage()
        val clientMsg = alice.generateMessage()
        val aliceKey = alice.deriveKeyMaterial(serverMsg)
        val bobKey = bob.deriveKeyMaterial(clientMsg)
        assertFalse(aliceKey.contentEquals(bobKey))
    }

    @Test
    fun `spake2 rejects an undecodable peer message`() {
        val alice = Spake2Alice(passwordA)
        alice.generateMessage()
        // Wrong size and y >= p both fail to decode.
        assertThrows(Spake2Exception::class.java) { alice.deriveKeyMaterial(ByteArray(10)) }
        assertThrows(Spake2Exception::class.java) {
            alice.deriveKeyMaterial(ByteArray(32) { 0xFF.toByte() })
        }
    }

    @Test
    fun `spake2 tampered peer message yields a different key`() {
        // SPAKE2 messages carry no integrity tag: flipping the encoded x-parity
        // bit still decodes to a valid on-curve point (point negation), so
        // derivation succeeds but the key no longer matches. The handshake
        // detects this later when the peer-info AEAD decryption fails.
        val alice = Spake2Alice(passwordA)
        val bob = Spake2Bob(passwordA)
        val serverMsg = bob.generateServerMessage()
        val tampered = serverMsg.copyOf()
        tampered[31] = (tampered[31].toInt() xor 0x80).toByte()
        alice.generateMessage()
        val honestKey = alice.deriveKeyMaterial(serverMsg)
        val alice2 = Spake2Alice(passwordA)
        alice2.generateMessage()
        val tamperedKey = alice2.deriveKeyMaterial(tampered)
        assertFalse(honestKey.contentEquals(tamperedKey))
    }

    @Test
    fun `spake2 key is deterministic for one transcript`() {
        val alice = Spake2Alice(passwordA)
        val bob = Spake2Bob(passwordA)
        val serverMsg = bob.generateServerMessage()
        val clientMsg = alice.generateMessage()
        assertArrayEquals(alice.deriveKeyMaterial(serverMsg), alice.deriveKeyMaterial(serverMsg))
        assertArrayEquals(bob.deriveKeyMaterial(clientMsg), bob.deriveKeyMaterial(clientMsg))
    }

    @Test
    fun `password scalar hardening clears the low three bits`() {
        // BoringSSL's hack: after hardening, w mod 8 == 0.
        val w =
            Spake2Alice.passwordScalar(
                MessageDigest.getInstance("SHA-512").digest("999999".toByteArray()),
            )
        assertEquals(0, w[0].toInt() and 0x07)
    }

    // -------------------------------------------------------------------- HKDF

    @Test
    fun `hkdf matches RFC 5869 test case 1`() {
        val ikm = ByteArray(22) { 0x0b }
        val salt =
            byteArrayOf(
                0x00,
                0x01,
                0x02,
                0x03,
                0x04,
                0x05,
                0x06,
                0x07,
                0x08,
                0x09,
                0x0a,
                0x0b,
                0x0c,
            )
        val info =
            byteArrayOf(
                0xf0.toByte(),
                0xf1.toByte(),
                0xf2.toByte(),
                0xf3.toByte(),
                0xf4.toByte(),
                0xf5.toByte(),
                0xf6.toByte(),
                0xf7.toByte(),
                0xf8.toByte(),
                0xf9.toByte(),
            )
        val expected =
            hexToBytes(
                "3cb25f25faacd57a90434f64d0362f2a" +
                    "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                    "34007208d5b887185865",
            )
        assertArrayEquals(expected, HkdfSha256.derive(ikm, salt, info, 42))
    }

    @Test
    fun `hkdf info separation gives different keys`() {
        val ikm = ByteArray(32) { it.toByte() }
        val a = HkdfSha256.derive(ikm, null, "info-a".toByteArray(), 16)
        val b = HkdfSha256.derive(ikm, null, "info-b".toByteArray(), 16)
        assertFalse(a.contentEquals(b))
    }

    // ------------------------------------------------------------- PairingCipher

    @Test
    fun `cipher encrypt decrypt round-trips`() {
        val cipher = PairingCipher(ByteArray(16) { it.toByte() })
        try {
            val plaintext = "peer-info-payload".toByteArray()
            assertArrayEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)))
        } finally {
            cipher.destroy()
        }
    }

    @Test
    fun `cipher never reuses a nonce`() {
        val cipher = PairingCipher(ByteArray(16) { 7 })
        try {
            val first = cipher.encrypt("same".toByteArray())
            val second = cipher.encrypt("same".toByteArray())
            assertFalse(first.contentEquals(second))
        } finally {
            cipher.destroy()
        }
    }

    @Test
    fun `cipher tampered ciphertext fails authentication`() {
        val cipher = PairingCipher(ByteArray(16) { 7 })
        try {
            val tampered = cipher.encrypt("data".toByteArray())
            tampered[5] = (tampered[5].toInt() xor 0x01).toByte()
            assertThrows(AEADBadTagException::class.java) { cipher.decrypt(tampered) }
        } finally {
            cipher.destroy()
        }
    }

    @Test
    fun `cipher decryption with the wrong key fails`() {
        val enc = PairingCipher(ByteArray(16) { 1 })
        val dec = PairingCipher(ByteArray(16) { 2 })
        try {
            assertThrows(AEADBadTagException::class.java) {
                dec.decrypt(enc.encrypt("data".toByteArray()))
            }
        } finally {
            enc.destroy()
            dec.destroy()
        }
    }

    @Test
    fun `cipher rejects a wrong key length`() {
        assertThrows(IllegalArgumentException::class.java) { PairingCipher(ByteArray(24)) }
    }

    @Test
    fun `cipher counters are independent per direction like the handshake`() {
        // The real handshake encrypts client peer info with a fresh client
        // cipher (encrypt counter 0) and decrypts it with a fresh server
        // cipher (decrypt counter 0), then the reverse for the server info.
        val key = ByteArray(16) { 9 }
        val client = PairingCipher(key)
        val server = PairingCipher(key)
        try {
            val clientCert = "client-cert".toByteArray()
            val serverCert = "server-cert".toByteArray()
            assertArrayEquals(clientCert, server.decrypt(client.encrypt(clientCert)))
            assertArrayEquals(serverCert, client.decrypt(server.encrypt(serverCert)))
        } finally {
            client.destroy()
            server.destroy()
        }
    }

    // ------------------------------------------------------------- certificates

    @Test
    fun `self-signed cert parses and verifies with its key`() {
        val generator = java.security.KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val der =
            DerCertificates.selfSigned(
                pair.public as RSAPublicKey,
                pair.private,
                "Test",
            )
        val factory = CertificateFactory.getInstance("X.509")
        val cert =
            factory.generateCertificate(der.inputStream()) as java.security.cert.X509Certificate
        cert.verify(pair.public) // throws when the signature is invalid
        val sha256 = MessageDigest.getInstance("SHA-256").digest(der)
        assertEquals(32, sha256.size)
        assertTrue(cert.subjectX500Principal.name.contains("Test"))
    }

    @Test
    fun `self-signed cert public key matches the keypair`() {
        val generator = java.security.KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val der =
            DerCertificates.selfSigned(
                pair.public as RSAPublicKey,
                pair.private,
                "Test",
            )
        val factory = CertificateFactory.getInstance("X.509")
        val cert =
            factory.generateCertificate(der.inputStream()) as java.security.cert.X509Certificate
        assertArrayEquals(
            (pair.public as RSAPublicKey).encoded,
            (cert.publicKey as RSAPublicKey).encoded,
        )
        assertTrue(pair.private is RSAPrivateKey)
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
