# Device-aware debloat catalogue

The offline catalogue adds five ordered layers: Android baseline, manufacturer,
OEM software, device, and Android/OEM version. `DeviceCatalogue` matches exact
package identities and compatibility selectors, groups by package name, and
sorts rules by layer then stable rule ID. More-specific rules supply the primary
explanation while all applicable sources remain available. Classification and
operation safety retain the strictest applicable assessment. No specific rule
can weaken a protection, even if it claims stronger evidence.

## Schema and integrity

`app/src/main/assets/device-catalogue.json` uses schema version 1 and revision
`2026-10-26-r1`. Its companion SHA-256 file is checked before parsing. Required
fields include identity/display name, layer, manufacturer/OEM/model/codename/
profile selectors, API range, exact optional OEM versions, classification,
separate disable/uninstall assessments, reasons, function, consequences,
dependencies, evidence/limitations, confidence and nullable last operation
verification date. Empty dependency lists mean unknown dependencies, not none.

The loader validates package syntax, dates, unique rule IDs, enum values,
compatibility ranges, evidence URLs and layer requirements. SAFE claims require
high confidence, exact model/codename, exact Android and OEM version scope, a
verification date and device-test evidence. Such a schema check is a minimum
structural guard; reviewers must still validate the underlying evidence.
Malformed/unsupported catalogues are rejected atomically. Failure retains the
last validated snapshot and surfaces an error. No remote catalogue endpoint
exists. Updates ship through reviewed, signed app updates with revision, digest,
tests and changelog updates. The bundled digest detects corruption; it is not an
independent signature protecting against an attacker who can replace both files.

## Classification and existing operation safeguards

| Classification | Meaning |
|---|---|
| SAFE | Evidence supports the particular operation on the scoped firmware. |
| CAUTION | Known optional-function loss or conditions require review. |
| UNSAFE | Significant loss is expected; operation is discouraged. |
| CRITICAL | Essential functionality; protected from debloat selection. |
| UNKNOWN | Insufficient evidence; never promoted to SAFE. |

For conflicting evidence the conservative order is CRITICAL, UNSAFE, CAUTION,
UNKNOWN, SAFE. An unknown assessment vetoes a SAFE claim. Disabling and
uninstalling have independent safety, recommendation and reason fields.
Recommendations require all applicable operation assessments to agree on SAFE.

The original 52-entry catalogue is preserved as historical reference evidence.
Its 15 ESSENTIAL entries become CRITICAL, 17 RISKY entries become CAUTION and 20
generic SAFE entries become UNKNOWN in the device assessment. None is relabeled
as device-tested. Uninstall safety is UNKNOWN unless a stronger protection
applies. `DeviceAwareSafetyCatalogue` bridges to the existing executor and retains
all original restrictions. It cannot lower a legacy essential/risky assessment.
Preview uses the operation-specific assessment. Existing shell commands,
identity checks, confirmation, sequential execution and history are unchanged.

This intentionally strengthens the UI's interpretation of old generic SAFE
labels. Unknown/non-low risk still requires the existing risk acknowledgement.
Nothing is automatically selected, disabled or uninstalled. Critical entries and
AETHER are excluded from individual/bulk selection; catalogue refresh prunes
newly protected selections and updates an open preview.

## Initial POCO F6 / HyperOS / Android 16 profile

Seven new rules describe **four unique researched package identities**, not seven
packages. Three identities are newly added to the historical inventory:

| Package | Evidence | Assessment |
|---|---|---|
| `com.android.systemui` | Android 16 AOSP manifest and system role | CRITICAL |
| `com.miui.analytics` | Pinned maintained community research | UNKNOWN |
| `com.miui.msa.global` | Pinned maintained community research | UNKNOWN |
| `com.miui.securitycenter` | Community reports of severe failures and OEM feature dependencies | CRITICAL, conservatively protected |

Sources reviewed 2026-10-26:

- [AOSP SystemUI manifest, Android 16 tag](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/packages/SystemUI/AndroidManifest.xml).
- [UAD Next Generation research at e0af3336d8f71fcb2b65ad4949051cf25c654f61](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/blob/e0af3336d8f71fcb2b65ad4949051cf25c654f61/resources/assets/uad_lists.json).
- [Google supported devices](https://storage.googleapis.com/play_public/supported_devices.csv) for the POCO model/codename mapping, described in device-detection.md.

Community research includes old MIUI reports, conflicting firmware results and
unknown dependencies. Its removal recommendations are **not** copied as SAFE
HyperOS recommendations. `com.xiaomi.mipicks` was considered but excluded because
the reviewed source itself questions its current identity/use. No Internet
search snippet is treated as removal evidence.

The target profile resolves **55 unique catalogue references**: 16 CRITICAL,
17 CAUTION, 22 UNKNOWN, 0 UNSAFE, 0 SAFE. This includes 52 historical entries,
not 55 newly verified packages. Generic fallback resolves the original 52
references. **Device-verified removal entries: 0. Recommended removals: 0.**
All operation verification dates remain null. Actual installed matches and
remaining unknown counts are computed on the phone, never inferred from the
user's prior 509-package observation.

The initial target profile supplies conservative reference guidance, not a
complete or physically validated HyperOS debloat list. Additional SAFE rules need
reproducible firmware-specific evidence and separate disable/uninstall testing.

## UI and validation

Device Recommendations shows detected device/profile, revision, inventory
matches, recommendations and each category count. Search/type/safety/sort filters
share state with Apps and Debloat. Each row has an icon, label, package, kind,
classification and controlled selection. “Why listed” opens app details, where
“Show evidence and consequences” exposes all rules, operation-specific reasons,
sources and limitations. The existing preview/confirmation remains mandatory.

JVM tests cover all layers, model/API/OEM compatibility, deterministic duplicates,
conservative conflicts, independent operations, unknown identities, invalid
schema/digest/SAFE claims, target-profile counts and protection after refresh.
Platform UI tests compile for explicit selection and device-information privacy.
Device-aware recommendations, safety filters, selection safeguards and existing
package-management workflows are **USER-VERIFIED ON PHYSICAL DEVICE** on POCO F6 /
Android 16 / HyperOS, according to developer-reported manual testing confirmed on
2026-10-26. This validates the reported behavior; no named-package, exact-firmware
SAFE removal evidence was supplied. Catalogue classifications and verification
dates remain unchanged. Automated instrumentation execution and other-firmware
coverage remain pending; see the [hardware record](device-management-report.md#hardware-validation--developer-reported-manual-testing).
