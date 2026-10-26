# Engineering audit and hardening

> Historical hardening baseline. The subsequent native notification pairing/discovery redesign and current build artifacts are documented in [notification-pairing-redesign.md](notification-pairing-redesign.md). Counts, paths and artifact hashes below describe the prior audit snapshot.

Audit date: 2026-10-25. Baseline: `ca653dd` on `main`, matching the local
`origin/main` reference. The pre-existing untracked `AGENTS.md` is preserved.
Remote CI and physical-device validation have not been established by this audit.

## Baseline and execution plan

The app is one Kotlin/Compose module, with custom SPAKE2 pairing, ADB transport,
PackageManager inventory, a bundled 52-entry catalogue, DataStore preferences,
SQLite history, and 14 navigation destinations plus the Android splash screen.
All 82 Kotlin source/test files were inventoried using token-saver skeletons;
implementation review proceeds by symbol and explicit line range.

The required `codebase-memory` skill is unavailable: it is absent from registered
skills, MCP tools/resources, and a bounded search of alternate skill directories.
Token-saver is available and used. The command sandbox cannot start because of
an invalid app-server socket mount; commands use the approved escalation path.

JDK 17 and Gradle Wrapper 9.8.0 execute. The first baseline verification command failed
because `ktfmtCheck` was not registered. The existing Android SDK initially lacks
API 37; Gradle installs the licensed platform during the second baseline run.
Actual baseline and final build results are recorded below.

Priorities:

1. Reproduce and repair protocol incompatibilities, command validation, false
   operation success, cancellation, identity storage and backup behavior.
2. Repair lifecycle ownership, stale inventory, recovery and observable errors.
3. Audit screen behavior, accessibility, release build and CI gates.
4. Run complete regression checks, inspect actual APKs, and record external
   blockers without making production-readiness claims.

## Confirmed initial findings

| ID | Severity | Evidence and consequence | Status |
|---|---|---|---|
| A01 | HIGH | `TlsPairingClient.buildPeerInfo` sends X.509 SPKI DER; AOSP `CalculatePublicKey`/`android_pubkey_encode` use base64 of a 524-byte Android RSA key structure. The paired identity cannot be registered correctly. | Fixed; evidence below |
| A02 | HIGH | `AdbTlsTransport.openAdbChannel` sends TLS immediately; AOSP `adb.cpp` negotiates plaintext CNXN/STLS before the connection TLS handshake. Existing TLS-first fake duplicates the defect. | Fixed; evidence below |
| A03 | CRITICAL | `AdbConnectionManager.validateArgs` only allowlists command names and omits redirection, quoting and argument boundaries; `exec` joins arguments into a shell string. Arbitrary `pm`/`cmd` operations and shell redirection remain possible. | Fixed; evidence below |
| A04 | HIGH | `PackageOperationExecutor.runOperation` reports every `Result.success(String)` as SUCCESS, including `Failure [...]` output. Legacy `shell:` does not carry an exit status, and no resulting state is checked. | Fixed; evidence below |
| A05 | HIGH | Executor catches `Exception` including coroutine cancellation and continues privileged operations; independent execution plans have no shared mutex. | Fixed; evidence below |
| A06 | HIGH | `ENABLE` omits `--user 0`, unlike disable/uninstall. Inventory is for the app's Android user while operations target user 0, with no ownership verification. | Fixed; evidence below |
| A07 | HIGH | Pairing opens a blocking channel without a cancellation guard; its transport can leak TCP sockets before the TLS try/finally. | Fixed; evidence below |
| A08 | HIGH | AndroidKeyStore failures silently fall back to exportable private-key files; manifest enables backup without excluding credentials, inventory metadata or operation history. | Fixed; evidence below |
| A09 | MEDIUM | `AdbSession` uses an unlimited channel and unbounded shell-output buffer, leaves dead reader sessions open, and does not notify manager state on connection loss. | Fixed; evidence below |
| A10 | MEDIUM | `MainActivity` constructs a new `AppContainer` on every recreation while ViewModels retain old dependencies, splitting socket/executor/history ownership. | Fixed; evidence below |
| A11 | MEDIUM | `PackageManagerRepository.getInstalledPackages` returns an empty list for bulk SecurityException/RuntimeException, concealing inventory failures. | Fixed; evidence below |
| A12 | MEDIUM | `riskOf` uses mutually exclusive AppKind; disabled/updated-system packages lose system-origin risk. | Fixed; evidence below |
| A13 | HIGH | CI wrapper validation uses `continue-on-error: true`, allowing an integrity failure to pass. | Fixed; evidence below |
| A14 | MEDIUM | Required `./gradlew ktfmtCheck` is missing; release verification omits formatting. | Fixed; evidence below |
| A15 | MEDIUM | README/architecture/limitations/dependency docs conflict with actual mDNS and Conscrypt code; SQLite and Compose UI coverage claims lack corresponding tests. | Fixed; evidence below |

## Evidence boundaries

Primary AOSP sources were fetched read-only for comparison; protocol source
inspection and deterministic vectors establish implementation evidence, not
successful hardware pairing. Package changes must be verified for the intended
Android user. A connection handshake alone does not prove package privileges.

Hardware validation still pending.

## Additional findings and corrections

