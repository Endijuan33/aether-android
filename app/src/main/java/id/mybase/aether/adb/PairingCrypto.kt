package id.mybase.aether.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic core of the wireless-debugging pairing handshake.
 *
 * The SPAKE2 construction replicates BoringSSL's SPAKE25519 (crypto/curve25519/spake25519.cc) as
 * used by AOSP's pairing_auth; it was verified byte-for-byte against BoringSSL's deterministic
 * oracle vectors (spake2-java/src/test/resources/oracle-vectors/boringssl-spake2-vectors.json): our
 * implementation reproduces BoringSSL's SPAKE2 messages and session keys exactly for the published
 * test cases. See docs/adb-pairing-protocol.md for primary sources.
 *
 * Verified:
 * - SPAKE2 identities: client "adb pair client\0", server "adb pair server\0" (NUL terminator
 *   included, matching AOSP's sizeof() on the C literals).
 * - Client plays the Alice (prover) role.
 * - Blinding bases M/N are BoringSSL's (NOT RFC 9382's); M has order 2L and N has order 8L.
 *   Cofactor clearing (ephemeral <<3, password-scalar hardening) confines the effective masks to
 *   the prime-order subgroup.
 * - Password material: the pairing-code bytes concatenated with 64 bytes of TLS keying material
 *   exported with label "adb-label\0" (channel binding; per Kadb, verified against AOSP's pairing
 *   flow).
 * - Session key material: SHA-512 over the length-prefixed (8-byte LE) transcript: myName,
 *   theirName, myMsg, peerMsg, dhSharedEncoded, passwordHash(SHA-512 of the password material).
 * - HKDF-SHA256 with null salt, info "adb pairing_auth aes-128-gcm key", 16 bytes of output for the
 *   AES-128 key.
 * - Peer-info messages are AES-128-GCM with a 12-byte IV formed as
 *   [message counter, 8 bytes little-endian][4 zero bytes]; each direction keeps its own counter
 *   starting at zero.
 * - Peer info is a fixed 8192-byte struct: 1 type byte + 8191 data bytes (AOSP
 *   pairing_connection.h: kMaxPeerInfoSize = 8192).
 */
internal object PairingCrypto {
    /** HKDF info string binding the derived key to the ADB pairing context. */
    val HKDF_INFO = "adb pairing_auth aes-128-gcm key".toByteArray(Charsets.UTF_8)

    /** Pairing identity strings, NUL-terminated exactly as AOSP defines them. */
    val CLIENT_NAME = "adb pair client\u0000".toByteArray(Charsets.UTF_8)
    val SERVER_NAME = "adb pair server\u0000".toByteArray(Charsets.UTF_8)

    /** TLS exporter label for the pairing channel binding (NUL-terminated). */
    val TLS_EXPORTER_LABEL = "adb-label\u0000".toByteArray(Charsets.UTF_8)

    /** Bytes of TLS keying material mixed into the SPAKE2 password. */
    const val TLS_EXPORTER_LENGTH = 64

    /** Fixed peer-info struct size (AOSP kMaxPeerInfoSize). */
    const val PEER_INFO_SIZE = 8192

    /** Peer-info types (AOSP pairing_connection.h). */
    const val PEER_INFO_RSA_PUB_KEY: Byte = 0
    const val PEER_INFO_DEVICE_GUID: Byte = 1
}

/**
 * Cryptographic core of the wireless-debugging pairing handshake.
 *
 * (See the [PairingCrypto] KDoc above for the verified parameter provenance.)
 */

/** Thrown when a SPAKE2 peer message is invalid (also signals a wrong code downstream). */
internal class Spake2Exception(message: String) : Exception(message)

/** 32-byte little-endian helpers for the SPAKE2 scalar arithmetic (mod 2^256). */
private fun ByteArray.addInPlaceLE(other: ByteArray) {
    require(size == 32 && other.size == 32)
    var carry = 0
    for (i in 0 until 32) {
        val sum = (this[i].toInt() and 0xFF) + (other[i].toInt() and 0xFF) + carry
        this[i] = sum.toByte()
        carry = sum ushr 8
    }
}

