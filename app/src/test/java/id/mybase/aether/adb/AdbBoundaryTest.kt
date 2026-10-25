package id.mybase.aether.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class AdbBoundaryTest {
    @Test
    fun `public key contains AOSP fields and Montgomery parameters`() {
        val key =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
                as RSAPublicKey
        val encoded = AdbPublicKey.encode(key)
        assertEquals(0, encoded.last().toInt())
        val binary =
            Base64.getDecoder().decode(encoded.toString(Charsets.US_ASCII).substringBefore(' '))
        assertEquals(524, binary.size)
        val words = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, words.int)
        val inverse = words.int.toLong() and 0xffffffffL
        val modulus = BigInteger(1, binary.copyOfRange(8, 264).reversedArray())
        val rr = BigInteger(1, binary.copyOfRange(264, 520).reversedArray())
        assertEquals(key.modulus, modulus)
        val base = BigInteger.ONE.shiftLeft(32)
        assertEquals(
            base.subtract(BigInteger.ONE),
            modulus.multiply(BigInteger.valueOf(inverse)).mod(base),
        )
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(modulus), rr)
        assertEquals(65537, words.getInt(520))
    }

    @Test
    fun `only complete allowlisted command forms are accepted`() {
        val valid =
            listOf(
                listOf("id", "-u"),
                listOf("pm", "list", "packages"),
                listOf("pm", "list", "packages", "-d", "--user", "0", "com.example.app"),
                listOf("pm", "uninstall", "--user", "0", "com.example.app"),
                listOf("pm", "enable", "--user", "0", "com.example.app"),
            )
        valid.forEach { assertEquals(it, AdbConnectionManager.validateArgs(it.toTypedArray())) }
        val invalid =
            listOf(
                listOf("pm", "clear", "com.example.app"),
                listOf("cmd", "package", "uninstall", "com.example.app"),
                listOf("pm", "uninstall", "--user", "1", "com.example.app"),
                listOf("pm", "enable", "com.example.app"),
                listOf("pm", "uninstall", "--user", "0", "com.example.app>file"),
                listOf("pm", "uninstall", "--user", "0", "com.example.app\nreboot"),
                listOf("pm", "uninstall", "--user", "0", "com.example.app", "--versionCode", "1"),
            )
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) {
                AdbConnectionManager.validateArgs(it.toTypedArray())
            }
        }
    }

    @Test
    fun `local endpoints accept loopback and reject remote device`() {
        assertTrue(localAdbEndpoint("127.0.0.1", 37123).address.isLoopbackAddress)
        assertThrows(IllegalArgumentException::class.java) {
            localAdbEndpoint("192.0.2.123", 37123)
        }
    }
}