| ID | Severity | Original problem / root cause | Correction | Verification boundary |
|---|---|---|---|---|
| A16 | HIGH | `AndroidAdbKeyStore`: the original key authorized only PKCS#1 signatures; TLS 1.3 client authentication requires RSA-PSS. | V2 alias authorizes PSS and suitable digests; legacy key is preserved. Re-pairing is required. | Keystore platform regression compiled; hardware execution pending. |
| A17 | CRITICAL | `PreviewDialog` and executor allowed disabling/uninstalling catalogue ESSENTIAL packages and AETHER. | Protected targets are refused in the executor and preview; ENABLE remains available. | Executor protection regression and compiled UI coverage. |
| A18 | HIGH | `PackageOperationExecutor`: history was written only after mutation, losing evidence on process death. | Durable IN_PROGRESS row before mutation; expected output plus state verification before SUCCESS. Uncertain rows survive clearing. | History-before-mutation, cancellation and concurrent-plan regressions; SQLite device test compiled. |
| A19 | HIGH | `AboutScreen` presented an unsupported “Hardware-validated” claim. | Replace with explicit pending-validation status; reconcile public docs. | Source/diff review; no hardware success asserted. |
| A20 | HIGH | `PairingDialog` decoration omitted the BasicTextField inner editor. | Invoke the actual editor; filter ASCII digits, handle paste and prevent repeated submission. | Input-validator unit tests; real Compose input regression compiled. |
| A21 | MEDIUM | Pairing dialog watched a shell Paired state that its separate pairing path did not emit. | Observe the pairing result directly, cancel on dismissal and keep connection port separate. | Connection ViewModel tests; compiled success-dismissal UI regression. |
| A22 | MEDIUM | Preview acknowledgement applied only to ESSENTIAL/CRITICAL entries; UNKNOWN/RISKY operations bypassed it. | Require acknowledgement for unknown/non-low risk, block protected mutations, gate execution on connection/in-progress state. | Risk/executor tests and source review; device UI execution pending. |
| A23 | MEDIUM | `HistoryViewModel` and `SettingsViewModel` let storage failures escape; screens lacked matching error states. | Expose loading/error/busy states; serialize history requests and navigate onboarding after persistence. | Storage-error ViewModel regressions. |
| A24 | LOW | `AppListScreen` “Export” copied JSON to the clipboard. | Use the Android document picker and IO output with close/error handling; label export as a reference, not rollback backup. | Existing JSON tests; document-provider hardware test pending. |
| A25 | MEDIUM | Screen flows used plain collection across inactive lifecycles. | Use collectAsStateWithLifecycle on data-driven screens. | Compilation/lint and source review. |
| A26 | MEDIUM | `RecoveryViewModel` offered already-enabled packages and read older history rows. | Filter to disabled installed apps and latest per-package records. | Recovery tests/source review; pm hardware behavior pending. |
| A27 | MEDIUM | `PackagesViewModel` retained selections for removed inventory entries and did not refresh after successful mutation. | Prune selection against scanned inventory and reload after verified success; filtering runs off Main. | Package ViewModel suite and executor tests. |
| A28 | MEDIUM | All SDK workflows supplied unsupported api-level/build-tools setup-android inputs. | Use the pinned action’s packages input, actual android-37.0 platform and build-tools 36.0.0. | Pinned action.yml inspected; local SDK/build evidence; YAML parsed. |
| A29 | HIGH | Release R8 failed on obsolete Conscrypt platform adapter types below minSdk. | Narrow rules for the two unreachable legacy adapter types, alongside the maintained Conscrypt update. | Actual R8/resource-shrunk release assembly; no broad ignorewarnings. |
| A30 | HIGH | Conscrypt 2.5.2 native ELF LOAD segments had 0x1000 alignment. | Update to stable 2.8.0: public exporter retained, minSdk 21, LOAD alignment 0x4000. | AAR/javap/readelf inspection; final APK inspection below. |
| A31 | MEDIUM | OSV matched four advisories for BC 1.80 (test-only), including constraints/ASN.1/LDAP/GOST issues. | Move BC out of production and select 1.85, which addresses the matching advisories. | Updated OSV query returned no matches for selected BC artifacts; JVM TLS tests. |
| A32 | MEDIUM | Wrapper configuration lacked a distribution checksum. | Pin the independently fetched publisher SHA-256; compare local wrapper JAR with publisher digest. | Exact wrapper checksum comparison. |
| A33 | MEDIUM | Resolved dependencies lacked lock/checksum verification and automated runtime advisories. | Record lockfiles/SHA-256 metadata and query the actual release graph in CI/release/weekly checks. | Strict rerun and actual 131-coordinate OSV result below; bootstrap trust limits documented. |
| A34 | LOW | Two unreferenced mDNS implementations contradicted supported manual setup; one did not stop discovery on success. | Remove unreachable duplicate scaffolding after reference checks and document AOSP service types accurately. | Token-saver plus scoped reference check; compilation. |
| A35 | MEDIUM | Light primary/white contrast was 3.70:1, below normal-text 4.5:1. | Darker primary reaches 5.08:1; secondary/white 5.24:1 and dark secondary-container text 6.83:1. | WCAG luminance calculation; TalkBack/device assessment pending. |
| A36 | LOW | `SafetyDot` claimed accessible naming but had no semantics; back icon was deprecated and not mirrored. | Add safety content description and AutoMirrored back icon; modern clipboard API. | Compiled semantics UI regression and lint. |
| A37 | LOW | Lint identified dead XML colors, obsolete pre-minSdk branches, redundant icon qualifiers and optional modifier ordering. | Remove dead branches/resources, preserve/move adaptive icons, use catalogued test dependencies and conventional modifier order. | Final lint/compilation; no unnecessary behavior-mirroring tests. |
| A38 | MEDIUM | NavHost dialog destinations wrapped additional Dialog windows, producing nested modal windows. | Navigation owns each dialog window; content is a Card and pairing cancellation follows disposal. | Compiled single-dialog/navigation UI regression; device execution pending. |
| A39 | MEDIUM | Cancellation test used virtual delay to assume real socket OPEN arrival; fake server left accepted sockets open. | Synchronize on actual OPEN and close fake accepted sockets; add real transport-loss and burst-output regressions. | Deterministic socket test suite. |
| A40 | INFO | Custom `Ed25519`/SPAKE2 uses variable-time BigInteger operations; oracle vectors do not establish side-channel safety. | Retained compatible implementation with explicit independent cryptographic review gate. | No side-channel security claim; production review remains open. |
| A41 | INFO | No supported local emulator/device-test infrastructure; SDK adb exits 132. | Compile device tests and document exact blocker plus physical-device matrix. | Hardware validation still pending. |
| A42 | INFO | Local release signing configuration/credentials are absent. | Produce an explicitly unsigned release; remote signing is conditional with restrictive file modes and EXIT cleanup. | Unsigned artifact/signature inspection; remote secret availability unverified. |
| A43 | INFO | Hardening changes are local and uncommitted; baseline remote CI cannot validate them. | Record baseline CI URL separately; leave reviewable workflows and exact execution commands. | Remote hardening CI remains pending. |
| A44 | MEDIUM | `PackageManagerRepository` silently dropped per-package metadata failures. | Return PackageInventory with partial-result warnings; browsing/debloat/recovery expose them and allow rescan. | Partial inventory ViewModel regression; compilation and lint. |
| A45 | MEDIUM | `RecoveryScreen` suggested install-existing without limiting it to retained factory APKs or explaining lost data. | Qualify availability restoration, owner-user arguments, OEM failure and unavailable data/APK rollback. | Source/diff review; no invented rollback feature. |
| A46 | MEDIUM | `AdbSession.route` ignored acknowledgements/output for an OPEN cancelled before remote ID arrival, leaving the peer stream open. | Close unknown/cancelled acknowledged streams; capture state once and recheck concurrent removal. | Controlled delayed-ACK socket regression; final strict pass. |
| A47 | MEDIUM | `.github/workflows/release.yml` inserted `github.ref_name` directly into shell code, reported secret presence as signing success, and inspected only the pre-sign APK without retaining its report. A valid tag containing command substitution reproduced shell execution in a controlled fixture. | Pass summary values through environment variables to `scripts/release_summary.sh`; report the actual signing-step outcome and mark failed builds unverified. Inspect after signing and upload the final inspection report. | Original interpolation reproduced with a harmless marker file; hardened controlled fixtures and all 4 persistent `test_release_summary.py` regressions passed without executing the tag. CI/release now run the tests. YAML/pins/Bash syntax checks passed; remote CI pending. |

