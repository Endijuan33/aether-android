package id.mybase.aether.adb

import java.math.BigInteger

/**
 * Twisted Edwards curve Ed25519 arithmetic over GF(p), p = 2^255 - 19.
 *
 * Implemented on [BigInteger] for clarity and auditability rather than speed: a pairing handshake
 * performs only a handful of scalar multiplications, so the simplicity (and reviewability) of
 * big-integer field arithmetic wins over limb-based performance here. Not constant-time; SPAKE2
 * ephemerals are single-use randoms, which bounds the side-channel exposure.
 *
 * Follows RFC 8032 encodings: 32-byte little-endian y with the high bit carrying x parity.
 */
internal object Ed25519 {
    private val P =
        BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val L =
        BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)

    /** d = -121665 * inv(121666) mod p. */
    private val D = run {
        val neg121665 = P.subtract(BigInteger("121665"))
        neg121665.multiply(BigInteger("121666").modInverse(P)).mod(P)
    }

    /** Curve order, exposed for scalar generation. */
    val order: BigInteger
        get() = L

    /** sqrt(-1) mod p = 2^((p-1)/4), for the p = 5 (mod 8) square-root fixup. */
    private val SQRT_M1 =
        BigInteger.valueOf(2)
            .modPow(
                P.subtract(BigInteger.ONE).shiftRight(2),
                P,
            )

    data class Point(val x: BigInteger, val y: BigInteger)

    val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE)

    /** Standard base point, decoded from its RFC 8032 compressed encoding. */
    val BASE: Point =
        decode(
            hexToBytes("5866666666666666666666666666666666666666666666666666666666666666"),
        ) ?: throw IllegalStateException("Ed25519 base point failed to decode")

    /**
     * SPAKE2 blinding base M, the BoringSSL/AOSP value.
     *
     * Verified against BoringSSL's pinned source (crypto/curve25519/spake25519.cc, commit
     * 9e04aed2d1652441d2c84e85ed21acc05e053260) via the spake2-java oracle vectors: our
     * implementation reproduces BoringSSL's SPAKE2 messages byte-for-byte with this point. NOTE:
     * this point has order 2L, not L. BoringSSL compensates with the cofactor clearing (ephemeral
     * <<3, password-scalar hardening), which confines the effective masks to the prime-order
     * subgroup.
     */
    val SPAKE_M: Point =
        decode(
            hexToBytes("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"),
        ) ?: throw IllegalStateException("SPAKE2 M failed to decode")

    /** SPAKE2 blinding base N, the BoringSSL/AOSP value (order 8L; see [SPAKE_M]). */
    val SPAKE_N: Point =
        decode(
            hexToBytes("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"),
        ) ?: throw IllegalStateException("SPAKE2 N failed to decode")

    fun isIdentity(p: Point): Boolean = p.x.signum() == 0 && p.y == BigInteger.ONE

    fun negate(p: Point): Point = Point(P.subtract(p.x).mod(P), p.y)

    /**
     * Complete Edwards addition for the twisted curve with a = -1: x3 = (x1*y2 + x2*y1) / (1 +
     * d*x1*x2*y1*y2) y3 = (y1*y2 + x1*x2) / (1 - d*x1*x2*y1*y2) (The y3 numerator is a sum because
     * a = -1; the familiar minus form applies to a = +1 curves.)
     */
    fun add(a: Point, b: Point): Point {
        val x1 = a.x
        val y1 = a.y
        val x2 = b.x
        val y2 = b.y
        val x1x2 = x1.multiply(x2).mod(P)
        val y1y2 = y1.multiply(y2).mod(P)
        val dxy = D.multiply(x1x2).multiply(y1y2).mod(P)
        val x3 =
            x1.multiply(y2)
                .add(x2.multiply(y1))
                .mod(P)
                .multiply(BigInteger.ONE.add(dxy).modInverse(P))
                .mod(P)
        val y3 = y1y2.add(x1x2).mod(P).multiply(BigInteger.ONE.subtract(dxy).modInverse(P)).mod(P)
        return Point(x3, y3)
    }

    /** Double-and-add scalar multiplication (scalar reduced mod L). */
    fun scalarMult(scalar: BigInteger, point: Point): Point {
        var k = scalar.mod(L)
        var result = IDENTITY
        var addend = point
        repeat(256) {
            if (k.testBit(0)) result = add(result, addend)
            addend = add(addend, addend)
            k = k.shiftRight(1)
        }
        return result
    }

    /**
     * Raw 256-bit scalar multiplication WITHOUT modular reduction.
     *
     * BoringSSL multiplies the (possibly >= L) hardened password scalar against the non-prime-order
     * M/N points directly; reducing mod L first would change the result because L*M != identity.
     * This processes all 32 little-endian bytes as the scalar, preserving bit 255.
     */
    fun scalarMultRaw(scalar32: ByteArray, point: Point): Point {
        require(scalar32.size == 32) { "scalar must be 32 bytes" }
        var result = IDENTITY
        var addend = point
        // Iterate bits from LSB to MSB across the 32 LE bytes.
        for (byteIndex in 0 until 32) {
            var b = scalar32[byteIndex].toInt() and 0xFF
            repeat(8) {
                if (b and 1 != 0) result = add(result, addend)
                addend = add(addend, addend)
                b = b ushr 1
            }
        }
        return result
    }

    /** True when [p] lies in the prime-order subgroup (kills small-subgroup points). */
    fun isInSubgroup(p: Point): Boolean = isIdentity(scalarMult(L, p))

    fun encode(p: Point): ByteArray {
        val out = toLittleEndian(p.y, 32)
        if (p.x.testBit(0)) out[31] = (out[31].toInt() or 0x80).toByte()
        return out
    }

    /**
     * Decodes a compressed point, or null when the bytes do not encode a valid on-curve point.
     *
     * p = 5 (mod 8): the principal sqrt is x^((p+3)/8), which only works when x2 is a fourth-power
     * residue. Otherwise x^((p+3)/8) is sqrt(-x2) and we multiply by sqrt(-1) to obtain sqrt(x2)
     * (RFC 8032 §5.1.3).
     */
    fun decode(bytes: ByteArray): Point? {
        if (bytes.size != 32) return null
        val raw = bytes.copyOf()
        val xOdd = (raw[31].toInt() and 0x80) != 0
        raw[31] = (raw[31].toInt() and 0x7F).toByte()
        val y = fromLittleEndian(raw)
        if (y >= P) return null
        val y2 = y.multiply(y).mod(P)
        val numerator = y2.subtract(BigInteger.ONE).mod(P)
        val denominator = D.multiply(y2).add(BigInteger.ONE).mod(P)
        if (denominator.signum() == 0) return null
        val x2 = numerator.multiply(denominator.modInverse(P)).mod(P)
        var x = x2.modPow(P.add(BigInteger("3")).shiftRight(3), P)
        if (x.multiply(x).mod(P) != x2) {
            x = x.multiply(SQRT_M1).mod(P)
            if (x.multiply(x).mod(P) != x2) return null
        }
        if (x.testBit(0) != xOdd) x = P.subtract(x)
        return Point(x, y)
    }

    private fun toLittleEndian(v: BigInteger, size: Int): ByteArray {
        require(v.signum() >= 0) { "negative value" }
        val be = v.toByteArray() // big-endian two's complement, maybe with a leading zero
        val mag = if (be.size == size + 1 && be[0] == 0.toByte()) be.copyOfRange(1, be.size) else be
        require(mag.size <= size) { "value does not fit in $size bytes" }
        val out = ByteArray(size)
        for (i in mag.indices) out[i] = mag[mag.size - 1 - i]
        return out
    }

    private fun fromLittleEndian(bytes: ByteArray): BigInteger {
        val be = bytes.reversed().toByteArray()
        return BigInteger(1, be)
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
