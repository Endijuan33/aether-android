# Architecture

AETHER is a single-module (`:app`) native Android app:
Kotlin, Jetpack Compose + Material 3, coroutines/Flow, Navigation Compose,
ViewModels. Package: `id.mybase.aether`.

## Module layout

```
id.mybase.aether/
├── MainActivity.kt            # single activity, hosts the NavHost
├── adb/                       # wireless-debugging + ADB engine
│   ├── AdbApi.kt              # public contracts: PairingResult, AdbConnectionState,
│   │                          #   AdbPairingClient, AdbShell, AdbKeyMaterial, AdbModule
│   ├── AdbConnectionManager.kt# AdbShell impl; state machine, allowlist, lifecycle
│   ├── AdbProtocol.kt         # classic ADB framing (CNXN/OPEN/WRTE/CLSE)
│   ├── AdbSession.kt          # session I/O over the TLS tunnel
│   ├── AdbTlsTransport.kt     # TLS transport for wireless debugging
│   ├── TlsPairingClient.kt    # AdbPairingClient impl: TLS + SPAKE2 handshake
│   ├── PairingProtocol.kt     # pairing message framing (version/type/length)
│   ├── PairingCrypto.kt       # SPAKE2 (BoringSSL SPAKE25519), HKDF, AES-128-GCM
│   ├── Ed25519.kt             # Edwards25519 primitives used by SPAKE2
│   ├── AdbKeyStores.kt        # AndroidKeyStore RSA-2048/PSS, alias AETHER_ADB_PAIRING_V3
│   ├── ConscryptPairingTransport.kt # public exporter (Conscrypt 2.8.0)
│   ├── AdbPublicKey.kt / LocalAdbEndpoint.kt # AOSP encoding / local-only endpoint
│   └── AdbDiagnostics.kt      # redactSecrets(): pairing codes/keys never reach logs
├── packages/                  # inventory, classification, operations
│   ├── PackageRepository.kt   # contract for package inventory
│   ├── PackageManagerRepository.kt  # PackageManager-backed impl (QUERY_ALL_PACKAGES)
│   ├── SafetyCatalogue.kt / CatalogueParser.kt / AssetSafetyCatalogue.kt
│   ├── PackageOperationExecutor.kt  # sequential plan executor, records history
│   ├── ExportInventory.kt     # JSON inventory export
│   └── PackagesModule.kt      # factories
├── persistence/               # DataStore prefs + SQLite history
│   ├── AetherPreferences.kt   # onboarding, last host/ports, theme
│   ├── OperationHistoryStore.kt
│   ├── SqliteOperationHistoryStore.kt  # aether_history.db
│   ├── InMemoryOperationHistoryStore.kt # tests/previews
│   └── PersistenceModule.kt
├── security/                  # InputValidators (host, port, pairing code, package id)
├── domain/                    # Models: AppPackage, AppKind, SafetyRating,
│                              #   OperationType, RiskLevel, riskOf(), OperationRecord
├── di/                        # AppContainer — manual DI, lazy process-scoped singletons
└── ui/                        # Compose screens, ViewModels, navigation, theme
    ├── navigation/ (Routes, AetherNavHost), theme/, components/
    ├── onboarding/, dashboard/, setup/, pairing/ (dialog), diagnostics/
    ├── apps/ (list + details), debloat/ (+ preview dialog + progress),
    ├── history/, recovery/, settings/, about/
```

## Layer responsibilities

- **UI** — Compose screens + ViewModels; collects state with lifecycle ownership. The document-picker callback writes explicit user exports on IO; sockets and database access remain behind repositories.
- **Domain** — pure data models and the risk matrix (`riskOf()`); no Android
  dependencies, fully unit-tested.
- **Data (packages)** — `PackageManager` inventory, asset catalogue parsing,
  JSON export. PackageInventory retains partial scan warnings rather than concealing unreadable packages.
- **ADB transport/pairing** — protocol framing, TLS, SPAKE2, session
  management; exposes only the small `AdbPairingClient` / `AdbShell`
  interfaces to the rest of the app.
- **Persistence** — DataStore for non-sensitive preferences; SQLite for the
  operation history (structured, queryable, durable across process death).
- **Security** — input validation and the diagnostics redaction boundary.

## ADB engine design

Pairing and shell are separate interfaces with separate lifecycles:

1. `AdbPairingClient.pair(host, pairingPort, code, timeoutMs=30s)` —
   TLS handshake to the device's pairing endpoint, SPAKE2 with the 6-digit
   code bound to the TLS exporter (channel binding), AES-128-GCM peer-info
   exchange. Returns `Success | InvalidCode | Timeout | ConnectionRefused |
   NetworkError`. Pairing is a one-time trust establishment; the device
   then authorizes this client's RSA-2048 public key.
2. `AdbShell.connect(host, adbPort, timeoutMs=15s)` — classic ADB
   CNXN/STLS negotiation followed by TLS and CNXN/OPEN/WRTE/CLSE to the device's ADB
   (connection) port.
3. `AdbShell.exec(vararg args)` — the **only** shell entry point. The complete argument list must match the fixed grammar in `AdbConnectionManager`: `id -u`, package-list queries, or uninstall/disable/enable for user 0. No arbitrary shell or `cmd` subcommands are accepted.

The pairing port and the ADB connection port are different endpoints
(typically both ephemeral, shown in system settings). The normal notification flow discovers them independently; manual entry remains an advanced troubleshooting fallback.