Total recorded observations: **47** (2 CRITICAL, 14 HIGH, 23 MEDIUM, 4 LOW, 4 INFO).
**43** repaired defects/weaknesses: A01–A39 and A44–A47. A40–A43 remain explicit review or external validation gates.
Severity is an engineering risk assessment, not a claim that every issue has an external exploit.
A03 was an unsafe privileged API boundary; the existing UI did not expose a general-purpose command console.

## Architectural, security and performance changes

The existing single-module implementation is preserved. AppContainer.get uses the application context and owns shared transport, identity, history and executor instances across activity recreation. ViewModels remain lifecycle owners for requests; there is no newly invented Shizuku service or privilege broker.

Pairing and connection share the AndroidKeyStore V2 identity. Provider failures are surfaced instead of silently exporting a private key to disk. TLS 1.3, local-only endpoint resolution, a complete shell argument grammar, owner-user inventory checks and per-operation UID checks reduce the privileged boundary. Automatic cloud backup/device transfer is excluded; Conscrypt/BoringSSL notices are included as APK assets.

Package plans are serialized across the shared executor. Each dispatched mutation has a durable IN_PROGRESS record. Successful command text alone is insufficient: the executor separately verifies resulting user-0 package state. Cancellation stops later work and retains UNVERIFIED history. Partial pm failures continue. Clearing completed history preserves unresolved records. Unknown catalogue entries remain unknown; no catalogue recommendation was guessed or upgraded.

The SPAKE2 mathematics remain custom and variable-time; selected BoringSSL vectors are evidence of compatibility, not cryptographic production approval. The transport advertises legacy ADB wire version 0x01000000 and retains additive checksums; AOSP transport.cpp confirms checksum skipping is conditional on negotiated 0x01000001. No checksum-free assumption was introduced.

Socket ownership now covers acquisition, cancellation, stream cleanup and connection loss. Bounded buffering applies backpressure and output is capped at 4 MiB. Package sorting/filtering runs on a background dispatcher and already-lazy inventories retain stable package-name keys. No unsupported startup/battery benchmark or measured device performance gain is claimed.

