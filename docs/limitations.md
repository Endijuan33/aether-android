# Known limitations

Honest accounting of what 0.1.0 does not do, cannot do, or has not yet
proven on hardware.

## Hardware validation: POCO manual verification; instrumentation pending

CI has **no physical Android device**. A user-supplied POCO F6 / Android 16 / HyperOS OS3.0 trace now verifies native notification reply, NSD, TLS/SPAKE2 pairing, authenticated transport and actual shell UID 2000 after the V3 identity correction (see the incident report). This is a single manual run; broad device/instrumentation validation remains pending.

On **2026-10-26**, the developer confirmed that the latest native icons,
automatic device/OEM detection, device-aware recommendations, safety filters,
individual selection/unselection, Select All/Deselect All, protected-package
safeguards, external native Developer Settings navigation, normal return to
AETHER, and existing Wireless ADB/package-management workflows behave as expected
on **POCO F6 / Android 16 / HyperOS**. These checks are
**USER-VERIFIED ON PHYSICAL DEVICE**, based on developer-reported manual testing.
This is the confirmation-record date; a separate test timestamp was not supplied.
See the [hardware validation record](device-management-report.md#hardware-validation--developer-reported-manual-testing).
The 38 instrumentation tests remain compiled, with 0 executed; automated execution
and broader OEM coverage remain pending. Existing JVM/lint/artifact results and
package-specific catalogue evidence are unchanged.

Implemented/unit-tested components (socket fixtures and BoringSSL vectors are separate from the successful POCO run):

- Full TLS 1.3 + SPAKE2 pairing, using standalone Conscrypt’s public exporter.
- AndroidKeyStore RSA-PSS identity use by the actual TLS provider.
- CNXN/STLS/TLS/CNXN transport negotiation, shell execution and reconnection.
- All three `pm` mutations with explicit `--user 0`, across Android/OEM versions.
- Compose flows, document-provider export and SQLite/keystore platform tests; their APK is compiled, but execution requires supported device-test infrastructure.

The peer-info RSA encoding is now checked against AOSP’s Android public-key structure, not assumed to be SPKI DER. This resolves the source incompatibility but does not constitute hardware validation.

A green CI build proves compilation and test passage, not on-device
behavior.

## Platform support

- **Android 11+ (API 30) required** for the wireless-debugging workflow —
  TLS-based pairing does not exist on older releases. The app will not
  install below API 30.
- Target / compile SDK 37. OEM skins (Samsung, Xiaomi, etc.) may behave
  differently; the safety catalogue explicitly varies by manufacturer,
  firmware and Android version (see
  [catalogue-provenance.md](catalogue-provenance.md)).

## Discovery

Android NSD discovers `_adb-tls-pairing._tcp.` and `_adb-tls-connect._tcp.` separately. Native notification input is the primary workflow; in-app discovery and advanced manual setup are compatibility fallbacks. User-supplied POCO F6 / Android 16 / HyperOS traces now confirm actual pairing-service resolution and native RemoteInput delivery. The subsequent V3 trace verifies actual pairing, authenticated connection and shell UID 2000 on this device. Other OEMs and lifecycle scenarios remain unvalidated. Android 13+ requires notification permission; SDK 37 devices require runtime local-network access. Disabled notification channels, OEM background restrictions and blocked mDNS can prevent notification pairing.

The user reported pairing discovery stuck on POCO F6 / HyperOS. Submission gating, swallowed resolution failures and the missing per-discovery timeout were repaired; code input is independent of endpoint discovery. The new on-device trace progressed through resolution and reply handling, then localized rejection to TLS_HANDSHAKE before SPAKE2. Source inspection exposed V2's incompatible raw RSA signing authorizations. The V3 identity adds DIGEST_NONE/raw padding for Conscrypt's platform-supported signing delegate, keeping the old keys and SIGN/VERIFY-only/non-exportable storage. A subsequent user-supplied V3 trace now verifies successful pairing/transport and shell UID 2000 on the POCO; independent instrumentation and broader scenarios remain pending. The host reports model 24069PC21G, Android 16/API 36, OS3.0; no authorized ADB test target is connected. See the [incident follow-up](notification-pairing-redesign.md#poco-f6--hyperos-discovery-failure-follow-up).

Pairing uses a short foreground service and a 150-second session deadline. It stops after verified connection, failure or cancellation. The shell then remains process-scoped; there is no indefinite foreground shell service and no restart/replay after process death. Keep AETHER open for package operations. Reconnect explicitly discovers a fresh endpoint using the retained keystore identity. RemoteInput does not expose a portable numeric-keyboard setting; exactly six ASCII digits are validated at submission. JVM strings and SystemUI-owned input cannot be securely zeroed; AETHER clears its mutable buffer and removes the received Intent payload without persisting the code.

## Privileges: no root, by design

- AETHER does not require or use root. It is an ordinary APK; installing
  it grants no shell privileges by itself.
- All package operations run through the wireless-debugging ADB session,
  i.e. the `shell` user — the same privilege level as `adb shell` from a
  paired workstation.
- What `pm` can do at that level: `uninstall --user 0` (removes the app
  for device owner user 0; system partition untouched), `disable-user`,
  `enable`. The app does not expose `clear` or arbitrary shell commands. What it cannot do: touch `/system` partitions,
  remove preinstalled APKs permanently, or grant itself new capabilities.

## Operations and recovery

- `pm uninstall --user 0` is **not fully reversible by this app**: the
  app's data is dropped, and reinstalling requires the original APK
  (Play Store, OEM source, or your own backup). The preview dialog states
  this before confirmation.
- `disable-user` / `enable` are symmetric toggles.
- Operation history records what was done, not a restore point. Export
  the inventory JSON before debloating if you want a reference.

## Release signing

Local release builds are deliberately unsigned; no release signing configuration
or debug-signing fallback is present. The 2026-10-27 pre-checkpoint audit verified
that the repository has zero Actions secrets, so all four required signing
secrets are absent and production signing is **NOT CONFIGURED**. Debug/test APK
signatures verify; unsigned release verification fails as expected. A signed
production artifact and production certificate review remain required. See the
[pre-checkpoint report](pre-checkpoint-validation-report.md) for the four secret
names, secure setup procedure, separate artifact hashes and checkpoint limits.

The isolated offline validation passed 264 JVM tests, formatting, both lint
variants and debug/release/test assembly. Its lint reports contain zero errors
and two trust-manager warnings per variant; the prior six-warning result remains
historical evidence, with no warning claimed fixed. Current-implementation
instrumentation execution and remote CI remain pending. A green CI run at the
committed baseline does not cover these uncommitted implementation changes.

## Catalogue

52 entries, revision `2026-10-24-r1`, curated from AOSP/Play/OEM sources.
Packages without an entry are shown as **unknown**, never auto-classified.
The catalogue is a starting reference, not an authority — see
[catalogue-provenance.md](catalogue-provenance.md).

## Production gates and local threat boundary

The custom BigInteger SPAKE2 implementation is not constant-time; deterministic vectors do not replace cryptographic review. TLS accepts self-signed adbd certificates. Pairing’s code/exporter binds that exchange, while reconnection relies on the user-selected local endpoint and Android’s client-key authorization. A compromised local process/OS can impersonate a listener or abuse an authorized identity; UID and command-output checks cannot authenticate such a malicious peer. No unrelated remote-device shell is accepted.

No foreground/background service maintains ADB after process death. Interrupted mutations are never replayed automatically; unverified durable history is retained. Uninstall is not rolled back here; enable/disable do not restore deleted data or application side effects. Multi-user mutations support user 0 only.

The latest check on this ARM64 host ran the available `adb devices -l`
successfully, but listed no connected devices. No local emulator binary or AVD is
installed, so current device-test execution remains pending. An earlier attempt
with the SDK-bundled adb exited 132; that is historical tool evidence, not the
result of the latest available-adb check. Play distribution still needs signing,
QUERY_ALL_PACKAGES policy review, SDK eligibility, accessibility/device validation
and privacy/release review. AETHER is not declared production-ready or Play
Store-ready.