Protocol details and the library decision (hand-rolled, no third-party ADB
dependency) live in [adb-pairing-protocol.md](adb-pairing-protocol.md).

## State models

Explicit sealed interfaces, no loose booleans:

- `AdbConnectionState`: `Disconnected | Pairing(host,port) | Paired |
  Connecting(host,port) | Connected(host,port) | Failed(reason)`
- `PairingResult`: `Success | InvalidCode | Timeout | ConnectionRefused |
  NetworkError(message)`
- `SafetyRating`: `SAFE | RISKY | UNKNOWN | ESSENTIAL`
- `AppKind`: `USER | SYSTEM | DISABLED | UPDATED_SYSTEM`
- `OperationStatus`: `IN_PROGRESS | SUCCESS | FAILED | SKIPPED` (persisted IN_PROGRESS is shown as UNVERIFIED)
- `RiskLevel`: `LOW | MEDIUM | HIGH | CRITICAL` via `riskOf()` (evaluation
  order is significant: ESSENTIAL ⇒ CRITICAL; system uninstall / RISKY ⇒
  HIGH; any unknown package / plain user uninstall ⇒ MEDIUM; else LOW).

`AdbConnectionManager` owns connect/disconnect/reconnect and cancels
in-flight jobs on disconnect, so no sockets, coroutines or service
instances leak across lifecycle transitions.

## DI

No framework: `di/AppContainer` builds the graph from
`context.applicationContext` via the per-workstream module factories
(`AdbModule`, `PackagesModule`, `PersistenceModule`). All instances are
lazy process-scoped singletons.

## Persistence choices

- **DataStore preferences** (`aether_prefs`) — onboarding flag, theme,
  last host, last pairing port, last connection port. Non-sensitive;
  cold-Flow reads, suspending writes.
- **SQLite** (`aether_history.db`, `SQLiteOpenHelper`) — operation history:
  timestamp, package name, label, operation, status, message per executed
  op. Chosen over Room for 0.1.0 because the schema is one table and the
  query surface is append + list; Room becomes worth it only when queries
  grow.
- **AndroidKeyStore** (alias `AETHER_ADB_PAIRING_V3`) — the RSA-2048 pairing
  identity; provider failures are surfaced. V1/V2 entries are preserved. V3 requires system-mediated re-pairing because existing authorizations cannot be amended. It retains RSA-PSS/PKCS#1 and SHA-256/384/512, and authorizes DIGEST_NONE/raw padding for Conscrypt's TLS RSA signing delegate. AndroidKeyStore maps private-key Cipher.ENCRYPT_MODE to SIGN; key purposes remain SIGN/VERIFY, with no ENCRYPT/DECRYPT and no extractable private-key fallback.
  Note the [SECURITY.md](../SECURITY.md) caveat: local encryption is not
  extraction-proof on a compromised device.

## Test architecture

Automated tests (actual counts/results are recorded in the engineering audit):

- Pure-JVM unit tests for domain, validators, catalogue, export, executor,
  preferences-shaped logic, crypto (against BoringSSL oracle vectors).
- Socket-based fakes (`FakeAdbServer`, `FakePairingServer`, `FakeTls`) for
  pairing/connection-manager tests — these exercise the real protocol code
  over loopback sockets, not a real adbd.
- Turbine-based `StateFlow` tests for ViewModels.

Not covered: real-device pairing/`pm` execution (no device in CI), Compose/UI and platform persistence/keystore tests (compiled, device execution pending). See
[limitations.md](limitations.md).

## Mutation safety and interruption

The shared executor mutex serializes entire plans. Each mutation requires owner-user inventory, a validated package ID, non-essential target, shell UID 2000 and installed-for-user-0 state. Before sending a mutation it commits an IN_PROGRESS history row. Expected command output and a second package query are both required for SUCCESS. Partial failures continue; cancellation stops subsequent work and records uncertainty. Clearing completed history preserves unresolved IN_PROGRESS rows.

Uninstall has no rollback feature. Disable/enable toggle availability only; they do not undo application side effects or restore deleted data. Process death disconnects ADB and leaves durable unverified records, never an automatic replay of mutations. A short foreground service owns notification pairing/discovery for up to 150 seconds. It stops at a terminal state; the shell remains process-scoped. There is no persistent foreground shell service or Shizuku API/Binder server.

## Notification pairing controller

`adb/pairing/WirelessPairingController` owns a strongly typed phase/snapshot, legal transition checks, single-session guard and single-use generation tokens. `AetherAdbPairingService` starts from a visible user action, meets the foreground deadline, uses shortService on API 34+, publishes native RemoteInput notifications and returns START_NOT_STICKY. Replies enter the explicit non-exported service directly; no notification-listener permission or trampoline receiver is used. Codes are never saved or redelivered.

`NsdAdbServiceDiscovery` owns per-collection Android NSD/network listeners. API 34+ uses ServiceInfoCallback updates; API 30–33 serializes legacy resolves. DiscoveryLease rejects late/out-of-order updates. Service loss or Wi-Fi loss invalidates endpoints. Search, pairing, fresh connection discovery, authenticated transport and `id -u` verification occur sequentially. The dashboard and ConnectionViewModel show Connected only after UID 2000 verification. The package executor still rechecks UID and package state independently before mutations.

Pairing permission requests are owned by the NavHost so navigation does not discard their callbacks. Notification permission/channel failure offers in-app discovered code entry. SDK 37 local-network denial is a separate blocking permission with Android settings guidance. Manual pairing/connection live under Advanced manual setup. All new components reuse the original key/crypto/transport and add no runtime dependency.
