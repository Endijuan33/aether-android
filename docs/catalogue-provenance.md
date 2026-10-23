# Safety catalogue — provenance and update policy

File: `app/src/main/assets/catalogue.json`

## Revision

- **Revision:** `2026-10-24-r1`
- **Updated:** 2026-10-24
- **Source label:** "AETHER curated catalogue v1"
- **Entries:** 52 — 15 ESSENTIAL, 17 RISKY, 20 SAFE

## Taxonomy

Each entry carries a `packageName`, a `rating`, a human-readable `note`
explaining *why*, and a `source` naming where the assessment came from
(e.g. `AOSP`, `Google Play`, `OEM preload (widely documented)`).

| Rating | Meaning |
|---|---|
| `ESSENTIAL` | Core OS function (system UI, telephony, settings provider, launcher, package installer, ADB shell endpoint…). Disabling risks an unusable device. |
| `RISKY` | Disabling is safe only under stated conditions (a replacement app installed, a feature never used, post-setup state). Read the note. |
| `SAFE` | Standalone user-level or inert app with no OS dependents. Still check the note for the one condition that applies (e.g. another browser for Chrome). |
| *(no entry)* | `UNKNOWN` — shown as unknown in the app. Never guessed, never inherited from a similar-looking package name. |

The app's risk matrix (`domain/Models.kt → riskOf()`) treats an ESSENTIAL
rating as CRITICAL for any operation, and refuses to present essential
packages as debloat candidates casually.

## What the catalogue is not

- **Not exhaustive.** 52 well-known AOSP/Google/Meta/OEM package ids. Most
  OEM-specific packages are absent — they show as unknown, which is the
  correct behavior.
- **Not authoritative.** It is a curated starting reference. It does not
  replace checking your device's actual dependencies.
- **Never classifies every system package as bloatware.** ESSENTIAL
  entries exist precisely to mark what must stay.
- **No fabricated ratings.** Every rating has a note and a source; an
  entry without evidence does not get a SAFE rating.

## OEM / firmware variance

Package safety varies by device manufacturer, firmware version, Android
version and user requirements. A package marked SAFE on AOSP (e.g. the
AOSP desk clock) may be load-bearing on an OEM skin that wired other
components to it. The in-app detail screen and the per-entry note always
take precedence over the one-word rating: read both before confirming an
operation.

## Update policy

1. Catalogue updates ship as app updates (the file is a bundled asset).
2. Each update **bumps `revision`** (format `YYYY-MM-DD-rN`), sets
   `updated` to the change date, and records `source` per entry.
3. New entries require a verifiable package id and an attributable
   source — OEM documentation, AOSP source, or widely documented
   community references. "Widely reported" is acceptable for OEM
   preloads only when multiple independent sources agree; otherwise the
   package stays unknown.
4. Corrections (wrong rating, stale note) follow the same revision bump.
5. The changelog ([CHANGELOG.md](../CHANGELOG.md)) notes catalogue
   revisions under the release they ship in.

## Contributing entries

See [CONTRIBUTING.md](../CONTRIBUTING.md). PRs that add entries must
include the package id, proposed rating, a note explaining the
assessment, and the source. PRs that bulk-rate unknown packages without
sources will be rejected.
