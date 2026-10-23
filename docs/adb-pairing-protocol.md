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
pairing to the specific TLS connection, preventing MITM forwarding. On Android,
export via Conscrypt reflection (`com.android.org.conscrypt.Conscrypt.exportKeyingMaterial`).
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

**Manual entry only in 0.1.0.** Wireless debugging does not advertise a documented,
stable mDNS service for the pairing endpoint that can be relied upon; the pairing
port is ephemeral and shown in system settings alongside the 6-digit code. NsdManager
discovery is not implemented to avoid depending on unverified advertisement behavior.

## Library decision

**Hand-rolled; no third-party ADB dependency.**

- `tananaev/adb-lib` (GitHub) returns 404; the Maven coordinate `com.tananaev:adblib:1.3`
  implements only classic plaintext ADB, predates Android 11+ wireless debugging
  (TLS + SPAKE2 pairing), has no TLS layer and a blocking-socket API unfit for coroutines.
- License compatibility was moot: the library cannot speak the required protocol.

See `gradle/aether-adb-deps.txt`.

## PENDING hardware validation

The SPAKE2 construction, key schedule, framing, and peer-info struct are verified
against BoringSSL's oracle vectors and AOSP sources. What remains:

1. **Live device pairing** — the full handshake has not been exercised against a real
   adbd (no device in this environment). The TLS exporter via Conscrypt reflection
   is the highest-risk item; if `exportKeyingMaterial` is unavailable, pairing fails
   closed (no fake success).
2. **Peer-info RSA public-key encoding** — AOSP's exact expected encoding inside the
   8191-byte data field is unverified; we send X.509 SPKI DER (the standard form).
3. **mDNS discovery** — deliberately not implemented (see above).
