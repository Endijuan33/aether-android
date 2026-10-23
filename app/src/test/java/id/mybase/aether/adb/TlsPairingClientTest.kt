package id.mybase.aether.adb

import id.mybase.aether.adb.fakes.FakeAdbKeyMaterial
import id.mybase.aether.adb.fakes.FakePairingServer
import java.net.ServerSocket
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the pairing client validates input before touching the network, maps transport outcomes to
 * the right [PairingResult], and completes a real TLS+SPAKE2+peer-info handshake against the in-JVM
 * fake server.
 */
class TlsPairingClientTest {

    private val exporterBytes = ByteArray(64) { 0xA5.toByte() }

    private fun client() =
        TlsPairingClient(
            FakeAdbKeyMaterial(),
            openChannel = { host, port, timeoutMs ->
                // Real TLS handshake against the fake server (which uses platform
                // TLS), but substitute fixed exporter bytes so the SPAKE2 password
                // matches the fake server's expectation. Uses BC (not Conscrypt)
                // because BC is pure-Java and works on JVM unit tests, while
                // Conscrypt Android requires Android native libraries.
                val real = BcTlsPairingTransport.openPairingChannel(host, port, timeoutMs)
                ConscryptPairingTransport.PairingTlsChannel(
                    input = real.input,
                    output = real.output,
                    exporter = exporterBytes.copyOf(),
                    closeAction = { real.close() },
                )
            },
        )

    /** A port nothing listens on: bind then close so connects are refused. */
    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `blank host is rejected without a socket`() = runTest {
        val result = client().pair("   ", 1234, "123456", 5_000)
        assertTrue(result is PairingResult.NetworkError)
    }

    @Test
    fun `port zero is rejected`() = runTest {
        val result = client().pair("127.0.0.1", 0, "123456", 5_000)
        assertTrue(result is PairingResult.NetworkError)
    }

    @Test
    fun `out-of-range port is rejected`() = runTest {
        val result = client().pair("127.0.0.1", 70_000, "123456", 5_000)
        assertTrue(result is PairingResult.NetworkError)
    }

    @Test
    fun `malformed code is rejected before any socket attempt`() = runTest {
        // Differential proof: the same closed port yields ConnectionRefused
        // for a well-formed code, so InvalidCode here means validation ran
        // before the socket was opened.
        val port = closedPort()
        val malformed = client().pair("127.0.0.1", port, "12345", 5_000)
        assertEquals(PairingResult.InvalidCode, malformed)
        val wellFormed = client().pair("127.0.0.1", port, "123456", 5_000)
        assertEquals(PairingResult.ConnectionRefused, wellFormed)
    }

    @Test
    fun `refused connection maps to ConnectionRefused`() = runTest {
        val result = client().pair("127.0.0.1", closedPort(), "123456", 5_000)
        assertEquals(PairingResult.ConnectionRefused, result)
    }

    @Test
    fun `wrong code maps to InvalidCode`() = runTest {
        val server = FakePairingServer(password = "654321", tlsExporterBytes = exporterBytes)
        try {
            val result = client().pair("127.0.0.1", server.port, "123456", 10_000)
            assertEquals(PairingResult.InvalidCode, result)
            assertTrue(server.sawWrongCode)
        } finally {
            server.close()
        }
    }

    @Test
    fun `correct code completes the handshake`() = runTest {
        val server = FakePairingServer(password = "123456", tlsExporterBytes = exporterBytes)
        try {
            val result = client().pair("127.0.0.1", server.port, "123456", 10_000)
            assertEquals(PairingResult.Success, result)
            assertTrue(server.completedOk)
            // The server received our 8192-byte peer-info struct with an RSA public key.
            val peerInfo = server.peerInfo
            assertTrue(peerInfo != null && peerInfo.size == PairingCrypto.PEER_INFO_SIZE)
            assertEquals(PairingCrypto.PEER_INFO_RSA_PUB_KEY, peerInfo!![0])
            // The embedded SPKI DER parses as an RSA public key
            // (RSA-2048 SPKI is 294 bytes).
            val spki = peerInfo.copyOfRange(1, 1 + 294)
            val publicKey =
                java.security.KeyFactory.getInstance("RSA")
                    .generatePublic(java.security.spec.X509EncodedKeySpec(spki))
            assertTrue(publicKey is java.security.interfaces.RSAPublicKey)
        } finally {
            server.close()
        }
    }

    @Test
    fun `code with separators is normalized and accepted`() = runTest {
        val server = FakePairingServer(password = "123456", tlsExporterBytes = exporterBytes)
        try {
            // filterPairingDigits strips the dashes before validation.
            val result = client().pair("127.0.0.1", server.port, "12-34-56", 10_000)
            assertEquals(PairingResult.Success, result)
        } finally {
            server.close()
        }
    }

    @Test
    fun `silent server maps to Timeout`() = runTest {
        val server =
            FakePairingServer(
                password = "123456",
                tlsExporterBytes = exporterBytes,
                hangBeforeSpakeResponse = true,
            )
        try {
            val result = client().pair("127.0.0.1", server.port, "123456", 1_000)
            assertEquals(PairingResult.Timeout, result)
        } finally {
            server.close()
        }
    }
}
