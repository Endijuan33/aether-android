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
│   ├── AdbKeyStores.kt        # AndroidAdbKeyStore (RSA-2048, alias AETHER_ADB_PAIRING)
│   │                          #   + app-private file fallback
│   ├── AdbPairingDiscovery.kt # discovery scaffolding (manual entry in 0.1.0)
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

- **UI** — Compose screens + ViewModels; observes `StateFlow`s, never touches
  sockets or the database directly.
- **Domain** — pure data models and the risk matrix (`riskOf()`); no Android
  dependencies, fully unit-tested.
- **Data (packages)** — `PackageManager` inventory, asset catalogue parsing,
  JSON export.
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
   CNXN/OPEN/WRTE/CLSE session tunnelled over TLS to the device's ADB
   (connection) port.
3. `AdbShell.exec(vararg args)` — the **only** shell entry point. The first
   argument must be in `{pm, cmd}` (allowlisted in `AdbConnectionManager`);
   package ids are validated by `InputValidators` before dispatch.

The pairing port and the ADB connection port are different endpoints
(typically both ephemeral, shown in system settings). They are stored and
entered separately.

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
- `OperationStatus`: `SUCCESS | FAILED | SKIPPED`
- `RiskLevel`: `LOW | MEDIUM | HIGH | CRITICAL` via `riskOf()` (evaluation
  order is significant: ESSENTIAL ⇒ CRITICAL; system uninstall / RISKY ⇒
  HIGH; unknown-system / plain user uninstall ⇒ MEDIUM; else LOW).

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
- **AndroidKeyStore** (alias `AETHER_ADB_PAIRING`) — the RSA-2048 pairing
  identity; app-private file fallback where AndroidKeyStore is unavailable.
  Note the [SECURITY.md](../SECURITY.md) caveat: local encryption is not
  extraction-proof on a compromised device.

## Test architecture

152 `@Test` functions under `app/src/test`:

- Pure-JVM unit tests for domain, validators, catalogue, export, executor,
  preferences-shaped logic, crypto (against BoringSSL oracle vectors).
- Socket-based fakes (`FakeAdbServer`, `FakePairingServer`, `FakeTls`) for
  pairing/connection-manager tests — these exercise the real protocol code
  over loopback sockets, not a real adbd.
- Turbine-based `StateFlow` tests for ViewModels.

Not covered: real-device pairing/`pm` execution (no device in CI), Compose
UI tests (written but not yet executed). See
[limitations.md](limitations.md).
