package id.mybase.aether.adb

import id.mybase.aether.security.InputValidators
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.crypto.AEADBadTagException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Wireless-debugging pairing client: the `adb pair` equivalent.
 *
 * Handshake (see docs/adb-pairing-protocol.md for sources):
 * 1. TLS to host:pairingPort via [BcTlsPairingTransport] (BouncyCastle — the platform Conscrypt's
 *    keying-material export is a hidden API, blocked for apps targeting recent SDKs). The server
 *    certificate is self-signed and not pre-trusted; the 6-digit code is the authentication.
 * 2. SPAKE2 exchange, framed per [PairingProtocol] (client = Alice). The SPAKE2 password is the
 *    pairing-code bytes concatenated with 64 bytes of TLS keying material exported with label
 *    "adb-label\0" (channel binding; without it, a MITM on the TLS connection could forward the
 *    pairing).
 * 3. HKDF-SHA256 over the SPAKE2 output → AES-128-GCM peer-info key.
 * 4. Client sends its peer info (8192-byte struct: type + RSA public key), then reads the server's
 *    peer info. A server that derived a different key (wrong code) cannot decrypt our peer info and
 *    drops the connection, which surfaces here as [PairingResult.InvalidCode].
 *
 * Error mapping:
 * - malformed code (not 6 digits after digit filtering) → [PairingResult.InvalidCode] without
 *   opening any socket;
 * - TCP refused → [PairingResult.ConnectionRefused];
 * - [timeoutMs] elapsed → [PairingResult.Timeout];
 * - anything else transport-shaped → [PairingResult.NetworkError].
 *
 * Success is never faked: it requires the server's encrypted peer info to decrypt and parse.
 *
 * @param openChannel opens the TLS pairing channel. Defaults to [BcTlsPairingTransport]; tests
 *   inject a fake.
 */
