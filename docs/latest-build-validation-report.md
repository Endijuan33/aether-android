# Latest build validation and repair report

Audit date: **2026-10-27 (Asia/Jakarta)**; evidence timestamps use UTC.

## A. Executive summary

**ALL 38 INSTRUMENTATION TESTS PASSED** on repaired commit
`b8dc257652ee399029ab631c4bbe57a8cace5d17`, GitHub Actions run
[40415583953, attempt 2](https://github.com/Endijuan33/aether-android/actions/runs/40415583953/attempts/2).
The uploaded XML contains 38 executed test cases, **38 passed, 0 failed,
0 errors and 0 skipped**, including both originally failing methods.
The stable checkpoint's original run remains historical evidence of **36 passed,
2 failed, 0 errors and 0 skipped**; that result has not been rewritten.

A confirmed Compose state-observation defect kept the recommendations preview
summary stale after selection. The repair makes selection and catalogue changes
invalidate the composition that computes the summary. The existing device
regression retains its assertions and adds explicit checkbox/deselection checks.
The CI emulator setup now enables and verifies Developer Options before testing
external Settings navigation.

The final local suite passed: **138 Gradle tasks executed**, 264 JVM tests passed,
both APK variants built, and all 38 instrumentation methods compiled. Both
online lint variants report 0 errors and 6 warnings. Four release-summary tests,
the 131-coordinate advisory audit and APK integrity checks passed.
Local instrumentation execution remains **PENDING — NO COMPATIBLE DEVICE**.
Actual device-test execution was performed by CI on an Android 15/API 35
Google APIs x86_64 emulator. The repair commits were reviewed, committed and
pushed without rewriting history. The stable tag, backup branches and original
APKs remain intact. Production signing and broader physical-device coverage are
still pending. Build assessment: **BUILD PASSED WITH DOCUMENTED PENDING ITEMS**.

## B. Source information and preservation

| Item | Value |
| --- | --- |
| Repository | https://github.com/Endijuan33/aether-android |
| Developer branch | `main` |
| Tested baseline commit | `9507b848bcaba24a81a5e85bebb60b15a96b4fc9` |
| Stable tag | `aether-stable-device-management-v1` |
| Annotated tag object | `4714f5475053abe6bd3c89067d19e8ea79b02188` |
| Tag target | `9507b848bcaba24a81a5e85bebb60b15a96b4fc9` |
| Initial working tree | Local-build phase started clean; CI follow-up preserved three modified repair files and this untracked report |
| Fetch result | No divergence before repair push; `origin/main` fast-forwarded from the stable checkpoint to the repaired commit |
| CI-tested repair commit | `b8dc257652ee399029ab631c4bbe57a8cace5d17` |
| Git policy | Repair commits pushed normally; stable checkpoint/tag and backup branches unchanged |
| Documentation | This report records the verified repair commit; its subsequent documentation-only commit does not alter tested source or workflow |

Builds use a detached worktree at:

`/home/endcore/aether-validation/latest-build-20261026T201148Z-fep3fqq5/source`

Full logs, command arguments, timestamps and exit codes are retained at:

`/home/endcore/aether-validation/latest-build-20261026T201148Z-fep3fqq5/evidence`

Each captured command has a `<label>.log` and `<label>.json` sidecar.
`source-state.json`, `environment.json`, `repair.patch`, initial test/lint
reports and downloaded CI XML/logcat reports provide separate evidence.
Build outputs are outside the developer worktree.

A pre-edit tracked-source archive, `evidence/baseline-source.tar.gz`, was created
from the baseline and verified by reading every one of its 198 files through
the gzip/tar reader. SHA-256:

`835612f1b0a4d5557dd3f3e101f3b6e7e8529a97104ad433c646eb6517099d93`

Existing stable backup branches, tags and archives remain unchanged. Original
developer-worktree APK sizes/hashes were recorded in `original-apks.json` and
verified unchanged after the final builds. Remote `main`, the annotated tag
object and its target were independently checked with `git ls-remote`; they
matched the baseline before repair publication. After publication, only `main`
advanced; the stable tag and backup branch stayed at their original objects.
Both worktrees contain identical repaired source and workflow bytes.

The CI follow-up preserved all four initially uncommitted files in a verified
archive before further changes:

`/home/endcore/aether-validation/instrumentation-20261026T222755Z-y47il28e/pre-existing-repairs.tar.gz`

Archive SHA-256:
`e1d7c0b47eb3626ad38b63e8b1ce75302b313849ecb47fe87edcef545deee055`.
All four archived file hashes matched their originals. Follow-up logs, metadata,
XML/logcat artifacts and command exit codes are in:

`/home/endcore/aether-validation/instrumentation-20261026T222755Z-y47il28e/evidence`

## C. Environment and configuration

| Component | Validated configuration |
| --- | --- |
| OS | Ubuntu 24.04.5 LTS, Linux 6.17.0-PRoot-Distro, aarch64 |
| JDK | Ubuntu OpenJDK 17.0.20.1+1-1-24.04; Java/Kotlin target 17 |
| Gradle wrapper | 9.8.0; embedded Kotlin 2.4.10 |
| Android Gradle Plugin | 9.4.1; built-in Kotlin support |
| Kotlin Compose plugin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| Formatter | ktfmt 0.65; existing SHA-256 verification task retained |
| Android SDK | `/home/endcore/android-sdk` |
| Compile / target / minimum SDK | 37 / 37 / 30 |
| Compile platform | `platforms/android-37.0`, revision 2 |
| Selected build-tools | 36.0.0 |
| Other installed platforms / build-tools | Platforms 33, 35, 36; build-tools 35.0.0 |
| SDK command-line tools | 12.0 |
| SDK platform-tools | 37.0.1 |
| Available adb | 1.0.41, 37.0.0-android-tools, Termux ARM64 executable |
| Emulator / AVD / connected device | None locally |
| AAPT2 | Existing machine-only override `/home/endcore/aapt2-wrapper/aapt2` |
| Test runner | `androidx.test.runner.AndroidJUnitRunner` |
| Release configuration | R8/resource shrinking enabled; no release signingConfig |
| Application identity | `id.mybase.aether`; debug suffix `.debug`; version 0.1.0 (1) |

`gradlew`, wrapper properties, root/settings/app Gradle configuration, version
catalog, Gradle properties and all three Actions workflows were reviewed.
The wrapper specifies distribution SHA-256
`bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.
Repositories are Google Maven, Maven Central and Gradle Plugin Portal, plus an
exclusive GitHub Ivy repository for the checksum-verified ktfmt artifact.
Dependency locks and strict dependency verification remain enabled.

`./gradlew tasks --all` succeeded before validation. `:app:test` covers the
available `:app:testDebugUnitTest`; this configuration exposes no release JVM
test task. Both release/debug lint and assembly tasks exist, as do
`assembleDebugAndroidTest`, `connectedDebugAndroidTest` and
`:app:exportRuntimeDependencies`.

No demonstrated toolchain incompatibility required an upgrade. Existing compiler,
Gradle deprecation, ARM AAPT2 override and lint warnings were retained, not
suppressed. Repository Actions secrets list is empty, and all four production
signing variables are absent in the local environment.

## D. Initial validation results

All command logs mentioned below are relative to the evidence directory.

| Check / evidence label | Original result |
| --- | --- |
| `task-inventory` | PASS, exit 0 |
| `initial-gradle` | FAIL, exit 1: isolated worktree had no SDK location; task graph could not resolve `:app:testDebugUnitTest` |
| `initial-gradle-sdk-retry` | PASS, exit 0: formatting, all available JVM tests, both lint variants, debug/release APK assembly, instrumentation APK assembly, dependency export |
| JVM XML in `initial-results/test-results` | 264 tests; 0 failures, errors or skipped |
| Initial online lint XML | Each variant: 0 errors, 6 warnings |
| `initial-release-summary` | PASS, exit 0: 4 Python tests |
| `initial-dependency-audit` | PASS, exit 0: OSV queried 131 resolved Maven coordinates; zero matching advisories |
| Local device inventory | adb succeeded; no devices; emulator/AVDs absent |
| Checkpoint GitHub build job | SUCCESS for the tested SHA |
| Checkpoint GitHub instrumentation job | FAILURE; 38 executed, 36 passed, 2 failed |

GitHub run:
https://github.com/Endijuan33/aether-android/actions/runs/40379385936

- Build job: `121771175461`.
- Instrumentation job: `121774534375`; API 35 Google APIs x86_64 emulator.
- Instrumentation artifact: `12676739950`, downloaded and parsed.
- XML: `ci-instrumentation/outputs/androidTest-results/connected/debug/TEST-test(AVD) - 15.xml`.
- Per-test status and stack traces: `ci-test-summary.json`.

The initial complete local command, after configuring the SDK, was:

```bash
ANDROID_HOME=/home/endcore/android-sdk ./gradlew \
  ktfmtCheck :app:test lintDebug lintRelease \
  assembleDebug assembleRelease assembleDebugAndroidTest \
  :app:exportRuntimeDependencies \
  --console=plain --stacktrace --continue --max-workers=2 \
  --no-build-cache --dependency-verification=strict
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/check_dependency_advisories.py
```

`--continue` lets independent tasks finish after a failure; it does not change
Gradle's failing exit status. Only one Gradle build process ran at a time.

## E. Failure analysis and repair

### 1. Isolated build could not locate the Android SDK

- Task graph: `:app:testDebugUnitTest`.
- Error: `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable...`.
- Classification: local environment configuration; blocks all dependent builds.
- Cause: the detached worktree correctly excluded ignored `local.properties`,
  and the shell had no `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
- Correction: pass `ANDROID_HOME=/home/endcore/android-sdk` to validation commands.
  No developer configuration was copied, changed or committed.
- Verification: the full initial matrix passed on retry.

### 2. CI recommendations preview did not reflect selection

- Task: `:app:connectedDebugAndroidTest`.
- Test: `ScreenRegressionTest.recommendationsNeverSelectAutomaticallyAndUseSharedSelection`.
- Failure: no semantics node with text `Preview (1)` at original line 195.
- Expected: selecting Alpha immediately produces an enabled `Preview (1)`.
  Actual: the assertion found no node with that text.
- Classification: application UI state-observation defect; blocks CI and can
  leave preview disabled/stale after changing selection.
- Root cause: `selectionSummary(apps)` reads ViewModel StateFlow values outside
  Compose observation. Delegated `state` and `snapshot` values were read only in
  child composition scopes. Selection changes can leave the visible app list
  equal, so the screen scope computing the summary does not recompose.
- Correction: read the lifecycle-collected state and catalogue snapshot directly
  in the screen scope before computing the summary. Safety classification and
  operation execution logic are unchanged.
- Regression: preserve the existing failing test, additionally assert checkbox
  off/on state and deselection returning preview to zero/disabled.
- Verification: the strengthened test passed in the complete 38-test CI suite
  on the repaired commit (0.948 seconds in the XML). This was a Compose
  state-observation defect, not classified as flaky or an emulator failure.

### 3. CI did not observe the external Developer Settings window

- Task: `:app:connectedDebugAndroidTest`.
- Test: `DeveloperSettingsPlatformTest.actualSettingsActivityOpensOutsideAetherAndCanReturn`.
- Error: `java.lang.AssertionError: Native Settings window was not observed`
  at original line 62.
- Expected: a native Settings package owns the active window after navigation.
  Actual: Settings briefly opened, returned to the launcher, and the test's
  bounded observation never saw the expected active window.
- Classification: missing emulator test precondition; CI blocker, not evidence
  that the production Settings intent failed to launch.
- Evidence: logcat records the exported
  `com.android.settings/.Settings$DevelopmentSettingsActivity` opening at
  18:47:43.457 UTC, its development fragment drawing, and the launcher moving
  back to the front at 18:47:43.893. AOSP Android 15 explicitly finishes this
  fragment when Developer Options is disabled. CI configured an emulator but
  did not establish that setting.
- Correction: enable `development_settings_enabled` and assert its value is
  `1` inside the disposable emulator's CI script, before the unchanged device
  test suite. Add `--stacktrace` to retain future Gradle failure details.
  No Settings toggle was executed on a developer physical device.
- Verification: the fresh CI emulator logged the setting as `null` (unset) at
  23:05:58.925 UTC. The workflow set it to `1` and verified the read-back before
  running tests. The unchanged Settings regression then passed (1.474 seconds).
  AOSP `DevelopmentSettingsEnabler` defaults an absent flag to disabled on
  non-`eng` builds; the development fragment immediately finishes when disabled.
  This establishes the missing system-setting precondition as the supported
  cause. The original run did not record the flag, which remains an explicit
  limit of the historical evidence. No delay or assertion weakening was added.

AOSP reference:
https://android.googlesource.com/platform/packages/apps/Settings/+/android-15.0.0_r1/src/com/android/settings/development/DevelopmentSettingsDashboardFragment.java

Default-value reference:
https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/packages/SettingsLib/src/com/android/settingslib/development/DevelopmentSettingsEnabler.java

### 4. Artifact download failed on this host

- Command: `gh run download`, evidence `ci-artifact-download`, exit 1.
- Error: temporary ZIP creation under `/data/local/tmp` failed because the
  directory did not exist.
- Classification: local GitHub CLI environment; diagnostic retrieval blocker.
- Correction: retry with `TMPDIR=/tmp`; exit 0. No repository change.

### 5. Added regression assertions initially failed formatting

- Task: `ktfmtCheck` in `affected-checks`, exit 1.
- Cause: line wrapping of the added fluent assertions in
  `ScreenRegressionTest.kt`; source/test assembly still succeeded with
  `--continue`.
- Correction: run the existing `ktfmtFormat` task with only that file selected.
- Verification: `affected-checks-retry` ran `ktfmtCheck assembleDebugAndroidTest`
  successfully, exit 0.

### 6. First repair CI attempt failed before instrumentation execution

- Run `40415583953`, attempt 1, instrumentation job `121899065025`.
- Command: `sdkmanager --install emulator --channel=0`.
- Error: `An error occurred while preparing SDK package Android Emulator: Error on ZipFile unknown archive`.
- Classification: external emulator package installation failure. The emulator
  never booted, the Gradle instrumentation task never started, and no test XML
  or instrumentation artifact existed. This is not a test failure count.
- Action: rerun the failed job on the exact same commit with
  `gh run rerun 40415583953 --failed`. No application or workflow changes were
  made to hide the error.
- Result: attempt 2 installed the emulator, ran the entire suite and passed all
  38 tests. The installation failure and failed artifact lookup remain recorded
  in `repair-job-log.log` and `repair-run-artifacts.log`.

## F. Changes implemented

| File | Change |
| --- | --- |
| `app/src/main/java/id/mybase/aether/ui/apps/DeviceRecommendationsScreen.kt` | Observe state/catalogue at the scope computing the preview summary |
| `app/src/androidTest/java/id/mybase/aether/ScreenRegressionTest.kt` | Strengthen the existing selection regression with checkbox and deselection assertions |
| `.github/workflows/ci.yml` | Establish and verify the emulator Developer Options precondition; collect stack traces |
| `docs/latest-build-validation-report.md` | This engineering report |

Repair diff before this new report: **3 files, 25 insertions, 4 deletions**.
No dependencies, SDK versions, application identity, permissions, production
signing configuration or package-safety rules changed. No pairing/transport/pm
execution code changed. No tests were disabled, skipped or weakened.

| Commit | Message | Scope |
| --- | --- | --- |
| `95a9114a78bbcd507af51828433d179eb42f2a32` | `fix(apps): refresh recommendation selection summary` | Screen and existing regression test |
| `b8dc257652ee399029ab631c4bbe57a8cace5d17` | `fix(ci): enable Developer Options for instrumentation` | CI precondition, initial-value diagnostic and stack traces |

## G. Final validation results

The full Gradle command from section D was repeated with `--rerun-tasks`,
forcing task execution with build-cache reuse disabled. It ran from
**2026-10-26 20:55:10 UTC to 21:05:14 UTC**, exited **0**, and reported
**138 actionable tasks: 138 executed**. Application/test source bytes have not
changed since this run. The CI follow-up additionally logged the emulator flag's
initial value and checked the modified YAML/shell syntax.

Before committing/pushing, all requested local tasks were invoked again, with
`:app:testDebugUnitTest --rerun` forcing a fresh 264-test execution. That run
completed at **22:32:18 UTC**, exit 0: **137 tasks, 6 executed and 131 up-to-date**.
Four release-summary tests also passed again. Local device discovery still
listed no devices. These additional logs are `local-validation`,
`local-jvm-summary.json`, `local-release-summary` and `local-lint-summary.json`
in the follow-up evidence directory.

| Validation | Result | Details |
| --- | --- | --- |
| Formatting | PASS | `ktfmtCheck`, including formatter checksum verification |
| JVM tests | PASS | `:app:test` / `:app:testDebugUnitTest`; 264 tests, 0 failures, 0 errors, 0 skipped |
| Debug lint | PASS | `lintDebug`; 0 errors, 6 warnings, online |
| Release lint | PASS | `lintRelease`; 0 errors, 6 warnings, online |
| Debug build | PASS | `assembleDebug`; new APK inspected |
| Release build | PASS | `assembleRelease`; R8/resource shrinking completed; APK intentionally unsigned |
| Instrumentation compilation | PASS | `assembleDebugAndroidTest`; unchanged 38-method suite with strengthened assertions compiles |
| Instrumentation execution | PASS | CI attempt 2: **38 passed, 0 failed, 0 errors, 0 skipped**; local execution remains **PENDING — NO COMPATIBLE DEVICE** |
| Dependency checks | PASS | Strict Gradle verification/locks; runtime graph exported; dynamic-version gate passed; OSV: 131 coordinates, 0 matching advisories |
| APK signature verification | PENDING | Debug and test APKs verify with v2 signatures; production release is unsigned |
| Release-summary tests | PASS | 4 Python tests, exit 0 |
| APK ZIP/native alignment | PASS | All three APK ZIPs valid; existing inspector verified native LOAD/ZIP alignment for 16-KiB pages |
| Workflow/script checks | PASS | All workflow YAML parsed; wrapper/release/emulator shell syntax checked; existing dynamic-version shell gate executed |
| Git/diff integrity | PASS | `git diff --check`; stable refs and original APKs preserved; no staged changes |
| GitHub CI for repairs | PASS | Run `40415583953`, attempt 2, exact repaired commit `b8dc257652ee399029ab631c4bbe57a8cace5d17` |

Evidence: `final-gradle`, `final-release-summary`, `final-dependency-audit`,
`final-dependency-graph`, `final-workflow-validation`,
`final-device-inventory`, `final-apk-inspection`, and per-APK manifest/signature
logs, with exact commands and exit codes in their JSON sidecars.
`:app:dependencies --configuration releaseRuntimeClasspath :app:buildEnvironment`
also completed successfully with strict verification (exit 0).
Workflow parsing/shell checks are distinct from the actual GitHub execution
verified below.

Each lint variant retains two `CustomX509TrustManager` warnings, one Gradle
update warning and three BouncyCastle test-dependency update warnings. These
warnings were not suppressed or fixed. The historical offline count of two is
not the result of this online run. Zero matching OSV advisories does not prove
the absence of vulnerabilities.

## H. APK artifacts

Artifact paths below are relative to this exact isolated source directory:

`/home/endcore/aether-validation/latest-build-20261026T201148Z-fep3fqq5/source`

| Artifact | Relative path | Bytes | Application ID | Version name / code | Signing |
| --- | --- | ---: | --- | --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 77,400,056 | `id.mybase.aether.debug` | 0.1.0 / 1 | Verified debug v2 signature, exit 0 |
| Release | `app/build/outputs/apk/release/app-release-unsigned.apk` | 13,929,124 | `id.mybase.aether` | 0.1.0 / 1 | Unsigned; verification exit 1 |
| Instrumentation | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2,707,597 | `id.mybase.aether.debug.test` | Not declared in test manifest | Verified debug v2 signature, exit 0 |

SHA-256:

- Debug: `97221f056d1b136194d04bdc9098d940a70f0fb6bdf0dde90603e362b8692d3a`
- Release: `2cc84c341bb9d4f2e9800f3935d36f63da10da31d3b7e53a8dd4cfcc0759f707`
- Instrumentation: `f3ee7c245fa98f0b505be5ccada7c6b17d1a099532d146f95b8c3210c5d8d7c9`

Identity/version values were parsed from `apkanalyzer manifest print` output,
not inferred from filenames. APK hashes, native alignment and ZIP integrity
come from `scripts/inspect_apks.py`.

Both debug signatures use the existing Android Debug certificate with SHA-256:
`18ff2a8be8ee42f539eb6d551a14973ffe346d52d9a959013964bff4869c6a4b`.
The release verification result is explicitly **DOES NOT VERIFY** with
`ERROR: Missing META-INF/MANIFEST.MF`. This is the expected unsigned-release
result, not a successful signature check or a build-breaking defect.
The release is not production-ready. No signing key was generated, rotated,
overwritten, exposed or committed. Detailed artifact records are in
`evidence/final-apks.json`.

## I. Remaining issues

1. Local instrumentation execution remains **PENDING — NO COMPATIBLE DEVICE**;
   CI execution is now verified. Broader API/OEM and physical-device regression
   coverage remains pending; emulator tests do not prove live ADB/package operations.
2. Configure the four production signing secrets securely when authorized:
   `AETHER_KEYSTORE_BASE64`, `AETHER_KEYSTORE_PASSWORD`, `AETHER_KEY_ALIAS`,
   `AETHER_KEY_PASSWORD`. Sign and verify a separate production release artifact.
3. Review the existing lint/compiler/Gradle warnings in their own scope; no
   warning-driven upgrades or suppressions were introduced.

Ten developer-reported checks on POCO F6 / Android 16 / HyperOS remain historical
manual evidence from 2026-10-26. They were not rerun for these repairs.
The pre-checkpoint baseline remains 264 JVM tests, formatting/debug/release builds
passed, offline lint 0 errors/2 warnings per variant, four release-summary tests,
131 dependencies with zero matching advisories, 38 instrumentation tests compiled
with execution pending at that time, verified debug signature and unsigned
release. The newly retrieved CI execution supersedes only the historical
instrumentation/CI pending status for the stable SHA. The new successful CI run
separately establishes the repaired source's emulator result. The historical
stable checkpoint report and tag were not modified.

### Verified GitHub execution

| Item | Verified value |
| --- | --- |
| Run URL | https://github.com/Endijuan33/aether-android/actions/runs/40415583953/attempts/2 |
| Tested commit | `b8dc257652ee399029ab631c4bbe57a8cace5d17` |
| Conclusion | SUCCESS |
| Instrumentation job | `121902162362` |
| Task | `:app:connectedDebugAndroidTest` |
| Emulator | Android Emulator 37.2.12.0, build 16428233; AVD `test` |
| Image | `system-images;android-35;google_apis;x86_64` (Android 15/API 35) |
| Readiness | Existing action waited for `sys.boot_completed`; boot timeout 600 seconds; 45-minute job timeout |
| Configuration | KVM enabled; animations disabled; existing image/API/timeouts retained |
| Test counts | 38 executed; 38 passed; 0 failed; 0 errors; 0 skipped |
| Report artifact | `instrumentation-reports`, ID `12695329126`, 334,892 bytes |
| XML | `repair-run-instrumentation-attempt-2/outputs/androidTest-results/connected/debug/TEST-test(AVD) - 15.xml` |

The XML's suite totals were checked against all 38 individual `testcase`
elements, with no failure/error/skipped children. Both originally failing
methods are present and passed. `verified-instrumentation.json` records every
test name; `repair-run-final.log` records the exact GitHub commit, attempt and
success conclusion. Per-test logcat and HTML reports are retained with the XML.
The original failures are evidenced by the original run; local hardware
reproduction was unavailable. The repairs were verified through the new full
emulator suite rather than by labeling either test flaky.

## J. Final assessment

**ALL 38 INSTRUMENTATION TESTS PASSED**

Build assessment remains **BUILD PASSED WITH DOCUMENTED PENDING ITEMS** because
production signing and broader hardware coverage remain outstanding. Both
original instrumentation failures are resolved in the new CI execution.
The reviewed repair commits are synchronized to GitHub through a normal
fast-forward push. The stable checkpoint and original APKs remain intact.