## Interface and navigation review

Actual graph: **14 destinations (12 screens and 2 dialogs), plus the Android splash screen**. Onboarding, dashboard, setup guide, diagnostics, app browser/details, debloat, preview, progress/results, history, recovery, settings and about are implemented. Results are part of the progress destination; there is no invented separate results screen.

Data flows use lifecycle-aware collection. Pairing uses its actual editor/result, supports paste/ASCII digits, guards duplicates and cancels on dismissal. Navigation owns the modal window; it no longer wraps another Dialog. Preview blocks essential/AETHER mutations and requires risk acknowledgement. Disable is the default debloat operation. Inventory failures/partial results, settings/history errors, stale selection and recovery filtering are surfaced. Export writes to a user-chosen document provider on IO; it is an inventory reference, not an APK/data backup.

Accessibility changes include safety-dot semantics, labelled theme toggle, mirrored back navigation and measured color contrast: light primary/white 3.70 → 5.08:1, secondary/white 5.24:1, dark secondary-container text 6.83:1. Device TalkBack, font scaling, focus and contrast across all component states remain validation work; compilation is not a UI usability test.

## Dependency and release decisions

- Conscrypt 2.5.2 → 2.8.0: publisher AAR minSdk 21, Apache-2.0, exporter API retained, native LOAD segments 0x4000 rather than 0x1000. No broad platform-dependency churn.
- BouncyCastle 1.80 → 1.85, JVM-test-only; remove unused bcpkix and ephemeral pairing certificate implementation. Matching OSV advisories for 1.80: GHSA-574f-3g2m-x479, GHSA-9pwp-9qqc-pr26, GHSA-c3fc-8qff-9hwx, GHSA-qp49-qgx5-5m26. The selected fixed artifacts returned no matching advisories.
- Add BOM-aligned Compose UI test libraries and AndroidX runner 1.7.0/ext JUnit 1.3.0. The activity manifest helper is debug-only.
- Keep AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Gradle 9.8.0, JDK 17 and SDK floor/target 30/37. Pin build-tools 36.0.0.
- Add checksum-verified ktfmt 0.65 Gradle tasks. Formatter JAR SHA-256: aba9753b6ab387926179ab7318ba8e0c33dad483ebc72f633e79522265b15214.
- Wrapper JAR matches publisher SHA-256 238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5. Distribution SHA-256 is pinned to bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c.
- Dependency locks/checksum metadata detect later changes after bootstrap acceptance. This is not independent publisher-signature attestation. The strict rerun passed as recorded below.
- Local release retains id.mybase.aether, versionName 0.1.0/versionCode 1, minSdk 30/targetSdk 37, adaptive/monochrome branding, R8 and resource shrinking. The release is unsigned; a debug signing certificate does not qualify it for production.

## Validation record

Baseline: required ktfmtCheck was absent; debug assembled; 152 unit tests (151 passed, 1 skipped); release R8 failed on two legacy Conscrypt adapter types.

Completed before the last socket-race change: formatting, **169 unit tests with no failures/skips**, lintDebug/lintRelease, assembleDebug/assembleRelease and assembleDebugAndroidTest all passed. Both lint variants had 0 errors and 6 warnings. The final strict regression record contains **170** passing unit tests, as detailed below.

The six lint warnings per variant are justified, not disabled: one newer-Gradle recommendation, three newer-test-BC recommendations (the selected stable 1.85 fixes the matching advisories), and two protocol-specific custom trust-manager warnings. ADB uses self-signed certificates; the pairing code/exporter authenticates pairing. Later connections trust the user-selected local listener and Android client-key authorization, not public web PKI. A compromised local process/OS can spoof command output; state/UID checks do not authenticate such a malicious peer.

A previous concurrent-edit lint attempt crashed in Compose ModifierParameterDetector. Stable-tree lint subsequently passed without disabling the detector. Compiler warnings from Android Context test stubs overriding deprecated abstract APIs are test-fixture compatibility warnings. The machine AAPT2 override warning is environmental; remaining Gradle deprecation source is recorded with the final trace.

Actual runtime advisory check: **131 resolved release Maven coordinates, 0 matching OSV advisories**. This is a database lookup result, not a zero-vulnerability guarantee. The development script fails on matching findings, incomplete results or network failure.

Instrumentation now contains **12 tests** for real Compose input/navigation/semantics, essential/unknown/offline/empty previews, search/error states, onboarding/settings persistence, SQLite finalization and AndroidKeyStore PSS. Compilation evidence is recorded below. Local SDK adb exits **132** and no emulator is installed, so device tests cannot execute here. A pinned emulator CI job executes these tests on API 35 when the revised workflow runs.

## CI/CD evidence

Public baseline CI passed at ca653dd:
https://github.com/Endijuan33/aether-android/actions/runs/40044383903

That run does not validate local hardening changes. No commit, push, release publication or remote workflow run was performed for this working tree. Workflow YAML is locally parsed, action inputs checked against pinned action.yml, and all external actions use immutable SHAs.

CI now gates wrapper integrity, shared formatting, unit tests, both lint variants, debug/release assembly, device-test compilation, runtime OSV checks, APK inspection/checksums and artifact/report upload. A separate API-35 emulator job runs Android tests. Release verification uses the same checks; signing remains conditional, uses environment password indirection, restrictive temporary-file modes and EXIT cleanup. No signing secret was accessed or committed.

