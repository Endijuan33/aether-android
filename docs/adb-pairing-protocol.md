# ADB Wireless-Debugging Pairing Protocol

Research findings for Project AETHER's `adb pair` implementation (Android 11+).
All parameters below were verified against primary sources; the implementation
in `app/src/main/java/id/mybase/aether/adb/` reproduces BoringSSL's behavior
byte-for-byte against its deterministic oracle vectors.

## Sources

- AOSP `platform/packages/modules/adb/pairing_auth/pairing_auth.cpp` — SPAKE2
  via BoringSSL (`SPAKE2_CTX_new/generate_msg/process_msg`), identities
  `"adb pair client"` / `"adb pair server"` with `sizeof()` **including NUL**
  (16 bytes each).
- AOSP `platform/packages/modules/adb/pairing_auth/aes_128_gcm.cpp` —
  `HKDF(key,16,EVP_sha256(),key_material,len,nullptr,0,"adb pairing_auth aes-128-gcm key",31)`;
  12-byte nonce = LE64 sequence counter + 4 zero bytes; separate enc/dec sequences.
- AOSP `platform/packages/modules/adb/pairing_connection/include/adb/pairing/pairing_connection.h` —
  `PeerInfo` is a fixed 8192-byte struct (`type:1B` + `data[8191]`);
  types `ADB_RSA_PUB_KEY=0`, `ADB_DEVICE_GUID=1`.
- BoringSSL `crypto/curve25519/spake25519.cc` (commit 9e04aed2d1652441d2c84e85ed21acc05e053260) —
  the SPAKE25519 construction, verified via deterministic oracle vectors.
- Kadb (Apache-2.0) — TLS exporter label `"adb-label\0"`, 64 bytes;
  password material = `pairing_code_bytes || tls_exporter_64B` (channel binding);
  header length field is big-endian; client sends peer info first.
- spake2-java (Flyfish233, GPL-3.0 — read-only reference, never copied) —
  confirmed the group is Edwards25519 and the BoringSSL M/N values.

## SPAKE2 (BoringSSL SPAKE25519)

**Do NOT use RFC 9382's M/N values.** BoringSSL uses its own blinding bases:
- M = `5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e` (order 2L)
- N = `10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778` (order 8L)

