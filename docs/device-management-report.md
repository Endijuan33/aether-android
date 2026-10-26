# Device management engineering report — 2026-10-26

## Result

Incremental implementation adds controlled package selection, native cached icons,
external Developer Settings navigation, device/OEM detection, an offline layered
catalogue and Device Recommendations. The ADB/pairing implementation and package
executor are unchanged. No dependency or CI workflow changes were required.

Branch: `main`. Starting and final HEAD: `1fe271fa0bdc3a122900e3fc403c5bab15687641`.
The initial working tree was clean. All development changes remain uncommitted;
no commits, pushes, branch changes or destructive Git commands were performed.

The earlier CI failure was already repaired in the starting revision. Its
[successful baseline CI](https://github.com/Endijuan33/aether-android/actions/runs/40244540176)
is separate evidence; current uncommitted changes have passed local checks but
have not been submitted to a new GitHub Actions run.

## Root causes and fixes

- **Checkbox:** the user confirmed that failure occurred on the checkbox only.
  The old checkbox ignored the requested checked Boolean and called the same
  inversion method as the clickable row. Bulk selection emitted one toggle per
  package. The new row is the sole toggleable input owner; its passive checkbox
  reflects immutable ViewModel state, and `setSelected(package, checked)` is
  idempotent. Bulk selection emits one update. The physical uncheck failure was
  not reproduced here, so duplicate event delivery is not claimed as proven.
- **Verified protection defect:** an individual tap could select an essential
  package while bulk selection excluded it. A new regression failed on old code
  (11 tests, one expected failure) and passes after the fix. Both paths now share
  protection rules, including AETHER itself. The reported 509 → 496 count is
  consistent with 13 ESSENTIAL exclusions in the old bulk code; exact package
  identities cannot be established without that device inventory.
- **Settings:** current onboarding/setup code already used the official Settings
  action, not an internal Developer Options destination. It lacked resolution,
  SecurityException handling, complete fallback feedback and explicit external
  task ownership. The shared launcher resolves an exported system activity,
  starts its own task and falls back to native Settings. The report of an internal
  destination is not independently reproduced in this revision.
- **Icons:** rows were text-only and there was no native icon provider. The new
  PackageManager loader, 64-entry shared cache and native drawable painter now
  serve lists, details, previews, recommendations, history and recovery.

Selection follows package identity. Search, type/safety filters and sorting keep
hidden selections, which are explicitly counted. Changing operation retains
eligible selections. SavedStateHandle restores selection and operation without
executing anything. Protected/missing selections are pruned after inventory or
catalogue refresh. No recommendation is automatically selected.

## Hardware validation — developer-reported manual testing

**Status: USER-VERIFIED ON PHYSICAL DEVICE**

- Device: **POCO F6**.
- Android version: **Android 16**.
- OEM software: **HyperOS**; exact HyperOS build/version was not supplied with this confirmation.
- Validation confirmation recorded: **2026-10-26**. This is the date the developer's report was received; a separate test timestamp was not supplied.
- Evidence source: the developer's explicit confirmation that the latest AETHER implementation was manually tested on the actual target device and behaves as expected.

| Manually validated functionality | Status |
|---|---|
| Native application icons | USER-VERIFIED ON PHYSICAL DEVICE |
| Automatic device/OEM detection | USER-VERIFIED ON PHYSICAL DEVICE |
| Device-aware recommendations | USER-VERIFIED ON PHYSICAL DEVICE |
| Application safety filters | USER-VERIFIED ON PHYSICAL DEVICE |
| Individual checkbox selection and unselection | USER-VERIFIED ON PHYSICAL DEVICE |
| Select All and Deselect All | USER-VERIFIED ON PHYSICAL DEVICE |
| Protected-package selection safeguards | USER-VERIFIED ON PHYSICAL DEVICE |
| External native Developer Settings navigation | USER-VERIFIED ON PHYSICAL DEVICE |
| Return to AETHER without breaking its expected functionality | USER-VERIFIED ON PHYSICAL DEVICE |
| Existing Wireless ADB and package-management workflows | USER-VERIFIED ON PHYSICAL DEVICE |

This is developer-reported manual testing. Codex did not execute or independently
observe these physical-device checks. The confirmation establishes the reported
behavior on this POCO F6; it does not establish coverage of other OEMs, every
lifecycle scenario, quantitative frame timing, or package-specific removal safety.
The tested APK's SHA-256 was not supplied, so this record does not bind the manual
test to a particular artifact digest. Existing artifact metadata below is retained.

**Automated instrumentation execution remains pending: 38 tests compiled,
0 executed.** No additional automated tests or builds were run for this
documentation-only update. The existing 264 JVM test result and lint results
remain the automated validation evidence.

## Device and catalogue evidence

The developer now confirms the latest features and existing Wireless ADB and
package-management workflows on POCO F6 / Android 16 / HyperOS, as recorded above.
Earlier reports of pairing, shell UID 2000, scanning and package operations remain
historical evidence. No new raw device-profile dump or package-operation log was
provided with this manual confirmation.

The model identifiers `24069PC21G` / `24069PC21I` and codename `peridot` were checked
against Google's supported-device list. Tests simulate matching those identifiers
with observed HyperOS properties. The developer confirms device/OEM detection
behaves as expected; the displayed confidence value and exact Build/property
values were not supplied with this confirmation. Unknown device/software information falls back
to the generic catalogue and displays limitations.

Catalogue schema 1, revision `2026-10-26-r1`, supports baseline, manufacturer,
OEM, device and version rules. It retains conservative conflicts, provenance,
independent disable/uninstall safety and existing executor protections. Seven
new rules cover four source-reviewed identities; three are new Xiaomi identities.
The 52 historical entries are retained as reference evidence. The simulated POCO
profile resolves 55 unique references: 16 CRITICAL, 17 CAUTION, 22 UNKNOWN,
0 UNSAFE and 0 SAFE. Device-verified removal entries and recommended removals are
both **zero**. Actual installed matches and remaining unknown package counts are
not supplied with the manual confirmation. Codex ran no package operation during
implementation or this documentation update. The developer's successful workflow
validation does not add named-package, exact-firmware SAFE removal evidence.

See [device detection](device-detection.md), [catalogue evidence and limitations](debloat-catalogue.md)
and [icon loading](package-icon-loading.md) for implementation details.

## Validation

Baseline: 241 JVM tests passed; formatting, both lint variants, debug/release
builds and instrumentation compilation passed. Lint had six warnings per variant.

Final command (using the project's existing tasks):

```sh
ANDROID_HOME=/home/endcore/android-sdk ./gradlew \
  ktfmtCheck testDebugUnitTest --rerun lintDebug lintRelease \
  assembleDebug assembleRelease assembleDebugAndroidTest \
  --dependency-verification strict --console=plain --max-workers=2
```

Final result: **BUILD SUCCESSFUL**, 9m 26s.

| Check | Actual result |
|---|---|
| JVM tests executed | 264 passed, 0 failed, 0 errors, 0 skipped |
| Formatting | Passed |
| Debug lint | 0 errors, 6 existing warnings |
| Release lint | 0 errors, 6 existing warnings |
| Debug APK | Built |
| Unsigned release APK | Built |
| Instrumentation tests | 38 compiled; 0 executed; execution pending |
| Manual physical-device checks | USER-VERIFIED ON PHYSICAL DEVICE: POCO F6 / Android 16 / HyperOS; developer confirmation recorded 2026-10-26 |
| Automated device/emulator checks | Pending; the earlier local `adb devices -l` check returned no devices |

The six lint warnings are the existing Gradle/BC update notices and two custom
X509TrustManager warnings in unchanged ADB code. New SavedStateHandle lint
warnings were repaired rather than baselined. Compiler/Gradle deprecation notes
are recorded in the build log; no working crypto dependency was changed.

The additional regression coverage tests selection/protection/restoration,
checkbox coordinates/recomposition, cache coalescing/invalidation, OEM/profile
matching, catalogue merge/schema/integrity/operation safety, refresh protection,
Settings launch/fallback and device-information privacy. Platform tests compile
for actual native Settings navigation and icon lookup; compilation is not OEM
compatibility evidence. Existing pairing, transport, notification, shell,
executor, preview and history tests remain in the suite.

Logs: `/tmp/aether-device-baseline.log`, `/tmp/aether-selection-red.log`,
`/tmp/aether-device-final.log`. Machine-readable results:
[device-management-validation.json](device-management-validation.json).

### Artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 77819871 | `6119e846a918d3d332a920e715706dff8069b6cec93f023c7fde087a8bd766cf` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 13929124 | `d3ad077fa26ca96ef9910283b3fc5058a549658b6fe15f4eb87d65ccce3ac352` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2738950 | `20b2fdf064c7ef4d0f3a42813b323b4fb0823b070343d642794b1f4d516ed5cb` |

## Objective status and remaining work

| Objective | Status | Remaining evidence |
|---|---|---|
| Selection/protection | USER-VERIFIED ON PHYSICAL DEVICE | Individual/bulk selection and safeguards confirmed on the POCO F6; automated recreation coverage remains pending |
| Native icons | USER-VERIFIED ON PHYSICAL DEVICE | Manual behavior confirmed; quantitative performance and broader OEM coverage remain pending |
| Developer Settings and return | USER-VERIFIED ON PHYSICAL DEVICE | External native navigation and normal return confirmed on HyperOS; automated and other-OEM navigation tests remain pending |
| Device detection | USER-VERIFIED ON PHYSICAL DEVICE | Correct behavior confirmed; exact displayed profile fields/confidence were not supplied |
| Device-aware recommendations and safety filters | USER-VERIFIED ON PHYSICAL DEVICE | UI behavior confirmed; package-specific removal evidence remains unchanged |
| Catalogue framework | COMPLETE AND VERIFIED | Existing JVM merge/schema/integrity/protection checks passed; future evidence still requires review |
| POCO F6 detailed catalogue | PARTIALLY IMPLEMENTED | Conservative references and UI are validated as described; exact-firmware, named-package SAFE removal evidence is still needed |
| Management UI | USER-VERIFIED ON PHYSICAL DEVICE | The reported manual checks passed; automated, accessibility and broader-device coverage remain pending |
| Wireless ADB and package-management workflows | USER-VERIFIED ON PHYSICAL DEVICE | Existing workflows confirmed by developer on the POCO F6; no additional per-operation results inferred |
| Local build/format/lint/JVM regression | COMPLETE AND VERIFIED | Recorded results preserved; current GitHub Actions run awaits separately authorized commit/push |
| Physical regression: reported scope | USER-VERIFIED ON PHYSICAL DEVICE | All ten reported checks confirmed; instrumentation execution remains pending |

### Remaining CI and release signing

Current uncommitted implementation changes have not been submitted to a new
GitHub Actions run. After a separately authorized commit/push, run the existing
CI checks against that revision and execute the 38 compiled instrumentation tests
on supported device/emulator infrastructure. Manual POCO validation does not
change the recorded instrumentation execution count.

The release artifact remains unsigned. The existing release workflow requires
`AETHER_KEYSTORE_BASE64`, `AETHER_KEYSTORE_PASSWORD`, `AETHER_KEY_ALIAS` and
`AETHER_KEY_PASSWORD` to sign it. The resumed pre-checkpoint audit on 2026-10-27
queried repository Actions secrets successfully: all four are absent (zero
repository secrets). Production signing is **NOT CONFIGURED**. Existing debug and
instrumentation APK signatures verify; the release is confirmed unsigned.
Production signing, signed-release verification and production certificate review
remain required. A future signed artifact needs its own checksum; all original
artifact metadata above remains preserved.

### Pre-checkpoint validation follow-up — 2026-10-27

The [pre-checkpoint report](pre-checkpoint-validation-report.md) completes the
interrupted validation audit using the preserved isolated source copy. All 195
source-snapshot files matched before documentation updates. That build passed
264 JVM tests (zero failures/errors/skips), formatting, both lint variants,
debug/unsigned release assembly and instrumentation compilation. Its offline lint
reports contain two existing trust-manager warnings per variant and zero errors;
the six-warning historical result above is preserved, and the four absent
update-availability notices are not claimed fixed. Four release-summary tests,
workflow static checks, 131-coordinate OSV lookup and artifact checks also passed.

The 38 instrumentation tests remain **COMPILED — EXECUTION PENDING**, with zero
executed for this implementation. The ten POCO manual checks remain
**USER-VERIFIED ON PHYSICAL DEVICE**. Baseline CI is green, but the uncommitted
implementation still needs its own remote run after a separately authorized
checkpoint/push. Recommendation: **READY WITH DOCUMENTED PENDING ITEMS** for a
reviewed source checkpoint. The isolated APKs and their distinct checksums are
listed separately in the follow-up report; the original APKs were not replaced.

Both documentation updates preserve application implementation, ADB/package
logic, the earlier automated records and original build artifacts. The follow-up
records separate isolated-build and supplementary check results. No commit or
push was made.

## Modified files by subsystem

### Documentation and validation

- `CHANGELOG.md`
- `docs/catalogue-provenance.md`
- `docs/debloat-catalogue.md`
- `docs/device-detection.md`
- `docs/device-management-report.md`
- `docs/device-management-validation.json`
- `docs/engineering-audit.md`
- `docs/limitations.md`
- `docs/package-icon-loading.md`

### Regression tests

- `app/src/androidTest/java/id/mybase/aether/DeveloperSettingsPlatformTest.kt`
- `app/src/androidTest/java/id/mybase/aether/PackageIconPlatformTest.kt`
- `app/src/androidTest/java/id/mybase/aether/ScreenRegressionTest.kt`
- `app/src/test/java/id/mybase/aether/device/DeviceProfileDetectorTest.kt`
- `app/src/test/java/id/mybase/aether/packages/DeviceCatalogueIntegrationTest.kt`
- `app/src/test/java/id/mybase/aether/packages/DeviceCatalogueTest.kt`
- `app/src/test/java/id/mybase/aether/packages/IconCacheTest.kt`
- `app/src/test/java/id/mybase/aether/ui/DeveloperSettingsNavigatorTest.kt`
- `app/src/test/java/id/mybase/aether/ui/packages/PackagesViewModelTest.kt`

### Catalogue and safety integration

- `app/src/main/assets/device-catalogue.json`
- `app/src/main/assets/device-catalogue.sha256`
- `app/src/main/java/id/mybase/aether/packages/DeviceAwareSafetyCatalogue.kt`
- `app/src/main/java/id/mybase/aether/packages/DeviceCatalogue.kt`
- `app/src/main/java/id/mybase/aether/packages/DeviceCatalogueParser.kt`
- `app/src/main/java/id/mybase/aether/packages/DeviceCatalogueRepository.kt`

### Device detection

- `app/src/main/java/id/mybase/aether/device/AndroidDeviceInfoProvider.kt`
- `app/src/main/java/id/mybase/aether/device/DeviceProfile.kt`

### Shared UI, selection and dependency wiring

- `app/src/main/java/id/mybase/aether/di/AppContainer.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/AppDetailsScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/AppListScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/DeviceRecommendationsScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/PackageFilters.kt`
- `app/src/main/java/id/mybase/aether/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/debloat/DebloatScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/debloat/PreviewDialog.kt`
- `app/src/main/java/id/mybase/aether/ui/history/HistoryScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/navigation/AetherNavHost.kt`
- `app/src/main/java/id/mybase/aether/ui/navigation/Routes.kt`
- `app/src/main/java/id/mybase/aether/ui/navigation/ViewModelHelpers.kt`
- `app/src/main/java/id/mybase/aether/ui/packages/PackagesViewModel.kt`
- `app/src/main/java/id/mybase/aether/ui/recovery/RecoveryScreen.kt`

### Native icons

- `app/src/main/java/id/mybase/aether/packages/IconCache.kt`
- `app/src/main/java/id/mybase/aether/packages/PackageIconProvider.kt`
- `app/src/main/java/id/mybase/aether/ui/components/PackageIcon.kt`

### Native Settings navigation

- `app/src/main/java/id/mybase/aether/ui/onboarding/OnboardingScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/pairing/DeveloperOptionsStatus.kt`
- `app/src/main/java/id/mybase/aether/ui/pairing/DeveloperSettingsNavigator.kt`
- `app/src/main/java/id/mybase/aether/ui/pairing/PairingActions.kt`
- `app/src/main/java/id/mybase/aether/ui/setup/SetupGuideScreen.kt`