## Hardware and production acceptance

**Hardware validation still pending.** Pairing success, transport connection, shell UID and package-state verification are distinct checks. Socket fixtures do not prove actual adbd authorization or OEM behavior. The app provides a narrow integrated ADB/pm experience; it does not implement a Shizuku-compatible service API.

Production readiness is withheld pending physical-device pairing/connection/pm tests, UI/platform test execution, independent custom-crypto review, release signing and policy/release assessment. The local environment cannot supply these external approvals or hardware. Network changes, Wi-Fi off/on, process death, OEM package rules, accessibility and 16-KiB runtime loading belong in that matrix.

Supported mutations target device-owner user 0 only. Uninstall rollback and deleted-data recovery are not implemented. Manual local endpoint entry remains the supported setup path; background persistence/automatic reconnect after process death is not provided. History browsing/recovery queries currently show recent records; older records remain persisted and manual enable is available. Explicit export to a cloud document provider follows that provider's policy.

## Next engineering steps and execution commands

1. Review the local diff, commit the intended project changes while excluding the pre-existing AGENTS.md, and run the revised CI. No remote success for these changes is assumed.
2. On a supported test runner, execute ./gradlew connectedDebugAndroidTest. Use disposable test-device packages for pm validation; never debloat essential components to prove a build.
3. Test Android 11 plus current Android/OEM versions, including a 16-KiB device: pairing-code expiry/wrong code, separate ports, actual certificate authorization, shell UID, pm failure/state verification, cancellation, network loss and process death.
4. Obtain independent review of the custom SPAKE2/curve implementation and local-listener threat model.
5. Supply release signing through the documented secret path, inspect the signed certificate/APK, and review QUERY_ALL_PACKAGES policy, SDK eligibility, privacy/accessibility and release notes before distribution.

Local final commands:

```bash
ANDROID_HOME=/home/endcore/android-sdk ./gradlew ktfmtCheck testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest :app:exportRuntimeDependencies --console=plain --max-workers=2
python3 scripts/check_dependency_advisories.py
python3 scripts/inspect_apks.py app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk
python3 -m unittest discover -s scripts -p 'test_*.py' -v
# On a supported test device/emulator:
./gradlew connectedDebugAndroidTest
```

## Final strict validation evidence

The final complete run after the cancelled-OPEN fix succeeded in **6m 53s**, with
138 actionable tasks (54 executed, 84 up-to-date). Dependency verification was
enabled in its default strict mode; this was not a metadata-generation run.
Local full log: `/tmp/aether-final-verified.log`.

```bash
ANDROID_HOME=/home/endcore/android-sdk ./gradlew ktfmtCheck testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest :app:exportRuntimeDependencies --console=plain --max-workers=2 --warning-mode all -Dorg.gradle.deprecation.trace=true
```

| Check | Actual result / evidence |
|---|---|
| Kotlin formatting | `ktfmtCheck` passed, including checksum verification of the formatter. |
| Kotlin compilation | Debug, release and device-test Kotlin compilation passed. |
| JVM regressions | **170 tests, 0 failures, 0 errors, 0 skipped**, aggregated from `app/build/test-results/testDebugUnitTest/TEST-*.xml`. |
| Android lint / static analysis | Both variants passed: **0 errors, 6 warnings each**; XML/HTML reports under `app/build/reports/`. No additional detekt suite is configured. |
| Dependency integrity | Wrapper publisher checksums matched; Gradle lockfiles and SHA-256 verification metadata accepted by the strict run. |
| Runtime advisories | `app/build/reports/dependency-advisories.json`: **131 resolved Maven coordinates, 0 OSV matches**. Database coverage is limited. |
| Debug build | `assembleDebug` passed; actual APK and signature verified below. |
| Release build | `assembleRelease` passed with R8/resource shrinking; unsigned artifact verified below. |
| Android instrumentation | `assembleDebugAndroidTest` passed; **12 tests compiled, none executed locally**. |
| Workflow validation | All three YAML files parsed; every external action reference is an immutable 40-character SHA. Bash syntax checks passed for 9 CI, 4 dependency-check and 10 release blocks, plus the extracted summary script. |
| Release-summary regression | Original valid-tag command substitution reproduced with a harmless marker; all **3** controlled hardened fixtures and **4 persistent Python unittest tests** passed. The tests execute the production Bash script with literal hostile tags and signing/build branches; they use only Python's standard library and run in CI/release. These are separate from the 170 JVM tests. |
| Diff hygiene | `git diff --check` passed. |

Only comment corrections followed the full Android run. The changed Kotlin
files were formatted; `ktfmtCheck compileDebugKotlin compileDebugUnitTestKotlin`
then passed in **22s** (`/tmp/aether-final-comment-check.log`). The subsequent
release-workflow correction passed the controlled shell regressions and YAML/
Bash checks above. It did not change application behavior.

Remaining build warnings are attributable rather than suppressed:

- Gradle's `Configuration.setVisible(boolean)` deprecation originates in AGP,
  including `BasePlugin.createAndroidJdkImageConfiguration`,
  `VariantDependenciesBuilder`, source-set and UTP configuration creation.
  The full trace is in the final log. It is an upstream compatibility concern
  for a future Gradle upgrade, not a reason to change application logic.
