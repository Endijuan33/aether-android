# AETHER stable source checkpoint

Checkpoint date: **2026-10-27 (Asia/Jakarta)**.

This record covers source preservation for the completed Device Management & UX
Improvements phase. It is not a production release. Application behavior,
dependencies, ADB transport and package execution were not changed during the
checkpoint phase.

## Repository and checkpoint identity

| Field | Value |
|---|---|
| Repository | https://github.com/Endijuan33/aether-android |
| Branch | `main` |
| Original HEAD | `1fe271fa0bdc3a122900e3fc403c5bab15687641` |
| Implementation and audit commit | `0ddeb80e16a6b38f94db714074ce5c0f50680f94` |
| Final HEAD | The commit identified by annotated tag `aether-stable-device-management-v1` (`aether-stable-device-management-v1^{commit}`) |
| Backup branch | `backup/pre-continuous-development-device-management` at `1fe271fa0bdc3a122900e3fc403c5bab15687641` |
| Annotated source checkpoint tag | `aether-stable-device-management-v1`; not a production-release tag |
| Exact final SHA and remote verification receipt | `/home/endcore/aether-backups/device-management-20261027-012422/remote-verification.json` |

The tag identifies the final documentation commit containing this report. A Git
commit cannot include its own content-dependent SHA. Its full hash and every
commit/message are recorded in the external verification receipt and final task
response. The immutable checkpoint identity can also be resolved after fetching:

```sh
git rev-parse 'aether-stable-device-management-v1^{commit}'
git log --reverse --format='%H %s' 1fe271fa0bdc3a122900e3fc403c5bab15687641..'aether-stable-device-management-v1^{commit}'
```

The original working tree contained **22 modified tracked files and 29 untracked
files**, with an empty index. All 197 tracked/nonignored working-tree files were
reviewed and preserved. The final source and documentation inventory has 198
tracked files, including this report. No original source file was omitted.

## Backup and recovery integrity

| Backup item | Result |
|---|---|
| Original HEAD branch | `backup/pre-continuous-development-device-management` preserves the original commit, independently of the later commits |
| Archive | `/home/endcore/aether-backups/device-management-20261027-012422/aether-pre-continuous-development-20261027-012422.tar.gz` |
| Archive size | 851243 bytes |
| Archive SHA-256 | `11dc2661eec245cb7f3cc2537cc3605cecf6afedd0f4b06703889471158444b1` |
| Archive contents | 197 working-tree files plus manifest and baseline history bundle; 199 regular members |
| Archive verification | Gzip read to EOF/CRC passed; exact member inventory, all extracted SHA-256 hashes and file modes matched |
| Baseline bundle | `/home/endcore/aether-backups/device-management-20261027-012422/pre-checkpoint-history.bundle`; `git bundle verify` passed |
| Manifest | `/home/endcore/aether-backups/device-management-20261027-012422/working-tree-manifest.json` |
| Verification evidence | `/home/endcore/aether-backups/device-management-20261027-012422/backup-verification.json` and `verified-extraction/` |
| Final checkpoint bundle | `/home/endcore/aether-backups/device-management-20261027-012422/stable-device-management.bundle`; final hash and verification recorded in the delivery receipt |

The archive was created and verified **before staging or committing anything**.
The backup branch alone would not preserve uncommitted work; the archive contains
the actual modified and untracked source/documentation files. It deliberately
excludes Git internals/local configuration, ignored files, credentials, keystores,
build caches, temporary diagnostics and APK/build outputs. The existing APKs remain
untouched in the original workspace, with their recorded checksums preserved.

The archive contains `aether-android/` and `backup-metadata/`. To recover, first
verify the archive checksum, extract into a new empty directory, and verify its
manifest. Clone `backup-metadata/pre-checkpoint-history.bundle` into a separate
new directory to recover the baseline history. Overlay the archived
`aether-android/` files there to restore the exact pre-staging working tree.
Restore into a new directory, without overwriting the active workspace. The final
bundle additionally preserves the completed commits and annotated tag.

No existing backup was replaced. The older branch
`backup/pre-continuous-development` remains at
`ca653ddf3f738b03723c8c90787d3776e9786acb`; the older annotated tag
`aether-stable-pre-continuous-development` retains tag object
`e456175b1b0e514737442b4f1d14580fc596d6d7`, targeting
`74aab0888962ea2792d6ff5b36ba11d5da3f24a5`. The preferred new names were available
both locally and remotely, so no alternate name was needed. The timestamped
archive was opened exclusively and did not replace an existing file.

