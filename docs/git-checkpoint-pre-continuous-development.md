# Git checkpoint before continuous development

This report completes the interrupted backup task. The existing implementation
was preserved byte-for-byte and organized into ten Conventional Commits. The
2026-10-26 continuation adds this recovery report only. No application features,
refactoring, dependency changes, or build fixes are part of this continuation.

## Repository and recovery references

| Item | Value |
|---|---|
| Repository | https://github.com/Endijuan33/aether-android |
| Branch | `main` |
| Original HEAD | `ca653ddf3f738b03723c8c90787d3776e9786acb` |
| Final implementation HEAD | `74aab0888962ea2792d6ff5b36ba11d5da3f24a5` |
| Original-HEAD backup branch | `backup/pre-continuous-development` |
| Annotated implementation tag | `aether-stable-pre-continuous-development` |
| Annotated tag object | `e456175b1b0e514737442b4f1d14580fc596d6d7` |
| Tag's peeled commit | `74aab0888962ea2792d6ff5b36ba11d5da3f24a5` |

The backup branch preserves the original committed baseline. The annotated tag
preserves the completed implementation, including the changes that were
uncommitted when this task started. Neither published reference is moved by this
report. The final `main` HEAD also includes the subsequent documentation commit.
Its exact SHA is recorded in the completion response and local synchronization
receipt; it can be recovered from Git without embedding a commit's own SHA in
its contents:

```bash
git log -1 --format='%H %s' -- docs/git-checkpoint-pre-continuous-development.md
```

## Working-tree archive

- Location: `/home/endcore/aether-backups/aether-working-tree-20261025T065251Z.tar.gz`
- Created: 2026-10-25 06:52:51 UTC.
- Size: 412377 bytes.
- SHA-256: `134184bf1b87f44839a0bec64480133e1ff567ec79398628c8715a8d1d78c876`.
- Checksum sidecar: the archive path followed by `.sha256`.

The archive contains 168 project files under `working-tree/`, plus a checkpoint
manifest, original status, and tracked working-tree patch under `metadata/`.
The manifest records per-file SHA-256 hashes, modes, original untracked files,
and six deleted tracked paths. It preserves source, tests, configuration,
documentation and relevant assets from before the granular commits.

The pre-existing user-owned `AGENTS.md` is included in the local archive and
preserved in the workspace. It is excluded from Git publication through a local
exact-path exclusion. The other 167 files comprise the complete published
implementation tree. Git internals, ignored build/cache artifacts, credentials,
private/signing keys and sensitive local configuration are excluded.

On 2026-10-26 the archive checksum was reverified, the archive was fully
extracted into a temporary directory, and all 168 extracted file hashes and
permissions matched the saved snapshot. The 167 published file contents and
modes also matched the current Git tree; all six recorded deletions remained
absent. The continuation preserves those files and adds only this report.

The archive is a local recovery copy outside the repository; it has not been
uploaded to GitHub. GitHub contains the committed implementation and recovery
references. Keep the archive and its checksum together for local recovery.

## Inspection and commit review

The original session inspected staged, unstaged and untracked changes, deletions,
renames and binary files before publication. Credential/key-pattern scan
candidates were reviewed as test fixtures; no credentials or signing keys were
identified for publication. Each staged diff was reviewed and checked against
the snapshot before its commit. This is a review result, not a guarantee from
the pattern scanner alone.

All ten commit messages and changed-path sets were rechecked against Git during
the continuation. Tests validate the final combined implementation; intermediate
commits are not claimed to have been independently built. A pre-existing blank
line at the end of `docs/engineering-audit.md` was preserved intentionally.

## Granular implementation commits

The following entries are in commit order. File lists use Git's no-rename view,
so moves list both the removed and added paths.

### 1. fix(keystore): authorize non-exportable RSA identity for ADB TLS signing