- JVM `FakeContext` implementations override deprecated Android abstract APIs
  for fixture compatibility; production Context usage does not require these
  overrides.
- `android.aapt2FromMavenOverride` is the existing ARM64/PRoot host workaround.
  It is local configuration, not committed release configuration.
- Lint's newer-version recommendations and the two ADB trust-manager warnings
  are justified above. Public web-PKI validation cannot simply replace the
  ADB trust model. The local-listener spoofing assumption remains explicit.

## Significant fix verification

| Findings | Named regression evidence | Actual result |
|---|---|---|
| A01, A03, local endpoint validation | `AdbBoundaryTest`: AOSP RSA fields/Montgomery parameters, complete command grammar, local/remote endpoint rejection. | 3/3 passed. |
| A02, A05, A09, A46 | `AdbConnectionManagerTest`: structured shell execution, cancellation, delayed OPEN acknowledgement cleanup, burst inventory output and transport-loss state. | 17/17 passed against controlled socket fixtures. |
| A07 and pairing error handling | `TlsPairingClientTest`, `PairingProtocolTest`, `PairingCryptoTest`: validation/timeouts/wrong code, packet framing and selected oracle vectors. | 9/9, 8/8 and 25/25 passed. This does not establish hardware or side-channel safety. |
| A04, A05, A06, A17, A18 | `ExecutorTest`: pm Failure output, state mismatch, owner-user targeting, protected packages, shared serialization, durable pre-mutation history and cancellation. | 15/15 passed. |
| A12, A22, catalogue integrity | `DomainTest` and `CatalogueTest`: retained system-origin risk and catalogue/input invariants. | 15/15 and 18/18 passed. |
| A23, A27, A44 | `HistoryViewModelTest`, `PackagesViewModelTest`, `SettingsViewModelTest`: storage errors, selection refresh and partial inventory warnings. | 4/4, 10/10 and 1/1 passed. |
| A16, A20, A38 and Android persistence/accessibility | Compiled Compose/platform instrumentation suite. | 12 tests compiled; execution blocked by the local runner. No device success claimed. |
| A47 | Controlled real Bash summary execution with a Git-valid command-substitution tag; persistent `ReleaseSummaryTest` covers hostile tag data and signing/build branches. | Baseline defect reproduced; 3 controlled fixtures and 4/4 persistent tests passed. Remote signing itself remains untested. |

## Verified APK artifacts

The archive-inspection script passed on both application APKs. It checked ZIP
CRC integrity, hashes, every native library's declared ELF LOAD alignment and
uncompressed ZIP data offsets. Each application APK contains 12 native libraries
across arm64-v8a, armeabi-v7a, x86 and x86_64; all inspected LOAD alignments and
ZIP offsets meet the 16-KiB checks. This is packaging evidence, not runtime
validation on a 16-KiB device. Both APKs contain the Conscrypt and BoringSSL
license assets. Detailed generated report: `app/build/reports/apk-inspection.json`.

| Artifact | Size (bytes) | SHA-256 | Signing |
|---|---:|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 77,240,951 | `7b5e24456229cde3f30107ca9573e82ec957d5a1d93db3453f7ce1f8cf804d90` | Verified APK Signature Scheme v2, Android Debug certificate. |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 13,762,746 | `fd185527f4fef5cee39644df2709961873ffd92e10644a52a9aa7ff020bc20b9` | **Unsigned**; apksigner verification fails as expected. Requires release signing before installation/distribution. |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2,672,470 | `820341b33fae67c6c5b219150daf36548b8bee6d3b3010a432f4cfaf3bae43e6` | Instrumentation package compiled; no execution result. |

Final debug certificate SHA-256:
`18ff2a8be8ee42f539eb6d551a14973ffe346d52d9a959013964bff4869c6a4b`.
Release signature inspection reported `DOES NOT VERIFY / Missing META-INF/MANIFEST.MF`,
consistent with the deliberately unsigned artifact; this is not release-signing success.

