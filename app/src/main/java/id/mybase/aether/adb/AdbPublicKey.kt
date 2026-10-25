package id.mybase.aether.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/** AOSP libcrypto_utils/android_pubkey.cpp: 64-word RSA key, not X.509 SPKI. */
internal object AdbPublicKey {
    fun encode(key: RSAPublicKey): ByteArray {
        require(key.modulus.bitLength() == 2048) { "ADB requires an RSA-2048 key" }
        require(key.publicExponent == BigInteger.valueOf(65537)) { "Unsupported RSA exponent" }
        val wordBase = BigInteger.ONE.shiftLeft(32)
        val n0inv = wordBase.subtract(key.modulus.mod(wordBase).modInverse(wordBase))
        val rr = BigInteger.ONE.shiftLeft(4096).mod(key.modulus)
        val binary = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        binary.putInt(64)
        binary.putInt(n0inv.toInt())
        binary.put(littleEndian(key.modulus))
        binary.put(littleEndian(rr))
        binary.putInt(65537)
        return (Base64.getEncoder().encodeToString(binary.array()) + " aether@android\u0000")
            .toByteArray(Charsets.US_ASCII)
    }

    private fun littleEndian(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().reversedArray()
        return bytes.copyOf(256)
    }
}
