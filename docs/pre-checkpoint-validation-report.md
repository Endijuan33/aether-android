# AETHER pre-checkpoint validation report

Audit resumed and completed **2026-10-27 (Asia/Jakarta)**. The isolated Gradle
validation completed on 2026-10-26 before the session interruption; its saved
results were recovered and checked against the current files. New supplementary
checks were executed during this continuation. UTC command timestamps are retained
in [the machine-readable record](pre-checkpoint-validation.json).

## A. Executive summary

**READY WITH DOCUMENTED PENDING ITEMS.** No reproducible application regression
or checkpoint-blocking defect was found in this validation scope. The current
implementation passed local compilation, 264 JVM tests, both lint variants,
formatting, debug/release assembly and instrumentation compilation. The four
release-summary tests, workflow syntax checks, runtime advisory lookup and APK
integrity checks also passed.

This recommendation permits review for a stable source checkpoint. It does not
declare a production release ready. Current-implementation remote CI,
instrumentation execution, production signing and the existing broader release
gates remain pending. The ten developer-reported physical-device checks remain
**USER-VERIFIED ON PHYSICAL DEVICE**.

No commits, pushes, workflow dispatches, branch/history changes, new signing keys,
or package mutations were performed. Existing source changes, untracked files and
all three original APKs were preserved. This continuation changes documentation
only; application code, dependencies, SDK versions and workflow configuration are
unchanged.

## B. Repository and environment

- Repository: `https://github.com/Endijuan33/aether-android`.
- Workspace: `/home/endcore/aether-android`.
- Branch: `main`; starting and final HEAD: `1fe271fa0bdc3a122900e3fc403c5bab15687641`.
- Remote `main` was queried through GitHub and has the same HEAD.
- Starting working tree: **21 modified tracked files, 27 untracked files,
  zero staged files**. It contains implementation, tests, assets and documentation;
  it is not a documentation-only diff against HEAD.
- Before documentation changes, SHA-256 comparison matched all **195 tracked or
  nonignored untracked files** against the original audit inventory, in both the
  workspace and the isolated source copy. The interrupted build therefore covers
  the current implementation. No duplicate full build was needed.
- `git fsck --no-reflogs --no-dangling` and `git diff --check`: exit 0.
- Single `:app` module; Gradle wrapper 9.8.0; Java 17.0.20.1; host `aarch64`;
  compile/target SDK 37, minSdk 30, build-tools 36.0.0; Android SDK at
  `/home/endcore/android-sdk`.
- `gh` is installed and authenticated; API reads succeeded. `adb devices -l`
  exits 0 and lists **no devices**. No local emulator binary or AVD definition
  is installed. No device API/ABI can be queried without a target.
- `local.properties` is absent. Repository `gradle.properties` contains ordinary
  build options; user Gradle properties contain the existing local AAPT2 override.
  No signing-property values were printed. This host setup is not proof of
  reproducibility on a fresh CI runner.

The codebase-memory skill was checked, but its graph tools were unavailable.
Token-saver and targeted configuration reads were used; this is a validation
audit, not an exhaustive architecture or call-graph review.

## C. Validation results

| Validation | Result | Evidence and boundary |
|---|---|---|
| JVM unit tests | PASS | **264 passed, 0 failed, 0 errors, 0 skipped**, 28 XML suites; test task forced with `--rerun` |
| Kotlin formatting | PASS | `ktfmtCheck` passed in the full run and again during continuation |
| Debug lint | PASS WITH WARNINGS | **0 errors, 2 warnings** in the isolated offline run |
| Release lint | PASS WITH WARNINGS | **0 errors, 2 warnings** in the isolated offline run |
| Debug APK build | PASS | `assembleDebug`; APK and debug signature inspected |
| Unsigned release build | PASS | `assembleRelease`; deliberately unsigned, without debug-signing fallback |
| Instrumentation compilation | PASS | `assembleDebugAndroidTest`; 38 compiled JUnit test annotations in 10 test classes confirmed using `javap -v` |
| Instrumentation execution | PENDING | **COMPILED — EXECUTION PENDING; 0 executed** for this implementation; no available target |
| Physical-device manual testing | PASS for reported scope | Developer-reported POCO F6 / Android 16 / HyperOS; confirmation 2026-10-26 |
| GitHub Actions CI | PENDING for current diff | Successful baseline CI excludes all uncommitted implementation changes |
| Production release signing | PENDING / NOT CONFIGURED | All four required repository secrets absent; no local signing config |
| APK signature verification | PASS for debug; PENDING for production | Original and isolated debug/test APKs verify; unsigned release verification fails as expected |
| Release-summary tests | PASS | 4 passed, 0 failed/errors/skipped |
| Runtime advisory lookup | PASS | OSV checked 131 resolved Maven coordinates; 0 matching advisories; not a guarantee of no vulnerabilities |
| Workflow static checks | PASS | Three YAML files parse; all external actions use full commit SHAs; 23 Bash blocks pass syntax checks |
| APK archive/native inspection | PASS | Six APKs inspected; ZIP integrity and applicable 16-KiB native/ZIP alignment checks passed; no runtime-loading claim |