class TlsPairingClient(
    private val keys: AdbKeyMaterial,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val openChannel:
        (
            host: String,
            port: Int,
            timeoutMs: Int,
        ) -> BcTlsPairingTransport.PairingTlsChannel =
        BcTlsPairingTransport::openPairingChannel,
) : AdbPairingClient {

    override suspend fun pair(
        host: String,
        port: Int,
        pairingCode: String,
        timeoutMs: Long,
    ): PairingResult =
        withContext(ioDispatcher) {
            val cleanHost = host.trim()
            if (!InputValidators.isValidHost(cleanHost)) {
                return@withContext PairingResult.NetworkError("Invalid host")
            }
            if (!InputValidators.isValidPort(port)) {
                return@withContext PairingResult.NetworkError("Invalid port: $port")
            }
            val digits = InputValidators.filterPairingDigits(pairingCode)
            if (!InputValidators.isValidPairingCode(digits)) {
                return@withContext PairingResult.InvalidCode
            }
            try {
                withTimeout(timeoutMs) { doPair(cleanHost, port, digits) }
            } catch (e: TimeoutCancellationException) {
                PairingResult.Timeout
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // doPair maps every expected failure to a PairingResult; this is
                // a safety net for the unexpected.
                PairingResult.NetworkError(
                    "Pairing failed: ${e.message?.redactSecrets() ?: e.javaClass.simpleName}",
                )
            }
        }

    private suspend fun doPair(host: String, port: Int, code: String): PairingResult {
        val channel =
            try {
                openChannel(host, port, CONNECT_TIMEOUT_MS)
            } catch (e: ConnectException) {
                return PairingResult.ConnectionRefused
            } catch (e: UnknownHostException) {
                return PairingResult.NetworkError("Unknown host")
            } catch (e: SocketTimeoutException) {
                return PairingResult.Timeout
            } catch (e: SSLException) {
                return PairingResult.NetworkError("TLS handshake with pairing endpoint failed")
            } catch (e: java.io.IOException) {
                if (currentJob()?.isCancelled == true)
                    throw CancellationException("Pairing cancelled")
                return PairingResult.NetworkError("Could not reach pairing endpoint")
            }
        try {
            return handshake(channel, code)
        } finally {
            runCatching { channel.close() }
        }
    }

    private suspend fun handshake(
        channel: BcTlsPairingTransport.PairingTlsChannel,
        code: String,
    ): PairingResult {
        val out = channel.output
        val input = channel.input
        try {
            // 1. SPAKE2 key exchange. Password = code || TLS exporter (channel binding).
            // The exporter was captured during the BC TLS handshake
            // (see BcTlsPairingTransport).
            val exporterBytes = channel.exporter
            if (exporterBytes.size != PairingCrypto.TLS_EXPORTER_LENGTH) {
                return PairingResult.NetworkError("TLS keying material export failed")
            }
            val passwordMaterial = code.toByteArray(Charsets.UTF_8) + exporterBytes
            val spake = Spake2Alice(passwordMaterial)
            passwordMaterial.fill(0)
            try {
                PairingProtocol.writeFrame(
                    out,
                    PairingProtocol.TYPE_SPAKE2,
                    spake.generateMessage(),
                )
            } catch (e: java.io.IOException) {
                return PairingResult.NetworkError(
                    "Pairing I/O error [write SPAKE2]: ${e.javaClass.simpleName}: ${e.message}",
                )
            }
            val serverSpake =
                try {
                    PairingProtocol.readFrame(input)
                } catch (e: SocketTimeoutException) {
                    throw e // Let the outer handler map this to Timeout.
                } catch (e: java.io.IOException) {
                    return PairingResult.NetworkError(
                        "Pairing I/O error [read SPAKE2]: ${e.javaClass.simpleName}: ${e.message}",
                    )
                }
            if (serverSpake.type != PairingProtocol.TYPE_SPAKE2) {
                return PairingResult.NetworkError("Unexpected pairing message from server")
            }
            val keyMaterial =
                try {
                    spake.deriveKeyMaterial(serverSpake.payload)
                } catch (e: Spake2Exception) {
                    return PairingResult.InvalidCode
                }
            // 2. Peer-info key.
            val aesKey = HkdfSha256.derive(keyMaterial, null, PairingCrypto.HKDF_INFO, 16)
            keyMaterial.fill(0)
            val cipher = PairingCipher(aesKey)
            aesKey.fill(0)
            try {
                // 3. Our peer info first (8192-byte struct), then the server's response.
                PairingProtocol.writeFrame(
                    out,
                    PairingProtocol.TYPE_PEER_INFO,
                    cipher.encrypt(buildPeerInfo()),
                )
                val serverInfo =
                    try {
                        PairingProtocol.readFrame(input)
                    } catch (e: EOFException) {
                        // The server derived a different key, could not
                        // decrypt our peer info, and dropped the connection:
                        // the code was wrong.
                        return PairingResult.InvalidCode
                    }
                if (serverInfo.type != PairingProtocol.TYPE_PEER_INFO) {
                    return PairingResult.NetworkError("Unexpected pairing message from server")
                }
                val serverPeerInfo =
                    try {
                        cipher.decrypt(serverInfo.payload)
                    } catch (e: AEADBadTagException) {
                        return PairingResult.InvalidCode
                    }
                if (!isPlausiblePeerInfo(serverPeerInfo)) {
                    return PairingResult.NetworkError("Server peer info malformed")
                }
                return PairingResult.Success
            } finally {
                cipher.destroy()
            }
        } catch (e: SocketTimeoutException) {
            return PairingResult.Timeout
        } catch (e: EOFException) {
            return PairingResult.NetworkError("Pairing connection closed unexpectedly")
        } catch (e: PairingProtocolException) {
            return PairingResult.NetworkError("Pairing protocol error")
        } catch (e: SSLException) {
            return PairingResult.NetworkError("TLS error during pairing")
        } catch (e: java.io.IOException) {
            // Cancellation closes the socket via withSocketGuard, which turns
            // into an IOException here: report cancellation, not a failure.
            if (currentJob()?.isCancelled == true) throw CancellationException("Pairing cancelled")
            // Include the concrete failure for on-device diagnostics (no secrets here:
            // just the exception class and message).
            return PairingResult.NetworkError(
                "Pairing I/O error: ${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    /**
     * Builds our 8192-byte peer-info struct: type ADB_RSA_PUB_KEY (0) followed by the RSA public
     * key in X.509 SPKI DER, zero-padded.
     *
     * PENDING HARDWARE VALIDATION: AOSP's exact expected public-key encoding inside the peer-info
     * data field is unverified from public sources; SPKI DER is the standard encoding and the most
     * likely accepted form.
     */
    private fun buildPeerInfo(): ByteArray {
        val struct = ByteArray(PairingCrypto.PEER_INFO_SIZE)
        struct[0] = PairingCrypto.PEER_INFO_RSA_PUB_KEY
        val spki = keys.getOrCreateKeyPair().public.encoded
        require(spki.size <= PairingCrypto.PEER_INFO_SIZE - 1) {
            "RSA public key too large for peer info"
        }
        spki.copyInto(struct, 1)
        return struct
    }

    private fun isPlausiblePeerInfo(info: ByteArray): Boolean {
        if (info.size != PairingCrypto.PEER_INFO_SIZE) return false
        // Type 0 = RSA public key, type 1 = device GUID; anything else is bogus.
        return info[0] == PairingCrypto.PEER_INFO_RSA_PUB_KEY ||
            info[0] == PairingCrypto.PEER_INFO_DEVICE_GUID
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
    }
}