## Granular commit history

| Commit | Conventional Commit message | Files |
|---|---|---:|
| `bfb183b130eeea48fbda788e648255ffe7664ae1` | `feat(device): add OEM and model-aware detection` | 3 |
| `b94da498a32627793a951608a53e7a96bee935d5` | `feat(apps): add cached native application icons` | 12 |
| `83295d663988903d8030e61652f0918721540cdb` | `feat(packages): add layered device-aware safety catalogue` | 8 |
| `8a34a34f0e8f2855290af87fcf727f1341238c68` | `fix(selection): preserve protected package selection across filters` | 9 |
| `27c056d354458a8dabed7bcdbbb67a03cd8a1f8a` | `feat(apps): expose device-aware recommendations and details` | 6 |
| `1b8a8012cf3df7fdc9193cda2fdfbfc73bf5f613` | `fix(settings): open external native Developer Settings safely` | 7 |
| `0ddeb80e16a6b38f94db714074ce5c0f50680f94` | `docs(validation): record device management and checkpoint audit` | 12 |
| `aether-stable-device-management-v1^{commit}` (this report's commit; exact SHA in receipt) | `docs(checkpoint): record stable source backup and synchronization` | 1 |

The icon, selection and recommendation changes shared several screens. Reviewed
partial hunks were staged to keep the responsibilities separate; the working
application files were not edited. Catalogue integration tests accompany the
selection state they exercise. Each group used explicit paths, staged-diff
inspection, whitespace checks and recorded diff/tree hashes before committing.
There were no empty commits, merges, history rewrites or new ADB-engine changes.
Staged patch offsets were checked and corrected before committing; no malformed
intermediate patch was committed.

The only edit to an existing file during this phase removed an extra EOF blank
line from `docs/device-management-report.md`, which `git diff --cached --check`
flagged when the previously untracked report was staged. The original is retained
in the archive. All application, test, asset, build, dependency and workflow bytes
match the pre-checkpoint validated snapshot.

## Validation baseline and scope

Source: [pre-checkpoint audit](pre-checkpoint-validation-report.md) and its
[machine-readable evidence](pre-checkpoint-validation.json).

| Validation | Preserved result |
|---|---|
| JVM tests | **264 passed**, 0 failed, 0 errors, 0 skipped |
| Formatting | Passed; additionally rerun in this phase, exit 0 |
| Debug build | Passed |
| Release build | Passed; unsigned APK preserved |
| Offline debug lint | 0 errors, 2 existing warnings |
| Offline release lint | 0 errors, 2 existing warnings |
| Release-summary tests | 4 passed |
| Dependency audit | 131 resolved dependencies checked; zero matching OSV advisories |
| Instrumentation | **38 compiled; execution pending, 0 executed** in the recorded baseline |
| Physical device | Ten developer-reported checks: **USER-VERIFIED ON PHYSICAL DEVICE**, POCO F6 / Android 16 / HyperOS; confirmation 2026-10-26 |
| Debug APK signature | Verified; existing debug identity retained |
| Release APK | Intentionally unsigned; not a signed production release |
| Production signing | Pending; four required repository signing secrets are not configured |
| Latest checkpoint GitHub CI | Pending at checkpoint preparation; push triggers CI, and the receipt records its observed run state without treating publication as a passing run |

This phase did not rerun JVM tests, lint, APK assembly, instrumentation or the
dependency advisory lookup. Their existing results still represent the unchanged
application source. The earlier six-warning lint baseline remains historical
evidence; the offline result did not establish that four warnings were fixed.
Manual POCO evidence is not automated instrumentation evidence and does not bind
to a supplied tested-APK checksum or prove other-device behavior.

The required formatting check was actually rerun in the workspace:

```sh
ANDROID_HOME=/home/endcore/android-sdk ./gradlew ktfmtCheck --offline --dependency-verification strict --console=plain --max-workers=2
```

Exit code **0**, 79.63 seconds. Log:
`/home/endcore/aether-backups/device-management-20261027-012422/checkpoint-ktfmt.log`. `git diff --check` and the complete new-commit range whitespace
check passed after the EOF cleanup. Complete tree/hash comparisons confirmed no
omitted files or application changes. No new test result is inferred from Git
commits or push success.

## Credential and repository hygiene

Tracked, staged and nonignored untracked files were reviewed for credentials,
private-key markers, common access-token patterns, signing-password literals,
local configuration, unexpected diagnostics and oversized artifacts. Ignored
paths were checked for sensitive filenames. No potentially sensitive file
requiring exclusion was found; the only key marker was the already reviewed
short invalid redaction-test fixture, not usable private material. No secret
values were printed or staged. No keystore, APK, build cache, `.env` file,
`local.properties`, private key or diagnostic dump was committed.

Existing `.gitignore` rules cover the actual generated and signing files in this
workspace; no demonstrated hygiene gap required an edit. No ignored developer
file was deleted. This scoped heuristic review is not a claim of exhaustive
secret detection across all historical encodings.

## Remote synchronization and verification

The initial live remote query and the pre-push fetch both confirmed origin's
`main` at `1fe271fa0bdc3a122900e3fc403c5bab15687641`. After the seven source/audit commits, `main...origin/main`
reported **7 ahead, 0 behind**, with no remote-only commits. The final report adds
one documentation commit on the same ancestry. No divergence was merged or forced.

Publication uses an explicit non-forced atomic push of exactly these refs:

```sh
git push --atomic origin refs/heads/main:refs/heads/main refs/heads/backup/pre-continuous-development-device-management:refs/heads/backup/pre-continuous-development-device-management refs/tags/aether-stable-device-management-v1:refs/tags/aether-stable-device-management-v1
```

The final verification receipt records the actual push exit code and output,
final HEAD, annotated tag object and peeled target, archive/bundle digests,
independent `git ls-remote` and GitHub API results, final ahead/behind counts and
working-tree status. Completion requires all of these checks:

- Remote `main`, local `main` and `origin/main` point to the final report commit.
- Remote backup branch still points to `1fe271fa0bdc3a122900e3fc403c5bab15687641`.
- Remote annotated tag object matches the local tag and peels to the final HEAD.
- The older backup branch and checkpoint tag retain their original targets.
- `main...origin/main` is `0 0`; the tracked/untracked working tree is clean.
- No force-push, branch/tag deletion, history rewrite, credential exposure or
  application change was needed.

**Final status: CHECKPOINT CREATED AND REMOTELY VERIFIED**, conditional on the
completed delivery receipt matching these checks. Before that receipt is written,
this report is a prepared checkpoint record, not independent proof of a push.
The final task response supplies the measured final hash and verification outcome.
GitHub backup integrity and latest-commit CI passage are separate claims.

## Remaining validation and release work

1. Execute the 38 compiled instrumentation tests on supported device/emulator
   infrastructure; retain results and investigate failures separately from
   infrastructure errors.
2. Run and complete GitHub Actions for the exact checkpoint commit. The main push
   triggers the existing CI workflow automatically; verify its SHA and all jobs
   before recording a pass. This source-control phase does not claim CI success.
3. Configure `AETHER_KEYSTORE_BASE64`, `AETHER_KEYSTORE_PASSWORD`,
   `AETHER_KEY_ALIAS` and `AETHER_KEY_PASSWORD` through the existing secure signing
   path, using the approved existing production identity.
4. When authorized, sign the production release APK, verify its signature and
   certificate identity, and record its own checksum. No signing key was generated
   or rotated here.

The broader hardware/custom-crypto/distribution gates in
[limitations.md](limitations.md) still apply. No continuous-development phase was
started by this checkpoint task.

## Files in each source/audit commit

### feat(device): add OEM and model-aware detection

Commit: `bfb183b130eeea48fbda788e648255ffe7664ae1`.

- `app/src/main/java/id/mybase/aether/device/AndroidDeviceInfoProvider.kt`
- `app/src/main/java/id/mybase/aether/device/DeviceProfile.kt`
- `app/src/test/java/id/mybase/aether/device/DeviceProfileDetectorTest.kt`

### feat(apps): add cached native application icons

Commit: `b94da498a32627793a951608a53e7a96bee935d5`.

- `app/src/androidTest/java/id/mybase/aether/PackageIconPlatformTest.kt`
- `app/src/main/java/id/mybase/aether/di/AppContainer.kt`
- `app/src/main/java/id/mybase/aether/packages/IconCache.kt`
- `app/src/main/java/id/mybase/aether/packages/PackageIconProvider.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/AppDetailsScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/AppListScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/components/PackageIcon.kt`
- `app/src/main/java/id/mybase/aether/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/debloat/PreviewDialog.kt`
- `app/src/main/java/id/mybase/aether/ui/history/HistoryScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/recovery/RecoveryScreen.kt`
- `app/src/test/java/id/mybase/aether/packages/IconCacheTest.kt`

### feat(packages): add layered device-aware safety catalogue

Commit: `83295d663988903d8030e61652f0918721540cdb`.

- `app/src/main/assets/device-catalogue.json`
- `app/src/main/assets/device-catalogue.sha256`
- `app/src/main/java/id/mybase/aether/di/AppContainer.kt`
- `app/src/main/java/id/mybase/aether/packages/DeviceAwareSafetyCatalogue.kt`
- `app/src/main/java/id/mybase/aether/packages/DeviceCatalogue.kt`
- `app/src/main/java/id/mybase/aether/packages/DeviceCatalogueParser.kt`
- `app/src/main/java/id/mybase/aether/packages/DeviceCatalogueRepository.kt`
- `app/src/test/java/id/mybase/aether/packages/DeviceCatalogueTest.kt`

### fix(selection): preserve protected package selection across filters

Commit: `8a34a34f0e8f2855290af87fcf727f1341238c68`.

- `app/src/androidTest/java/id/mybase/aether/ScreenRegressionTest.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/AppListScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/PackageFilters.kt`
- `app/src/main/java/id/mybase/aether/ui/debloat/DebloatScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/navigation/AetherNavHost.kt`
- `app/src/main/java/id/mybase/aether/ui/navigation/ViewModelHelpers.kt`
- `app/src/main/java/id/mybase/aether/ui/packages/PackagesViewModel.kt`
- `app/src/test/java/id/mybase/aether/packages/DeviceCatalogueIntegrationTest.kt`
- `app/src/test/java/id/mybase/aether/ui/packages/PackagesViewModelTest.kt`

### feat(apps): expose device-aware recommendations and details

Commit: `27c056d354458a8dabed7bcdbbb67a03cd8a1f8a`.

- `app/src/androidTest/java/id/mybase/aether/ScreenRegressionTest.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/AppDetailsScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/apps/DeviceRecommendationsScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/navigation/AetherNavHost.kt`
- `app/src/main/java/id/mybase/aether/ui/navigation/Routes.kt`

### fix(settings): open external native Developer Settings safely

Commit: `1b8a8012cf3df7fdc9193cda2fdfbfc73bf5f613`.

- `app/src/androidTest/java/id/mybase/aether/DeveloperSettingsPlatformTest.kt`
- `app/src/main/java/id/mybase/aether/ui/onboarding/OnboardingScreen.kt`
- `app/src/main/java/id/mybase/aether/ui/pairing/DeveloperOptionsStatus.kt`
- `app/src/main/java/id/mybase/aether/ui/pairing/DeveloperSettingsNavigator.kt`
- `app/src/main/java/id/mybase/aether/ui/pairing/PairingActions.kt`
- `app/src/main/java/id/mybase/aether/ui/setup/SetupGuideScreen.kt`
- `app/src/test/java/id/mybase/aether/ui/DeveloperSettingsNavigatorTest.kt`

### docs(validation): record device management and checkpoint audit

Commit: `0ddeb80e16a6b38f94db714074ce5c0f50680f94`.

- `CHANGELOG.md`
- `README.md`
- `docs/catalogue-provenance.md`
- `docs/debloat-catalogue.md`
- `docs/device-detection.md`
- `docs/device-management-report.md`
- `docs/device-management-validation.json`
- `docs/engineering-audit.md`
- `docs/limitations.md`
- `docs/package-icon-loading.md`
- `docs/pre-checkpoint-validation-report.md`
- `docs/pre-checkpoint-validation.json`

### docs(checkpoint): record stable source backup and synchronization

Commit: `aether-stable-device-management-v1^{commit}`; exact full SHA in the delivery receipt.

- `docs/stable-checkpoint-report.md`