### Commands and preserved baseline

Working directory for validation:
`/home/endcore/aether-validation/pre-checkpoint-20261026-206hc72h/source`.

```sh
ANDROID_HOME=/home/endcore/android-sdk ./gradlew :app:tasks --all --offline --console=plain
ANDROID_HOME=/home/endcore/android-sdk ./gradlew ktfmtCheck testDebugUnitTest --rerun lintDebug lintRelease assembleDebug assembleRelease assembleDebugAndroidTest :app:exportRuntimeDependencies --offline --dependency-verification strict --console=plain --max-workers=2
```

Both commands exited **0**. Task discovery took 85.48 seconds; full validation
took 489.84 seconds including process overhead, with Gradle reporting
`BUILD SUCCESSFUL in 8m 8s` and 138 actionable tasks (75 executed, 63 from cache).
Unit-test XML confirms 264 tests, rather than treating cached task counts as tests.

Continuation commands, all exit 0:

```sh
ANDROID_HOME=/home/endcore/android-sdk ./gradlew ktfmtCheck --offline --dependency-verification strict --console=plain --max-workers=2
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/check_dependency_advisories.py
python3 /tmp/aether-precheckpoint-static.py
```

The first three ran in the isolated source copy; the static checker inspected
the workspace. The exact commands, per-command exit codes, durations, artifact
inspection arguments and external API queries are preserved in
[pre-checkpoint-validation.json](pre-checkpoint-validation.json). Local raw logs
and XML reports are under `/home/endcore/aether-validation/pre-checkpoint-20261026-206hc72h`; those local paths are not remote artifacts.

The earlier Device Management result remains **264 JVM tests and six warnings
per lint variant**. Its original XML contains `AndroidGradlePluginVersion` (1),
`NewerVersionAvailable` (3) and `CustomX509TrustManager` (2). The isolated
`--offline` reports contain only the two trust-manager warnings. The four
update-availability notices are absent from that run; no source, dependency or
lint configuration changed, and no warning was suppressed or claimed repaired.
Compiler/Gradle deprecation notices remain in the full build log.

### Instrumentation execution procedure

The runner is `androidx.test.runner.AndroidJUnitRunner`; the configured task is
`connectedDebugAndroidTest`. On a compatible, authorized test target (API 30+),
preferably the CI-equivalent API 35 `google_apis` x86_64 emulator:

```sh
adb devices -l
adb -s SERIAL shell getprop ro.build.version.sdk
adb -s SERIAL shell getprop ro.product.cpu.abi
ANDROID_SERIAL=SERIAL ANDROID_HOME=/path/to/android-sdk ./gradlew connectedDebugAndroidTest --console=plain
```

Replace `SERIAL` and the SDK path with the actual test environment. Retain
`app/build/reports/androidTests` and `app/build/outputs/androidTest-results`.
No connected test was run here; this phase performed no system-package operation.

### GitHub Actions audit

| Workflow | Trigger and coverage | Audit result |
|---|---|---|
| `ci.yml` | Push / pull request to `main`; formatting, JVM tests, both lints, runtime advisories, debug/unsigned release builds, instrumentation compilation, APK checks, and dependent API-35 emulator execution | Configuration reviewed; latest committed-baseline jobs both succeeded |
| `release.yml` | `v*` tag push or manual dispatch; verification, release assembly, optional signing, final APK inspection, checksums and summary | Configuration reviewed; production signing path not exercised |
| `dependency-check.yml` | Weekly Monday 06:00 UTC or manual dispatch; dynamic-version gate, dependency export and OSV lookup | Configuration reviewed; latest committed-baseline run succeeded |