private fun ByteArray.doubledLE(): ByteArray {
    require(size == 32)
    val out = ByteArray(32)
    var carry = 0
    for (i in 0 until 32) {
        val b = this[i].toInt() and 0xFF
        out[i] = ((b shl 1) or carry).toByte()
        carry = b ushr 7
    }
    return out
}

private fun BigInteger.toLe32(): ByteArray {
    val be = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
    require(be.size <= 32) { "value does not fit in 32 bytes" }
    val out = ByteArray(32)
    for (i in be.indices) out[i] = be[be.size - 1 - i]
    return out
}

/**
 * SPAKE2 password-authenticated key exchange, BoringSSL SPAKE25519 construction, client (Alice)
 * role.
 *
 * Message: X = x*G + w*M where x = (reduce64(rand) << 3) and w =
 * harden(reduce64(SHA-512(passwordMaterial))). Key material: SHA-512 over the length-prefixed
 * transcript (myName, theirName, myMsg, peerMsg, dhSharedEncoded, passwordHash).
 *
 * @param passwordMaterial the pairing-code bytes concatenated with the 64 TLS exporter bytes
 *   (channel binding). NOT just the 6-digit code.
 * @param ephemeralInput64 optional 64-byte deterministic ephemeral input (test only; production
 *   passes null and uses [random]).
 */