(RFC 9382's edwards25519 M table value does not decode per RFC 8032; verified twice.)

### Message generation (Alice = `adb pair` client)

1. Ephemeral: 64 random bytes → reduce mod L → left-shift 3 bits (×8 cofactor, mod 2^256, bit 255 preserved) → `x`.
2. `X = x*G` (raw 256-bit scalar mult, no mod-L reduction).
3. Password: `pwHash = SHA-512(passwordMaterial)` (64 bytes).
4. `w = reduceModL(pwHash)` → **harden**: if bit0 set, `w += L`; if bit1 set, `w += 2L`; if bit2 set, `w += 4L` (all mod 2^256, bits checked on the updated value — BoringSSL compatibility hack, clears low 3 bits).
5. Mask: `w*M` (raw scalar mult — **do NOT reduce w mod L first**, because L*M ≠ identity).
6. Message: `encode(X + w*M)` (32 bytes).

### Key derivation

1. Decode peer message (must be on-curve; AOSP does **not** reject identity-after-unmasking).
2. `Q = peerMsg − w*N` (Alice; Bob uses `w*M`).
3. `DH = x*Q`, `dhEnc = encode(DH)` (32 bytes).
4. `keyMaterial = SHA-512(len8le(myName) || myName || len8le(theirName) || theirName || len8le(myMsg) || myMsg || len8le(peerMsg) || peerMsg || len8le(dhEnc) || dhEnc || len8le(pwHash) || pwHash)` (64 bytes).
   - Alice: myName=`"adb pair client\0"`, theirName=`"adb pair server\0"`, myMsg=our message, peerMsg=their message.
   - Bob: canonical order is client-first regardless of role.

### Password material (channel binding)

`passwordMaterial = pairing_code_bytes || TLS_exporter("adb-label\0", 64 bytes)`.

The 6-digit code alone is **not** the SPAKE2 password. The TLS exporter binds the
pairing to the specific TLS connection, preventing MITM forwarding. Production uses standalone Conscrypt 2.8.0 via its public `Conscrypt.exportKeyingMaterial`; framework hidden APIs are not used. BouncyCastle is confined to JVM test fixtures.
Verified against BoringSSL oracle: `passwordHex=353135313039` is ASCII `"515109"`.

### Peer-info key

`aesKey = HKDF-SHA256(keyMaterial, salt=null→32 zeros, info="adb pairing_auth aes-128-gcm key", 16)`.

## Peer-info exchange

After SPAKE2, both sides exchange AES-128-GCM-encrypted peer infos (IV = LE64 counter + 4 zeros, independent counters per direction, starting at 0).

- **Client sends first**, then reads the server's.
- Plaintext is the 8192-byte `PeerInfo` struct: `type` (1 byte) + `data` (8191 bytes, zero-padded).
- Client sends type `0` (`ADB_RSA_PUB_KEY`) with its RSA public key in the data field.
- A server that derived a different key (wrong code) cannot decrypt our peer info and drops the connection → surfaces as `PairingResult.InvalidCode`.

## Framing

Each pairing message: `version(1B)=1 || type(1B) || length(4B big-endian) || payload`.
Types: `0`=SPAKE2, `1`=PEER_INFO. Max payload 64 KiB.

## Discovery

AOSP advertises `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp`. AETHER now uses Android NSD to discover them in separate, cancellable sessions. Pairing replies bind to a live service generation; lost services invalidate obsolete resolutions and submissions. Only loopback/local-interface addresses are accepted. The connection endpoint is discovered afresh after protocol pairing success. Manual entry remains an advanced fallback. See [notification-pairing-redesign.md](notification-pairing-redesign.md).

## AOSP public key and transport

The client PeerInfo data contains base64 of AOSP's 524-byte Android RSA public-key structure, then a space/comment/NUL. It is not SPKI DER. The little-endian fields are 64 words, `n0inv`, 256-byte modulus, 256-byte Montgomery R² and exponent 65537. See AOSP [android_pubkey.cpp](https://android.googlesource.com/platform/system/core/+/refs/heads/main/libcrypto_utils/android_pubkey.cpp) and [client/auth.cpp](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/client/auth.cpp).

Connection begins with plaintext CNXN, receives STLS version 0x01000000, acknowledges STLS, performs TLS 1.3 with the same persistent client identity, then receives CNXN over TLS. See AOSP [adb.cpp](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/adb.cpp). CNXN and OPEN string payloads are NUL-terminated. Shell output is bounded to 4 MiB; buffering applies backpressure before acknowledging WRTE. Individual cancellation closes the stream; I/O failure closes the transport and updates connection state.

## Library decision

**Hand-rolled; no third-party ADB dependency.**

- `tananaev/adb-lib` (GitHub) returns 404; the Maven coordinate `com.tananaev:adblib:1.3`
  implements only classic plaintext ADB, predates Android 11+ wireless debugging
  (TLS + SPAKE2 pairing), has no TLS layer and a blocking-socket API unfit for coroutines.
- License compatibility was moot: the library cannot speak the required protocol.

See `gradle/aether-adb-deps.txt`.

## Pending device and cryptographic validation

SPAKE2 has deterministic BoringSSL-vector coverage. RSA fields and connection ordering were cross-checked against primary sources and loopback fixtures. These checks do not prove interoperability with Android adbd. A user-supplied POCO F6 / Android 16 / HyperOS trace now verifies the real TLS/exporter/SPAKE2 exchange, authenticated ADB transport and shell UID 2000. V3 adds the DIGEST_NONE/raw RSA padding required by Conscrypt's AndroidKeyStore signing delegate while retaining SIGN/VERIFY-only non-exportable keys; V1/V2 are preserved. Repeated/lifecycle runs, other Android/OEMs and owner-user package operations still need device validation. See the notification-pairing incident report for the source contract and trace provenance. The BigInteger-based custom curve implementation is not constant-time and needs an independent cryptographic review before production approval. No compatible third-party ADB client has been adopted in this hardening pass; the existing implementation was repaired rather than replaced.

Hardware validation still pending for the broader device/lifecycle/package-operation matrix; one successful POCO F6 run is recorded.