AAPT2 badging and the compiled binary manifest confirm application ID
`id.mybase.aether`, versionCode **1**, versionName **0.1.0**, minSdk **30** and
target/compileSdk **37**. Release `debuggable` is absent (platform default false),
`allowBackup=false`, `usesCleartextTraffic=false`, and backup/data-transfer
exclusion resources are referenced. The exported launcher is `MainActivity`.
AndroidX's startup provider is not exported; its exported profile-install receiver
requires `android.permission.DUMP`. Permissions are INTERNET,
ACCESS_NETWORK_STATE, QUERY_ALL_PACKAGES and the AndroidX-generated signature
permission `id.mybase.aether.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
QUERY_ALL_PACKAGES distribution-policy eligibility is still a release gate.

## Acceptance status

Local build, formatting, unit tests, dependency checks and APK packaging checks
have evidence. Protocol source comparisons and controlled fixtures have evidence.
Actual pairing/connection/shell/pm behavior, device UI/platform/accessibility,
network/process-death/OEM behavior, custom-crypto production approval, signed
release and CI for this diff remain pending. **Production-ready status is not
granted. Hardware validation still pending.**

The changes remain local and uncommitted on `main` at `ca653dd`; repository history
and the pre-existing `AGENTS.md` are preserved. No commit, push, workflow submission
or release publication was performed; no signing credential was accessed.

## Files changed

The inventory below contains **105 paths** (73 M, 26 A, 6 D).
`M` means modified, `A` added/untracked project work, and `D` removed from its
original path. The test-only TLS fixture and adaptive icon moves are represented
as deletion/addition pairs because these changes are uncommitted. The pre-existing
untracked `AGENTS.md` and ignored local/generated files are excluded.

```text
M .github/workflows/ci.yml
M .github/workflows/dependency-check.yml
M .github/workflows/release.yml
M .gitignore
M CHANGELOG.md
M PRIVACY.md
M README.md
M SECURITY.md
A THIRD_PARTY_NOTICES.md
M app/build.gradle.kts
A app/gradle.lockfile
M app/proguard-rules.pro
A app/src/androidTest/java/id/mybase/aether/PlatformPersistenceTest.kt
A app/src/androidTest/java/id/mybase/aether/PreferencesUiTest.kt
A app/src/androidTest/java/id/mybase/aether/ScreenRegressionTest.kt
A app/src/androidTest/java/id/mybase/aether/UiRegressionTest.kt
M app/src/main/AndroidManifest.xml
A app/src/main/assets/licenses/boringssl-LICENSE.txt
A app/src/main/assets/licenses/conscrypt-LICENSE.txt
M app/src/main/java/id/mybase/aether/MainActivity.kt
M app/src/main/java/id/mybase/aether/adb/AdbConnectionManager.kt
M app/src/main/java/id/mybase/aether/adb/AdbKeyStores.kt
D app/src/main/java/id/mybase/aether/adb/AdbMdnsDiscovery.kt
D app/src/main/java/id/mybase/aether/adb/AdbPairingDiscovery.kt
M app/src/main/java/id/mybase/aether/adb/AdbProtocol.kt
A app/src/main/java/id/mybase/aether/adb/AdbPublicKey.kt
M app/src/main/java/id/mybase/aether/adb/AdbSession.kt
M app/src/main/java/id/mybase/aether/adb/AdbTlsTransport.kt
D app/src/main/java/id/mybase/aether/adb/BcTlsPairingTransport.kt
M app/src/main/java/id/mybase/aether/adb/ConscryptPairingTransport.kt
A app/src/main/java/id/mybase/aether/adb/LocalAdbEndpoint.kt
D app/src/main/java/id/mybase/aether/adb/PairingClientCert.kt
M app/src/main/java/id/mybase/aether/adb/TlsPairingClient.kt
M app/src/main/java/id/mybase/aether/di/AppContainer.kt
M app/src/main/java/id/mybase/aether/domain/Models.kt
M app/src/main/java/id/mybase/aether/packages/PackageManagerRepository.kt
M app/src/main/java/id/mybase/aether/packages/PackageOperationExecutor.kt
M app/src/main/java/id/mybase/aether/packages/PackageRepository.kt
M app/src/main/java/id/mybase/aether/packages/PackagesModule.kt
M app/src/main/java/id/mybase/aether/persistence/AetherPreferences.kt
M app/src/main/java/id/mybase/aether/persistence/InMemoryOperationHistoryStore.kt
M app/src/main/java/id/mybase/aether/persistence/OperationHistoryStore.kt
M app/src/main/java/id/mybase/aether/persistence/SqliteOperationHistoryStore.kt
M app/src/main/java/id/mybase/aether/security/InputValidators.kt
M app/src/main/java/id/mybase/aether/ui/about/AboutScreen.kt
M app/src/main/java/id/mybase/aether/ui/apps/AppDetailsScreen.kt
M app/src/main/java/id/mybase/aether/ui/apps/AppListScreen.kt
M app/src/main/java/id/mybase/aether/ui/components/Badges.kt
M app/src/main/java/id/mybase/aether/ui/components/Common.kt
M app/src/main/java/id/mybase/aether/ui/connection/ConnectionViewModel.kt
M app/src/main/java/id/mybase/aether/ui/dashboard/DashboardScreen.kt
M app/src/main/java/id/mybase/aether/ui/debloat/DebloatScreen.kt
M app/src/main/java/id/mybase/aether/ui/debloat/PreviewDialog.kt
M app/src/main/java/id/mybase/aether/ui/debloat/ProgressScreen.kt
M app/src/main/java/id/mybase/aether/ui/diagnostics/DiagnosticsScreen.kt
M app/src/main/java/id/mybase/aether/ui/history/HistoryScreen.kt
M app/src/main/java/id/mybase/aether/ui/history/HistoryViewModel.kt
M app/src/main/java/id/mybase/aether/ui/navigation/AetherNavHost.kt
M app/src/main/java/id/mybase/aether/ui/onboarding/OnboardingScreen.kt
M app/src/main/java/id/mybase/aether/ui/packages/PackagesViewModel.kt
M app/src/main/java/id/mybase/aether/ui/pairing/PairingDialog.kt
M app/src/main/java/id/mybase/aether/ui/recovery/RecoveryScreen.kt
M app/src/main/java/id/mybase/aether/ui/recovery/RecoveryViewModel.kt
M app/src/main/java/id/mybase/aether/ui/settings/SettingsScreen.kt
M app/src/main/java/id/mybase/aether/ui/settings/SettingsViewModel.kt
M app/src/main/java/id/mybase/aether/ui/setup/SetupGuideScreen.kt
M app/src/main/java/id/mybase/aether/ui/theme/Theme.kt
D app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
D app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
A app/src/main/res/mipmap-anydpi/ic_launcher.xml
A app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
M app/src/main/res/values/colors.xml
A app/src/main/res/xml/backup_rules.xml
A app/src/main/res/xml/data_extraction_rules.xml
A app/src/test/java/id/mybase/aether/adb/AdbBoundaryTest.kt
M app/src/test/java/id/mybase/aether/adb/AdbConnectionManagerTest.kt
M app/src/test/java/id/mybase/aether/adb/AdbKeyStoreTest.kt
A app/src/test/java/id/mybase/aether/adb/BcTlsPairingTransport.kt
M app/src/test/java/id/mybase/aether/adb/TlsPairingClientTest.kt
M app/src/test/java/id/mybase/aether/adb/fakes/FakeAdbServer.kt
M app/src/test/java/id/mybase/aether/domain/DomainTest.kt
M app/src/test/java/id/mybase/aether/packages/ExecutorTest.kt
A app/src/test/java/id/mybase/aether/persistence/InMemoryPreferencesDataStore.kt
A app/src/test/java/id/mybase/aether/persistence/PreferencesTest.kt
M app/src/test/java/id/mybase/aether/security/InputValidatorsTest.kt
M app/src/test/java/id/mybase/aether/ui/connection/ConnectionViewModelTest.kt
M app/src/test/java/id/mybase/aether/ui/fakes/TestFakes.kt
M app/src/test/java/id/mybase/aether/ui/history/HistoryViewModelTest.kt
M app/src/test/java/id/mybase/aether/ui/packages/PackagesViewModelTest.kt
A app/src/test/java/id/mybase/aether/ui/settings/SettingsViewModelTest.kt
M build.gradle.kts
M docs/adb-pairing-protocol.md
M docs/architecture.md
M docs/dependencies.md
A docs/engineering-audit.md
M docs/limitations.md
M gradle/libs.versions.toml
A gradle/verification-metadata.xml
M gradle/wrapper/gradle-wrapper.properties
A scripts/check_dependency_advisories.py
A scripts/inspect_apks.py
A scripts/release_summary.sh
A scripts/test_release_summary.py
A settings-gradle.lockfile
M settings.gradle.kts
```




## Device-aware management phase — 2026-10-26

Baseline `1fe271fa0bdc3a122900e3fc403c5bab15687641` on `main`; initial tree clean.
Development changes remain uncommitted. Earlier audit sections are preserved.
The [new engineering report](device-management-report.md) records scope, exact
root-cause evidence, limitations, all modified files, APK hashes and per-objective
status. Architecture details are in [device detection](device-detection.md),
[debloat catalogue](debloat-catalogue.md), and [package icons](package-icon-loading.md).

The required codebase-memory skill was unavailable during initial inspection;
the user authorized token-saver as the fallback. Kotlin skeletons, targeted reads
and reference queries were used. When a reference query missed Kotlin usages,
a narrow source search supplied the missing evidence. No subagents were used.

Phases proceeded through baseline inspection, selection, icons, Settings,
detection, catalogue, POCO references, UI integration and full regression.
Each implementation phase received focused checks and diff inspection before
continuing. The initially failing individual-protection regression established a
real defect. Physical checkbox uncheck failure and an internal Settings route
were not reproduced; the report distinguishes those limits from verified fixes.

Baseline: 241 JVM tests, 0 failures, six lint warnings per variant.
Final: 264 JVM tests, 0 failures/errors/skips; ktfmtCheck passed; debug/release lint
0 errors and the same six warnings; debug and unsigned release APKs built;
38 instrumentation tests compiled, none executed. Strict dependency verification
passed. `adb devices -l` found no connected device. No new dependency, ADB engine,
package executor or CI workflow changes were made. No package operation was run.

Hardware validation still pending.


## Developer-reported physical validation — 2026-10-26

**USER-VERIFIED ON PHYSICAL DEVICE**: the developer confirms successful manual
testing of the latest implementation on **POCO F6 / Android 16 / HyperOS**.
The confirmation was recorded on 2026-10-26; no separate test timestamp was supplied.
The ten confirmed checks are native icons, automatic device/OEM detection,
device-aware recommendations, safety filters, individual selection/unselection,
Select All/Deselect All, protected-package safeguards, external native Developer
Settings, normal return to AETHER, and existing Wireless ADB/package-management
workflows. See the [hardware validation record](device-management-report.md#hardware-validation--developer-reported-manual-testing).

This supersedes the pending physical-validation status for those checks in the
preceding implementation snapshot. Historical audit evidence is retained.
These results are developer-reported manual testing, not Codex-observed or
automated instrumentation execution. The 38 instrumentation tests remain compiled
and unexecuted; execution is pending. The existing 264 passing JVM tests,
0 lint errors and six existing warnings per variant, APK sizes/checksums and
artifact metadata are preserved. No tests/builds were rerun for this update.
No new package-specific SAFE classification is justified by this report.

A new CI run for the uncommitted implementation and signed-release verification
remain pending. Only documentation changed; no application, ADB or package logic
was modified, and no commit or push was made.