internal open class Spake2Alice(
    passwordMaterial: ByteArray,
    ephemeralInput64: ByteArray? = null,
    random: SecureRandom = SecureRandom(),
) {
    /** 32-byte LE ephemeral scalar: reduceModL(input) << 3. Never zero. */
    private val xBytes: ByteArray =
        ephemeralScalar(ephemeralInput64 ?: ByteArray(64).also(random::nextBytes))

    /** 64-byte SHA-512(passwordMaterial), also hashed into the transcript. */
    protected val passwordHash: ByteArray = run {
        require(passwordMaterial.isNotEmpty()) { "empty SPAKE2 password material" }
        MessageDigest.getInstance("SHA-512").digest(passwordMaterial)
    }

    /** 32-byte LE hardened password scalar. */
    protected val wBytes: ByteArray = passwordScalar(passwordHash)

    /** Our SPAKE2 wire message, set by [generateMessage]. */
    protected var myMsg: ByteArray? = null

    /** Our public SPAKE2 element X = x*G + w*M, 32 bytes. */
    fun generateMessage(): ByteArray {
        val blinded =
            Ed25519.add(
                Ed25519.scalarMultRaw(xBytes, Ed25519.BASE),
                Ed25519.scalarMultRaw(wBytes, Ed25519.SPAKE_M),
            )
        return Ed25519.encode(blinded).also { myMsg = it }
    }

    /**
     * Processes the server's element and returns the 64-byte session key material (SHA-512
     * transcript hash).
     *
     * Mirrors BoringSSL/AOSP: the peer message must decode to an on-curve point, but
     * identity-after-unmasking is NOT rejected.
     */
    open fun deriveKeyMaterial(peerMessage: ByteArray): ByteArray {
        val ourMsg = myMsg ?: throw IllegalStateException("generateMessage() must be called first")
        val qStar =
            Ed25519.decode(peerMessage)
                ?: throw Spake2Exception("peer SPAKE2 element does not decode")
        val peerMask = Ed25519.scalarMultRaw(wBytes, Ed25519.SPAKE_N)
        val q = Ed25519.add(qStar, Ed25519.negate(peerMask))
        val dh = Ed25519.scalarMultRaw(xBytes, q)
        val dhEncoded = Ed25519.encode(dh)
        return transcriptHash(
            PairingCrypto.CLIENT_NAME,
            PairingCrypto.SERVER_NAME,
            ourMsg,
            peerMessage,
            dhEncoded,
            passwordHash,
        )
    }

    companion object {
        /** reduceModL(64B) << 3: the BoringSSL ephemeral scalar. */
        fun ephemeralScalar(input64: ByteArray): ByteArray {
            require(input64.size == 64) { "ephemeral input must be 64 bytes" }
            return leftShift3(reduceModL(input64))
        }

        /** SHA-512(passwordMaterial) reduced mod L, then BoringSSL-hardened. */
        fun passwordScalar(passwordHash64: ByteArray): ByteArray {
            require(passwordHash64.size == 64) { "password hash must be 64 bytes" }
            return hardenPasswordScalar(reduceModL(passwordHash64))
        }

        /** Interprets 64 bytes as a little-endian integer and reduces mod L. */
        fun reduceModL(bytes64: ByteArray): ByteArray {
            require(bytes64.size == 64)
            val be = bytes64.reversedArray()
            return BigInteger(1, be).mod(Ed25519.order).toLe32()
        }

        /** 3-bit left shift on a 32-byte LE scalar, dropping overflow (mod 2^256). */
        fun leftShift3(scalar32: ByteArray): ByteArray {
            require(scalar32.size == 32)
            val out = ByteArray(32)
            var carry = 0
            for (i in 0 until 32) {
                val b = scalar32[i].toInt() and 0xFF
                out[i] = ((b shl 3) or carry).toByte()
                carry = b ushr 5
            }
            return out
        }

        /**
         * BoringSSL's password-scalar hardening: clears the low three bits by adding L, 2L, 4L
         * according to the (updated) low bits, all mod 2^256. Byte-exact replication of BoringSSL's
         * compatibility hack.
         */
        fun hardenPasswordScalar(reduced32: ByteArray): ByteArray {
            require(reduced32.size == 32)
            val s = reduced32.copyOf()
            var order = Ed25519.order.toLe32()
            if (s[0].toInt() and 1 != 0) s.addInPlaceLE(order)
            order = order.doubledLE()
            if (s[0].toInt() and 2 != 0) s.addInPlaceLE(order)
            order = order.doubledLE()
            if (s[0].toInt() and 4 != 0) s.addInPlaceLE(order)
            return s
        }

        /**
         * BoringSSL session-key transcript hash: SHA-512 over 8-byte-LE length-prefixed myName,
         * theirName, myMsg, peerMsg, dhSharedEncoded, passwordHash. Verified byte-for-byte against
         * BoringSSL's oracle.
         */
        fun transcriptHash(
            myName: ByteArray,
            theirName: ByteArray,
            myMsg: ByteArray,
            peerMsg: ByteArray,
            dhSharedEncoded: ByteArray,
            passwordHash: ByteArray,
        ): ByteArray {
            val md = MessageDigest.getInstance("SHA-512")
            fun lenPrefixed(data: ByteArray) {
                md.update(
                    ByteBuffer.allocate(8)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putLong(data.size.toLong())
                        .array(),
                )
                md.update(data)
            }
            lenPrefixed(myName)
            lenPrefixed(theirName)
            lenPrefixed(myMsg)
            lenPrefixed(peerMsg)
            lenPrefixed(dhSharedEncoded)
            lenPrefixed(passwordHash)
            return md.digest()
        }
    }
}

/**
 * SPAKE2 server (Bob) role. Used by the in-JVM fake pairing server in tests; mirrors [Spake2Alice]
 * with the M/N blinding factors swapped and the transcript in canonical (client-first) order.
 */