Commit: [`3edd71d69764ced18557b6406d4afc3dd9af21e8`](https://github.com/Endijuan33/aether-android/commit/3edd71d69764ced18557b6406d4afc3dd9af21e8).

Category: AndroidKeyStore identity. Retain V1/V2 identities, authorize V3 raw RSA/PSS signatures with SIGN/VERIFY only, and fail closed without exportable fallback.

Changed files:

```text
app/src/androidTest/java/id/mybase/aether/AdbIdentityPlatformTest.kt
app/src/main/java/id/mybase/aether/adb/AdbKeyStores.kt
app/src/test/java/id/mybase/aether/adb/AdbKeyStoreTest.kt
```

### 2. fix(adb): enforce authenticated framing and bounded shell transport

Commit: [`997f92be026cc8da0aeea05486094fe68c34fc0b`](https://github.com/Endijuan33/aether-android/commit/997f92be026cc8da0aeea05486094fe68c34fc0b).

Category: ADB protocol and execution boundary. Correct AOSP public-key encoding and CNXN/STLS sequencing; constrain argv/local endpoints and bound cancellation, output and socket cleanup; retain real protocol fixtures.

Changed files:

```text
app/src/main/java/id/mybase/aether/adb/AdbConnectionManager.kt
app/src/main/java/id/mybase/aether/adb/AdbProtocol.kt
app/src/main/java/id/mybase/aether/adb/AdbPublicKey.kt
app/src/main/java/id/mybase/aether/adb/AdbSession.kt
app/src/main/java/id/mybase/aether/adb/AdbTlsTransport.kt
app/src/main/java/id/mybase/aether/adb/ConscryptPairingTransport.kt
app/src/main/java/id/mybase/aether/adb/LocalAdbEndpoint.kt
app/src/main/java/id/mybase/aether/adb/PairingClientCert.kt
app/src/main/java/id/mybase/aether/adb/TlsPairingClient.kt
app/src/main/java/id/mybase/aether/adb/pairing/PairingDiagnostics.kt
app/src/main/java/id/mybase/aether/adb/pairing/WirelessPairingState.kt
app/src/main/java/id/mybase/aether/security/InputValidators.kt
app/src/test/java/id/mybase/aether/adb/AdbBoundaryTest.kt
app/src/test/java/id/mybase/aether/adb/AdbConnectionManagerTest.kt
app/src/test/java/id/mybase/aether/adb/TlsPairingClientTest.kt
app/src/test/java/id/mybase/aether/adb/fakes/FakeAdbServer.kt
app/src/test/java/id/mybase/aether/adb/pairing/PairingDiagnosticBufferTest.kt
app/src/test/java/id/mybase/aether/security/InputValidatorsTest.kt
```

### 3. build(android): pin verified dependencies and compile platform regressions

Commit: [`e038b28275ed9418687f525679f72388aeeea830`](https://github.com/Endijuan33/aether-android/commit/e038b28275ed9418687f525679f72388aeeea830).

Category: Build integrity and dependencies. Pin wrapper distribution/checksum and dependency graph, upgrade Conscrypt/native alignment, move BouncyCastle to JVM fixtures, register formatter and instrumentation dependencies.

Changed files:

```text
.gitignore
THIRD_PARTY_NOTICES.md
app/build.gradle.kts
app/gradle.lockfile
app/proguard-rules.pro
app/src/main/assets/licenses/boringssl-LICENSE.txt
app/src/main/assets/licenses/conscrypt-LICENSE.txt
app/src/main/java/id/mybase/aether/adb/BcTlsPairingTransport.kt
app/src/test/java/id/mybase/aether/adb/BcTlsPairingTransport.kt
build.gradle.kts
gradle/libs.versions.toml
gradle/verification-metadata.xml
gradle/wrapper/gradle-wrapper.properties
settings-gradle.lockfile
settings.gradle.kts
```

### 4. fix(packages): verify user-zero mutations and preserve uncertain history

Commit: [`f9987586a67131343b11449188dbc25674ae81ae`](https://github.com/Endijuan33/aether-android/commit/f9987586a67131343b11449188dbc25674ae81ae).

Category: Package-management safety and inventory. Serialize destructive plans, verify UID and resulting state, protect essential/AETHER targets, durably record pending outcomes, expose partial inventory and refresh recovery/selection.

Changed files:

```text
app/src/androidTest/java/id/mybase/aether/PlatformPersistenceTest.kt
app/src/main/java/id/mybase/aether/domain/Models.kt
app/src/main/java/id/mybase/aether/packages/PackageManagerRepository.kt
app/src/main/java/id/mybase/aether/packages/PackageOperationExecutor.kt
app/src/main/java/id/mybase/aether/packages/PackageRepository.kt
app/src/main/java/id/mybase/aether/packages/PackagesModule.kt
app/src/main/java/id/mybase/aether/persistence/InMemoryOperationHistoryStore.kt
app/src/main/java/id/mybase/aether/persistence/OperationHistoryStore.kt
app/src/main/java/id/mybase/aether/persistence/SqliteOperationHistoryStore.kt
app/src/main/java/id/mybase/aether/ui/packages/PackagesViewModel.kt
app/src/main/java/id/mybase/aether/ui/recovery/RecoveryViewModel.kt
app/src/test/java/id/mybase/aether/domain/DomainTest.kt
app/src/test/java/id/mybase/aether/packages/ExecutorTest.kt
app/src/test/java/id/mybase/aether/ui/fakes/TestFakes.kt
app/src/test/java/id/mybase/aether/ui/packages/PackagesViewModelTest.kt
```

### 5. fix(persistence): expose storage failures and persist onboarding before navigation

Commit: [`8c3026a6e4ab6b438a491806ed4c86d13a22621a`](https://github.com/Endijuan33/aether-android/commit/8c3026a6e4ab6b438a491806ed4c86d13a22621a).

Category: Preferences and history UI. Use injectable DataStore for deterministic tests; surface settings/history busy and error states and await durable onboarding completion.

Changed files:

```text
app/src/androidTest/java/id/mybase/aether/PreferencesUiTest.kt
app/src/main/java/id/mybase/aether/persistence/AetherPreferences.kt
app/src/main/java/id/mybase/aether/ui/history/HistoryScreen.kt
app/src/main/java/id/mybase/aether/ui/history/HistoryViewModel.kt
app/src/main/java/id/mybase/aether/ui/onboarding/OnboardingScreen.kt
app/src/main/java/id/mybase/aether/ui/settings/SettingsScreen.kt
app/src/main/java/id/mybase/aether/ui/settings/SettingsViewModel.kt
app/src/test/java/id/mybase/aether/persistence/InMemoryPreferencesDataStore.kt
app/src/test/java/id/mybase/aether/persistence/PreferencesTest.kt
app/src/test/java/id/mybase/aether/ui/history/HistoryViewModelTest.kt
app/src/test/java/id/mybase/aether/ui/settings/SettingsViewModelTest.kt
```

### 6. fix(ui): make package safety and recovery states accessible

Commit: [`e59b674f2f82bb9f5c8721a0aa36483c101e6c5e`](https://github.com/Endijuan33/aether-android/commit/e59b674f2f82bb9f5c8721a0aa36483c101e6c5e).

Category: Interface, navigation and accessibility. Repair preview safeguards/modal ownership, inventory export and lifecycle observation; expose partial inventory/recovery limits, improve semantics/contrast and clean adaptive-icon resources.

Changed files:

```text
app/src/androidTest/java/id/mybase/aether/ScreenRegressionTest.kt
app/src/androidTest/java/id/mybase/aether/UiRegressionTest.kt
app/src/main/java/id/mybase/aether/ui/about/AboutScreen.kt
app/src/main/java/id/mybase/aether/ui/apps/AppDetailsScreen.kt
app/src/main/java/id/mybase/aether/ui/apps/AppListScreen.kt
app/src/main/java/id/mybase/aether/ui/components/Badges.kt
app/src/main/java/id/mybase/aether/ui/components/Common.kt
app/src/main/java/id/mybase/aether/ui/debloat/DebloatScreen.kt
app/src/main/java/id/mybase/aether/ui/debloat/PreviewDialog.kt
app/src/main/java/id/mybase/aether/ui/debloat/ProgressScreen.kt
app/src/main/java/id/mybase/aether/ui/recovery/RecoveryScreen.kt
app/src/main/java/id/mybase/aether/ui/theme/Theme.kt
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
app/src/main/res/mipmap-anydpi/ic_launcher.xml
app/src/main/res/mipmap-anydpi/ic_launcher_round.xml
app/src/main/res/values/colors.xml
```

### 7. fix(adb): recover NSD resolution with bounded retries and stale guards

Commit: [`ea5d674ac3104bef08128a0089ca0955d1604970`](https://github.com/Endijuan33/aether-android/commit/ea5d674ac3104bef08128a0089ca0955d1604970).

Category: Wireless service discovery. Replace obsolete discovery scaffolding with separate pairing/connect watchers, monitored/legacy resolution fallback, Wi-Fi-loss errors, bounded retries and deterministic cleanup tests.

Changed files:

```text
app/src/main/java/id/mybase/aether/adb/AdbMdnsDiscovery.kt
app/src/main/java/id/mybase/aether/adb/AdbPairingDiscovery.kt
app/src/main/java/id/mybase/aether/adb/pairing/AdbDiscoveryException.kt
app/src/main/java/id/mybase/aether/adb/pairing/AdbServiceDiscovery.kt
app/src/main/java/id/mybase/aether/adb/pairing/DiscoveryLease.kt
app/src/main/java/id/mybase/aether/adb/pairing/NsdAdbServiceDiscovery.kt
app/src/main/java/id/mybase/aether/adb/pairing/NsdDiscoveryPlatform.kt
app/src/main/java/id/mybase/aether/adb/pairing/ResilientNsdDiscovery.kt
app/src/test/java/id/mybase/aether/adb/pairing/DiscoveryLeaseTest.kt
app/src/test/java/id/mybase/aether/adb/pairing/ResilientNsdDiscoveryTest.kt
```

### 8. feat(adb): pair from native notification and verify shell UID

Commit: [`19f52b969e1ce19ce5da46e8446dff421a28b43e`](https://github.com/Endijuan33/aether-android/commit/19f52b969e1ce19ce5da46e8446dff421a28b43e).

Category: Notification pairing and application integration. Publish actionable RemoteInput before discovery, buffer one valid code, serialize/cancel sessions and automatically verify transport/UID; wire shared lifecycle ownership, permission fallback, diagnostics and advanced setup.

Changed files:

```text
app/src/androidTest/java/id/mybase/aether/NotificationPairingUiTest.kt
app/src/androidTest/java/id/mybase/aether/PairingNotificationPlatformTest.kt
app/src/androidTest/java/id/mybase/aether/PairingServicePlatformTest.kt
app/src/main/AndroidManifest.xml
app/src/main/java/id/mybase/aether/MainActivity.kt
app/src/main/java/id/mybase/aether/adb/pairing/AetherAdbPairingService.kt
app/src/main/java/id/mybase/aether/adb/pairing/AetherPairingNotifications.kt
app/src/main/java/id/mybase/aether/adb/pairing/PairingNotificationContent.kt
app/src/main/java/id/mybase/aether/adb/pairing/PairingTransitions.kt
app/src/main/java/id/mybase/aether/adb/pairing/WirelessPairingController.kt
app/src/main/java/id/mybase/aether/di/AppContainer.kt
app/src/main/java/id/mybase/aether/ui/connection/ConnectionViewModel.kt
app/src/main/java/id/mybase/aether/ui/dashboard/DashboardScreen.kt
app/src/main/java/id/mybase/aether/ui/diagnostics/DiagnosticsScreen.kt
app/src/main/java/id/mybase/aether/ui/navigation/AetherNavHost.kt
app/src/main/java/id/mybase/aether/ui/navigation/Routes.kt
app/src/main/java/id/mybase/aether/ui/pairing/PairingActions.kt
app/src/main/java/id/mybase/aether/ui/pairing/PairingDialog.kt
app/src/main/java/id/mybase/aether/ui/pairing/WirelessConnectionCard.kt
app/src/main/java/id/mybase/aether/ui/setup/AdvancedPairingScreen.kt
app/src/main/java/id/mybase/aether/ui/setup/SetupGuideScreen.kt
app/src/main/res/drawable/ic_pairing_notification.xml
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
app/src/test/java/id/mybase/aether/adb/pairing/NotificationPairingProtocolIntegrationTest.kt
app/src/test/java/id/mybase/aether/adb/pairing/WirelessPairingControllerTest.kt
app/src/test/java/id/mybase/aether/ui/connection/ConnectionViewModelTest.kt
```

### 9. ci(android): verify release artifacts and secure signing summaries

Commit: [`d3da73ff628b76f11800010d423bd2d457909254`](https://github.com/Endijuan33/aether-android/commit/d3da73ff628b76f11800010d423bd2d457909254).

Category: GitHub Actions and release tooling. Gate wrapper/formatting/tests/both lint variants/APKs, add emulator regressions and advisory checks, verify final signed artifacts, avoid shell interpolation and clean temporary signing files.

Changed files:

```text
.github/workflows/ci.yml
.github/workflows/dependency-check.yml
.github/workflows/release.yml
scripts/check_dependency_advisories.py
scripts/inspect_apks.py
scripts/release_summary.sh
scripts/test_release_summary.py
```

### 10. docs(audit): record hardening fixes and POCO hardware evidence

Commit: [`74aab0888962ea2792d6ff5b36ba11d5da3f24a5`](https://github.com/Endijuan33/aether-android/commit/74aab0888962ea2792d6ff5b36ba11d5da3f24a5).

Category: Engineering and user documentation. Preserve the historical audit, document notification/NSD/TLS root causes and physical Android 16 UID-2000 success, reconcile setup/security/privacy and retain honest validation limits.

Changed files:

```text
CHANGELOG.md
PRIVACY.md
README.md
SECURITY.md
docs/adb-pairing-protocol.md
docs/architecture.md
docs/dependencies.md
docs/engineering-audit.md
docs/limitations.md
docs/notification-pairing-redesign.md
docs/troubleshooting.md
docs/wireless-debugging-setup.md
```

## Checkpoint documentation commit

Message: `docs(checkpoint): record verified backup and recovery procedures`.
Category: recovery documentation. Changed file:
`docs/git-checkpoint-pre-continuous-development.md`.
This commit records the archive, implementation commits, validation, remote
references and safe recovery procedures. Resolve its SHA with the Git command
above; it is additional to the ten implementation commits.

## Local validation

The original post-commit validation on 2026-10-25 passed at implementation HEAD
`74aab0888962ea2792d6ff5b36ba11d5da3f24a5`:
241 JVM tests, zero failures/errors/skips, formatting, both lint variants,
debug/unsigned-release APK assembly and instrumentation APK compilation.
Each lint variant retained six warnings (version availability and custom trust
manager checks), with zero errors. Four release-summary tests also passed in
that session.

Original evidence:

- `/home/endcore/aether-backups/aether-working-tree-20261025T065251Z-validation.json`
- `/home/endcore/aether-backups/aether-working-tree-20261025T065251Z-validation.log`
- Original log SHA-256: `c92e11d813ebbd730fbaed31a1a119e8da65a8366fb1e6d691377cf36d990553`.

The continuation reran the required validation on the unchanged implementation:

```bash
ANDROID_HOME=/home/endcore/android-sdk ./gradlew ktfmtCheck testDebugUnitTest --rerun lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest --console=plain --max-workers=2
```

Run: 2026-10-25T22:37:32.045889+00:00 to 2026-10-25T22:39:54.484934+00:00; exit code 0.

| Check | Result |
|---|---|
| JVM tests | 241 executed; zero failures, errors or skips |
| Kotlin formatting | Passed (`ktfmtCheck`) |
| Debug lint | 0 errors; 6 warnings |
| Release lint | 0 errors; 6 warnings |
| Debug build | Passed |
| Unsigned release build | Passed; signing remains pending |
| Instrumentation APK | Compiled; device tests not executed locally |

The continuation also reran `python3 -m unittest discover -s scripts -p 'test_*.py'`: all four release-summary tests passed.

Continuation summary: `/home/endcore/aether-backups/aether-checkpoint-resume-20261025T223732Z.json`.

Continuation log: `/home/endcore/aether-backups/aether-checkpoint-resume-20261025T223732Z.log`.

Log SHA-256: `f5c303d673d703867ba0e3cba3e8f473c0ec79fe10de9a2fe63e189f01f21bc9`.

APK SHA-256 values from this local run:

| APK | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | `02935c8aa627b1a3255a8f1fe4eda9e6e8213182330d4c5759ab02fbd77e770c` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `bbd0d6a8400889c4ab9e1219eb4301351beea83e98de614c94ebcff220722e8c` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `fe10b63757c3d2d2970ba9cb66d5c67f94bc5c9e32a9a7d8071e5608deea2a5b` |

The local environment uses JDK 17 and the existing ARM64 Android build setup.
It has no usable emulator/device test target; no new physical-device validation
was performed during this checkpoint task. The earlier user-supplied POCO F6 /
Android 16 / HyperOS trace records successful native notification pairing,
authenticated transport and shell UID 2000; package management was reported
operational by the user. Those observations do not establish all package
mutations or broader device coverage. See [limitations.md](limitations.md).
Broader hardware validation still pending. Local automated success does not
establish production readiness or signed-release readiness.

## GitHub synchronization and CI limitation

The original pushes of `main`, the backup branch and the annotated stable tag
succeeded. The recorded remote verification at 2026-10-25 07:36:31 UTC confirmed
all ten implementation commits were present and the working tree was clean.
On 2026-10-26 the continuation fetched `origin` and reverified that the original
backup branch, tag object and peeled implementation SHA match the table above;
local `main` and `origin/main` matched at `74aab08` before adding this report.
No published history was rewritten and no reference was force-pushed or deleted.

The report is published as a normal additional commit on `main`. Final report
push status, the final HEAD SHA, fetched remote refs and clean-tree verification
are recorded after publication in the completion response and the local receipt
`/home/endcore/aether-backups/aether-checkpoint-completion.json`.

**GitHub CI failed for the preserved implementation.** Run
[40173301636](https://github.com/Endijuan33/aether-android/actions/runs/40173301636)
stopped while configuring Gradle's root-project classpath, before the formatting
task could execute. Dependency verification reported four missing metadata
artifact entries:

- `com.google.guava:guava-parent:33.4.0-jre` — `guava-parent-33.4.0-jre.pom`.
- `org.junit:junit-bom:5.11.0-M2` — `junit-bom-5.11.0-M2.module`.
- `org.jetbrains.kotlin:kotlin-gradle-plugins-bom:2.2.10` — `.module` and `.pom`.

The coordinate/artifact entries are absent from the committed
`gradle/verification-metadata.xml`. A future build-maintenance task must review
publisher/repository evidence, add the appropriate verified metadata and rerun
CI in strict mode. Do not disable dependency verification to bypass this result.
This checkpoint deliberately preserves the existing configuration. The local
checks used the existing local cache/environment and do not prove clean-runner
resolution. No passing remote CI result is claimed for this baseline.

## Safe recovery procedures

Start by inspecting `git status --short`. Preserve any current work separately
before replacing a file or reverting a commit. Recovery into a new worktree or
directory leaves the current checkout intact.

### Inspect the stable implementation

```bash
git fetch origin
git show --no-patch aether-stable-pre-continuous-development
git log --oneline backup/pre-continuous-development..aether-stable-pre-continuous-development
git diff --stat backup/pre-continuous-development aether-stable-pre-continuous-development
```

The stable tag intentionally predates this report. Use current `main` to obtain
the report and the tag to inspect the exact archived implementation.

### Restore one file

Review its existing diff, save any work you need, and replace `path/to/file`
with the intended tracked file:

```bash
git diff -- path/to/file
git restore --source=aether-stable-pre-continuous-development --worktree -- path/to/file
git diff -- path/to/file
```

Review and commit the resulting change normally. This restores file contents
only; it cannot restore Android application data removed by package operations.

### Revert one implementation commit

On a clean worktree, create a new branch and inspect the commit first:

```bash
git switch -c recovery/review-revert main
git show COMMIT_SHA
git revert COMMIT_SHA
```

Replace `COMMIT_SHA` with the reviewed entry above. Resolve any dependency or
merge conflicts, rerun applicable checks, and review before merging. Use
`git revert --abort` to abandon an in-progress conflicting revert safely.

### Create a recovery branch without changing the active checkout

```bash
git worktree add -b recovery/stable-baseline ../aether-stable-recovery aether-stable-pre-continuous-development
```

Choose a new branch and unused destination if those names already exist. For
the original baseline before the ten commits, substitute
`backup/pre-continuous-development` as the starting reference.

### Restore the complete archived working tree

Verify the archive before extracting it into a new empty directory:

```bash
cd /home/endcore/aether-backups
sha256sum -c aether-working-tree-20261025T065251Z.tar.gz.sha256
tar -tzf aether-working-tree-20261025T065251Z.tar.gz
mkdir aether-archive-recovery
tar -xzf aether-working-tree-20261025T065251Z.tar.gz -C aether-archive-recovery
```

The recovered project is under `aether-archive-recovery/working-tree/`; original
status, file metadata and the tracked patch are under `metadata/`. It has no Git
internals or machine-local build/signing configuration. Supply those separately
as needed. The snapshot already represents the intended deletions, so use a new
directory instead of overlaying it on an older checkout. To retain full Git
history, use the stable-tag worktree procedure instead.

Recovery must not rewrite published history or discard unrelated work. This
report recommends new branches/worktrees and ordinary revert commits rather
than destructive resets.