All workflows grant only `contents: read`, pin external actions, use JDK 17 and
the configured SDK/build-tools, and configure Gradle caching. Required build/test
steps fail normally; CI test reports upload with `always()`. Release summary
downloads allow missing artifacts so failures can still be reported. Signing
material lives under runner temporary storage, outside artifact upload paths.
The 23 Bash blocks were syntax-checked without executing their workflow actions.

[CI run 40244540176](https://github.com/Endijuan33/aether-android/actions/runs/40244540176)
is **successful**, including build and Android UI/platform jobs, at
`1fe271fa0bdc3a122900e3fc403c5bab15687641`. The latest
[dependency run 40324558701](https://github.com/Endijuan33/aether-android/actions/runs/40324558701)
also succeeded at that SHA. Older failed CI attempts were superseded by the
successful baseline; no current failed run was hidden.

Neither successful run covers the uncommitted Device Management implementation.
CI has no `workflow_dispatch` trigger. Dispatching Release or Dependency check
would still run committed code, so there is no appropriate remote run for the
current diff without a reviewed commit/push. None was dispatched or pushed in
this phase. After a separately authorized checkpoint/push, monitor the new CI
run and confirm its `headSha` equals the resulting commit, including the emulator
job and reports.

## D. Release-signing audit

**Production signing: NOT CONFIGURED.** The GitHub repository secrets endpoint
returned `total_count: 0`; this conclusion is based on a successful API read,
not merely workflow secret names. The four corresponding environment variables
were also absent locally. No organization or unrelated external credential
store was searched, and no signing material was created or changed.

`app/build.gradle.kts` defines no release `signingConfig`. Local release assembly
intentionally produces an unsigned APK. Debug and instrumentation APKs use the
existing Android debug identity; there is no silent production-to-debug fallback.

The workflow requires all of these repository Actions secrets:

| Secret | Required material |
|---|---|
| `AETHER_KEYSTORE_BASE64` | Base64 encoding of the existing approved production keystore |
| `AETHER_KEYSTORE_PASSWORD` | Keystore password |
| `AETHER_KEY_ALIAS` | Approved signing-key alias |
| `AETHER_KEY_PASSWORD` | Password for that key |

Security findings:

- No hardcoded production signing password or private signing material was found
  in the reviewed build/workflow configuration or the scoped working-tree scan.
- No `.keystore`, `.jks`, `.p12`, `.pfx`, `.pem`, `.env*` or `key.properties`
  file was tracked or present among nonignored untracked files. `.gitignore`
  excludes `local.properties`, `.keystore` and `.jks` files and APK/build outputs.
  This is a scoped heuristic scan, not a guarantee about all history or arbitrary
  secret encodings.
- The only private-key marker was a short, intentionally invalid redaction-test
  fixture in `AdbDiagnosticsTest.kt:48`; it is not a usable production key.
- The workflow checks secret presence without logging values, uses environment
  password indirection for `apksigner`, sets `umask 077`, and cleans temporary
  keystore/signed files with an EXIT trap. Upload paths contain APKs/reports,
  not the temporary keystore. Actual secret-backed signing remains untested.
- The workflow verifies the temporary signed APK before moving it into the CI
  output path and computes checksums afterward. The filename may still say
  `app-release-unsigned.apk`; determine signing from verification and job outcome,
  not the filename. No such replacement occurred in this audit workspace.

Secure setup procedure, for a later authorized release phase:

1. Have the key owner identify the existing production keystore, alias and trusted
   certificate fingerprint. Keep a secure backup outside the repository. Do not
   generate, replace or rotate a key as part of this validation task.
2. Configure the four named repository Actions secrets through GitHub's secrets
   UI or `gh secret set` fed through stdin from an approved secret manager. Keep
   passwords/base64 material out of shell arguments, command history, logs and
   repository files. Do not upload private material as a build artifact.
3. Run the existing release workflow only for an approved committed revision.
   Confirm the signing step actually succeeded, then download the resulting APK
   and checksums. Preserve the existing release application ID `id.mybase.aether`.
4. Run `apksigner verify --verbose --print-certs /path/to/signed.apk`; compare its
   public certificate SHA-256 with the trusted production fingerprint. Verify the
   package/version using `apkanalyzer manifest print /path/to/signed.apk`, record
   its new SHA-256, and complete distribution review. A debug certificate does
   not satisfy production identity verification.

## E. APK artifact integrity

All APKs have minSdk 30. Application APKs retain version **0.1.0 / code 1**;
the test APK manifest declares neither version attribute. `apkanalyzer manifest
print` exited 0 for every APK. Inspection used build-tools 36.0.0 `apksigner
verify --verbose --print-certs` and the existing `scripts/inspect_apks.py`.

### Original artifacts — preserved

Paths below are relative to `/home/endcore/aether-android`.

| Relative APK path | Bytes | SHA-256 | Package ID | Version name / code | Signature verification |
|---|---:|---|---|---|---|
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2738950 | `20b2fdf064c7ef4d0f3a42813b323b4fb0823b070343d642794b1f4d516ed5cb` | `id.mybase.aether.debug.test` | Not declared / not declared | Debug v2 valid; exit 0 |
| `app/build/outputs/apk/debug/app-debug.apk` | 77819871 | `6119e846a918d3d332a920e715706dff8069b6cec93f023c7fde087a8bd766cf` | `id.mybase.aether.debug` | 0.1.0 / 1 | Debug v2 valid; exit 0 |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 13929124 | `d3ad077fa26ca96ef9910283b3fc5058a549658b6fe15f4eb87d65ccce3ac352` | `id.mybase.aether` | 0.1.0 / 1 | Unsigned; expected exit 1 |

### Isolated validation artifacts — separate files

Paths below are relative to `/home/endcore/aether-validation/pre-checkpoint-20261026-206hc72h/source`.

| Relative APK path | Bytes | SHA-256 | Package ID | Version name / code | Signature verification |
|---|---:|---|---|---|---|
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2706945 | `5b7a7d15548a759e2f4c6d4ce6cf7fa9c729c40948d1abba519d4d223315d5c7` | `id.mybase.aether.debug.test` | Not declared / not declared | Debug v2 valid; exit 0 |
| `app/build/outputs/apk/debug/app-debug.apk` | 77400056 | `c4c467be9b093155c04243a0ca45e0b9e49826e9a7d1e8dbe7a5f0bc8484c5c0` | `id.mybase.aether.debug` | 0.1.0 / 1 | Debug v2 valid; exit 0 |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 13929124 | `0d67462eba9dc17e882950395c85caf87212a102d4d90e4f9e1af4232aa9efae` | `id.mybase.aether` | 0.1.0 / 1 | Unsigned; expected exit 1 |

Both debug APK pairs verify with APK Signature Scheme v2. Public certificate DN:
`C=US, O=Android, CN=Android Debug`; certificate SHA-256:
`18ff2a8be8ee42f539eb6d551a14973ffe346d52d9a959013964bff4869c6a4b`.
Release APKs have no v1 signatures or APK signing block. Their expected exit 1
(`DOES NOT VERIFY / Missing META-INF/MANIFEST.MF`) confirms the unsigned state;
it is not production-signature verification success.

Original checksums match the preserved Device Management artifact record.
Whole-APK hashes and some sizes differ in the isolated build. Extracted entry
contents are identical for original/isolated debug and instrumentation APKs;
their container/signing representation differs. Release payload entries differ
only in `META-INF/version-control-info.textproto`: the original records HEAD,
while the source-only validation copy records `NO_SUPPORTED_VCS_FOUND` because
it has no `.git` directory. These are validation artifacts, not replacement
distribution artifacts. No byte-for-byte APK reproducibility claim is made.

## F. Physical-device evidence — preserved

**USER-VERIFIED ON PHYSICAL DEVICE**, from the developer's manual confirmation
recorded **2026-10-26**: **POCO F6 / Android 16 / HyperOS**. A separate test
timestamp, exact HyperOS build and tested APK checksum were not supplied.

| Confirmed check | Status |
|---|---|
| Native application icons | USER-VERIFIED ON PHYSICAL DEVICE |
| Automatic device/OEM detection | USER-VERIFIED ON PHYSICAL DEVICE |
| Device-aware recommendations | USER-VERIFIED ON PHYSICAL DEVICE |
| Application safety filters | USER-VERIFIED ON PHYSICAL DEVICE |
| Individual checkbox selection and unselection | USER-VERIFIED ON PHYSICAL DEVICE |
| Select All and Deselect All | USER-VERIFIED ON PHYSICAL DEVICE |
| Protected-package selection safeguards | USER-VERIFIED ON PHYSICAL DEVICE |
| External native Developer Settings navigation | USER-VERIFIED ON PHYSICAL DEVICE |
| Return to AETHER with expected functionality | USER-VERIFIED ON PHYSICAL DEVICE |
| Existing Wireless ADB and package-management workflows | USER-VERIFIED ON PHYSICAL DEVICE |

Codex did not execute or independently observe those physical-device checks.
They remain valid developer-reported evidence for that scope despite the current
absence of a connected target. They do not establish other-OEM compatibility,
quantitative frame timing, every lifecycle scenario, or named-package removal
safety. Automated instrumentation execution remains separate and pending.

See the [original manual record](device-management-report.md#hardware-validation--developer-reported-manual-testing),
[limitations](limitations.md), and [icon validation boundary](package-icon-loading.md).
The icon document was reviewed and needed no change. Earlier catalogue evidence,
physical records and `device-management-validation.json` were preserved.

## G. Outstanding issues and impact

| Item | Category / impact | Resolution |
|---|---|---|
| 38 instrumentation tests not executed for current implementation | PENDING; validation limitation and release confidence gate, not an observed application defect | Run `connectedDebugAndroidTest` on a compatible target and retain reports |
| Current uncommitted diff absent from remote CI | PENDING; source-checkpoint follow-up | Review and separately authorize checkpoint/push, then require CI for the resulting SHA |
| Four signing secrets absent | PENDING; signed distribution blocker, not a code-checkpoint blocker | Key owner supplies existing approved identity via the documented secret names |
| Production certificate/signature unverified | PENDING; release blocker | Sign an approved build, verify it and compare certificate identity/checksum |
| Two offline lint warnings; six in previous baseline | PASS WITH WARNINGS; known findings retained | Keep trust-boundary review and update-availability review explicit; no suppression or dependency upgrade in this phase |
| Broader device/lifecycle and custom-crypto review | PENDING; existing release-acceptance boundary | Complete scoped device tests and independent review; a single POCO report does not remove these gates |
| QUERY_ALL_PACKAGES policy, SDK eligibility, accessibility/privacy/release review | PENDING; distribution gates from existing limitations | Complete review before distribution; no Play-ready claim |

No confirmed issue in this audit requires an application-code fix before a
reviewed source checkpoint. Pending external checks are not classified as
regressions. No new feature work was started.

## H. Checkpoint readiness assessment

| Area | Status | Basis |
|---|---|---|
| Repository integrity | PASS | Git object check, unchanged HEAD/staging, complete initial inventory and preserved edits |
| Local build stability | PASS | Debug/release/test APK builds passed with strict dependency verification |
| JVM test stability | PASS | 264/264, zero skips/failures/errors |
| Lint | PASS WITH WARNINGS | Zero errors; actual offline and historical warning counts retained |
| Instrumentation status | PENDING | Compilation passed; execution requires a target |
| Physical-device documentation | PASS | All ten developer-reported checks preserved with provenance and limits |
| Current-implementation GitHub CI | PENDING | Committed baseline green; uncommitted changes cannot be dispatched |
| Release-signing security | PASS WITH WARNINGS | Static handling reviewed; production configuration and signing execution pending |
| APK artifact integrity | PASS | Original hashes retained, separate validation artifacts, identities/signatures inspected |
| Documentation consistency | PASS | Current follow-up linked; historical counts/artifacts and manual evidence preserved |
| Secret/credential exposure review | PASS WITH WARNINGS | No usable credential found in scoped review; heuristic/history limitations remain |

**Recommendation: READY WITH DOCUMENTED PENDING ITEMS.** This is readiness for
reviewed source preservation, not signed production distribution.

## I. Next actions

1. Review the complete existing implementation diff and this report; explicitly
   retain the documented pending instrumentation, CI and release items in the
   checkpoint description.
2. In the next separately authorized Git phase, create the reviewed checkpoint
   and push it through the existing CI path. This task creates neither a commit
   nor a push. Confirm the new run's SHA and all jobs before calling remote
   validation complete; investigate any failure without conflating it with the
   older successful baseline.
3. Execute the current instrumentation suite on the CI emulator or another
   compatible authorized target. Before signed distribution, configure the
   existing production identity, verify the signed artifact and complete the
   remaining release reviews above.

## Working-tree inventory and audit outputs

Original audit inventory (all preserved; ` M` means unstaged modification and
`??` means untracked):

```text
 M CHANGELOG.md
 M app/src/androidTest/java/id/mybase/aether/ScreenRegressionTest.kt
 M app/src/main/java/id/mybase/aether/di/AppContainer.kt
 M app/src/main/java/id/mybase/aether/ui/apps/AppDetailsScreen.kt
 M app/src/main/java/id/mybase/aether/ui/apps/AppListScreen.kt
 M app/src/main/java/id/mybase/aether/ui/dashboard/DashboardScreen.kt
 M app/src/main/java/id/mybase/aether/ui/debloat/DebloatScreen.kt
 M app/src/main/java/id/mybase/aether/ui/debloat/PreviewDialog.kt
 M app/src/main/java/id/mybase/aether/ui/history/HistoryScreen.kt
 M app/src/main/java/id/mybase/aether/ui/navigation/AetherNavHost.kt
 M app/src/main/java/id/mybase/aether/ui/navigation/Routes.kt
 M app/src/main/java/id/mybase/aether/ui/navigation/ViewModelHelpers.kt
 M app/src/main/java/id/mybase/aether/ui/onboarding/OnboardingScreen.kt
 M app/src/main/java/id/mybase/aether/ui/packages/PackagesViewModel.kt
 M app/src/main/java/id/mybase/aether/ui/pairing/PairingActions.kt
 M app/src/main/java/id/mybase/aether/ui/recovery/RecoveryScreen.kt
 M app/src/main/java/id/mybase/aether/ui/setup/SetupGuideScreen.kt
 M app/src/test/java/id/mybase/aether/ui/packages/PackagesViewModelTest.kt
 M docs/catalogue-provenance.md
 M docs/engineering-audit.md
 M docs/limitations.md
?? app/src/androidTest/java/id/mybase/aether/DeveloperSettingsPlatformTest.kt
?? app/src/androidTest/java/id/mybase/aether/PackageIconPlatformTest.kt
?? app/src/main/assets/device-catalogue.json
?? app/src/main/assets/device-catalogue.sha256
?? app/src/main/java/id/mybase/aether/device/AndroidDeviceInfoProvider.kt
?? app/src/main/java/id/mybase/aether/device/DeviceProfile.kt
?? app/src/main/java/id/mybase/aether/packages/DeviceAwareSafetyCatalogue.kt
?? app/src/main/java/id/mybase/aether/packages/DeviceCatalogue.kt
?? app/src/main/java/id/mybase/aether/packages/DeviceCatalogueParser.kt
?? app/src/main/java/id/mybase/aether/packages/DeviceCatalogueRepository.kt
?? app/src/main/java/id/mybase/aether/packages/IconCache.kt
?? app/src/main/java/id/mybase/aether/packages/PackageIconProvider.kt
?? app/src/main/java/id/mybase/aether/ui/apps/DeviceRecommendationsScreen.kt
?? app/src/main/java/id/mybase/aether/ui/apps/PackageFilters.kt
?? app/src/main/java/id/mybase/aether/ui/components/PackageIcon.kt
?? app/src/main/java/id/mybase/aether/ui/pairing/DeveloperOptionsStatus.kt
?? app/src/main/java/id/mybase/aether/ui/pairing/DeveloperSettingsNavigator.kt
?? app/src/test/java/id/mybase/aether/device/DeviceProfileDetectorTest.kt
?? app/src/test/java/id/mybase/aether/packages/DeviceCatalogueIntegrationTest.kt
?? app/src/test/java/id/mybase/aether/packages/DeviceCatalogueTest.kt
?? app/src/test/java/id/mybase/aether/packages/IconCacheTest.kt
?? app/src/test/java/id/mybase/aether/ui/DeveloperSettingsNavigatorTest.kt
?? docs/debloat-catalogue.md
?? docs/device-detection.md
?? docs/device-management-report.md
?? docs/device-management-validation.json
?? docs/package-icon-loading.md
```

This continuation adds `docs/pre-checkpoint-validation-report.md` and
`docs/pre-checkpoint-validation.json`, and updates only the current validation
notes in `README.md`, `docs/device-management-report.md` and `docs/limitations.md`.
The final working tree has **22 modified tracked files, 29 untracked files,
zero staged files**. Existing implementation edits remain uncommitted.

Raw evidence: `/home/endcore/aether-validation/pre-checkpoint-20261026-206hc72h/evidence`. Important records include `before.json`, `commands.json`,
`local-validation.log`, the three `resume-*-commands.json` records,
`resume-measurements.json`, `resume-static-audit.json`, original/validation
artifact metadata and signature logs. The in-repository JSON preserves the
essential measurements if those local logs later become unavailable.