internal class Spake2Bob(
    passwordMaterial: ByteArray,
    random: SecureRandom = SecureRandom(),
) : Spake2Alice(passwordMaterial, null, random) {
    private val yBytes: ByteArray = ephemeralScalar(ByteArray(64).also(random::nextBytes))

    fun generateServerMessage(): ByteArray {
        val blinded =
            Ed25519.add(
                Ed25519.scalarMultRaw(yBytes, Ed25519.BASE),
                Ed25519.scalarMultRaw(wBytes, Ed25519.SPAKE_N),
            )
        return Ed25519.encode(blinded).also { myMsg = it }
    }

    override fun deriveKeyMaterial(peerMessage: ByteArray): ByteArray {
        val ourMsg =
            myMsg ?: throw IllegalStateException("generateServerMessage() must be called first")
        val pStar =
            Ed25519.decode(peerMessage)
                ?: throw Spake2Exception("peer SPAKE2 element does not decode")
        val peerMask = Ed25519.scalarMultRaw(wBytes, Ed25519.SPAKE_M)
        val p = Ed25519.add(pStar, Ed25519.negate(peerMask))
        val dh = Ed25519.scalarMultRaw(yBytes, p)
        val dhEncoded = Ed25519.encode(dh)
        // Canonical order: client name/msg first regardless of role.
        return transcriptHash(
            PairingCrypto.CLIENT_NAME,
            PairingCrypto.SERVER_NAME,
            peerMessage,
            ourMsg,
            dhEncoded,
            passwordHash,
        )
    }
}

/** HKDF-SHA256 per RFC 5869 (null salt = HashLen zero bytes, per the RFC). */
internal object HkdfSha256 {
    fun derive(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32) { "invalid HKDF length: $length" }
        val realSalt = salt ?: ByteArray(32)
        val prk = hmac(realSalt, ikm)
        val okm = ByteArray(length)
        var previous = ByteArray(0)
        var produced = 0
        var counter = 1
        while (produced < length) {
            previous = hmac(prk, previous + info + counter.toByte())
            val take = minOf(previous.size, length - produced)
            previous.copyInto(okm, produced, 0, take)
            produced += take
            counter++
        }
        return okm
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

/**
 * AES-128-GCM for pairing peer-info messages.
 *
 * IV construction (per implementation-derived documentation): the message counter as 8 bytes
 * little-endian followed by 4 zero bytes. Encryption and decryption keep independent counters, both
 * starting at zero; the peer's encryption counter and our decryption counter advance in lockstep
 * because each side encrypts in a fixed order.
 */
internal class PairingCipher(keyMaterial: ByteArray) {
    private val key = keyMaterial.copyOf()
    private var encCounter = 0L
    private var decCounter = 0L
    private var destroyed = false

    init {
        require(keyMaterial.size == 16) { "AES-128 key must be 16 bytes" }
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        checkUsable()
        return doCipher(Cipher.ENCRYPT_MODE, ivFor(encCounter++), plaintext)
    }

    /** Throws [javax.crypto.AEADBadTagException] when authentication fails. */
    fun decrypt(ciphertext: ByteArray): ByteArray {
        checkUsable()
        return doCipher(Cipher.DECRYPT_MODE, ivFor(decCounter++), ciphertext)
    }

    fun destroy() {
        destroyed = true
        key.fill(0)
    }

    private fun checkUsable() = check(!destroyed) { "pairing cipher destroyed" }

    private fun doCipher(mode: Int, iv: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(input)
    }

    companion object {
        fun ivFor(counter: Long): ByteArray =
            ByteBuffer.allocate(12)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(counter)
                .putInt(0)
                .array()
    }
}

/**
 * Minimal DER builder for a self-signed X.509 v3 RSA certificate.
 *
 * Used by the file-backed key store (and test fakes) to mint the certificate that wraps the RSA
 * public key for the pairing peer-info exchange and the TLS client authentication. The
 * AndroidKeyStore-backed store instead lets the platform generate this certificate. Output is
 * verified in tests by parsing it with the JCA [java.security.cert.CertificateFactory] and checking
 * the signature.
 */
internal object DerCertificates {
    private val OID_RSA_ENCRYPTION = intArrayOf(1, 2, 840, 113549, 1, 1, 1)
    private val OID_SHA256_WITH_RSA = intArrayOf(1, 2, 840, 113549, 1, 1, 11)
    private val OID_COMMON_NAME = intArrayOf(2, 5, 4, 3)

    /**
     * Builds a self-signed certificate for [publicKey] signed by [signer]. Validity is
     * [validityDays] from now. Subject/issuer are "CN=[commonName]".
     */
    fun selfSigned(
        publicKey: RSAPublicKey,
        signer: java.security.PrivateKey,
        commonName: String = "AETHER ADB Pairing",
        validityDays: Long = 25 * 365,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val serial = ByteArray(8).also(random::nextBytes)
        serial[0] = (serial[0].toInt() and 0x7F).toByte() // keep positive
        if (serial.all { it == 0.toByte() }) serial[7] = 1

        val sigAlgId = sequence(oid(OID_SHA256_WITH_RSA), null_())
        val now = Instant.now()
        val validity =
            sequence(
                generalizedTime(now),
                generalizedTime(now.plusSeconds(validityDays * 24 * 3600)),
            )
        val name = sequence(set(sequence(oid(OID_COMMON_NAME), utf8String(commonName))))
        val rsaPub =
            sequence(
                integer(publicKey.modulus.toByteArray()),
                integer(publicKey.publicExponent.toByteArray()),
            )
        val spki =
            sequence(
                sequence(oid(OID_RSA_ENCRYPTION), null_()),
                bitString(rsaPub),
            )
        val tbs =
            sequence(
                explicit(0, integer(byteArrayOf(2))), // v3
                integer(serial),
                sigAlgId,
                name, // issuer
                validity,
                name, // subject
                spki,
            )
        val signature =
            Signature.getInstance("SHA256withRSA").run {
                initSign(signer)
                update(tbs)
                sign()
            }
        return sequence(tbs, sigAlgId, bitString(signature))
    }

    // ---- DER primitives ----

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val len = content.size
        val lenBytes =
            if (len < 128) {
                byteArrayOf(len.toByte())
            } else {
                val be =
                    BigInteger.valueOf(len.toLong())
                        .toByteArray()
                        .dropWhile { it == 0.toByte() }
                        .toByteArray()
                byteArrayOf((0x80 or be.size).toByte()) + be
            }
        return byteArrayOf(tag.toByte()) + lenBytes + content
    }

    private fun sequence(vararg parts: ByteArray) = tlv(0x30, parts.reduce { a, b -> a + b })

    private fun set(vararg parts: ByteArray) = tlv(0x31, parts.reduce { a, b -> a + b })

    private fun explicit(tagNo: Int, content: ByteArray) = tlv(0xA0 or tagNo, content)

    private fun integer(content: ByteArray): ByteArray {
        var c = content.dropWhile { it == 0.toByte() }.toByteArray()
        if (c.isEmpty()) c = byteArrayOf(0)
        if (c[0].toInt() and 0x80 != 0) c = byteArrayOf(0) + c
        return tlv(0x02, c)
    }

    private fun oid(arcs: IntArray): ByteArray {
        require(arcs.size >= 2)
        val body = mutableListOf<Byte>()
        body.add((40 * arcs[0] + arcs[1]).toByte())
        for (arc in arcs.drop(2)) {
            var v = arc
            val stack = ArrayDeque<Byte>()
            stack.addFirst((v and 0x7F).toByte())
            v = v ushr 7
            while (v > 0) {
                stack.addFirst(((v and 0x7F) or 0x80).toByte())
                v = v ushr 7
            }
            body.addAll(stack)
        }
        return tlv(0x06, body.toByteArray())
    }

    private fun utf8String(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))

    private fun null_() = tlv(0x05, ByteArray(0))

    private fun bitString(content: ByteArray) = tlv(0x03, byteArrayOf(0) + content)

    private val timeFormat =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC)

    private fun generalizedTime(instant: Instant) =
        tlv(0x18, timeFormat.format(instant).toByteArray(Charsets.US_ASCII))
}
