# Native package icons

`PackageIconProvider` uses `PackageManager.getApplicationIcon(packageName)` on
`Dispatchers.IO`. It makes no system/user/disabled distinction, so all installed
package types use the same native lookup. Android drawables retain adaptive-icon
and OEM resource behavior. The Compose painter draws the drawable directly;
there is no raster conversion, remote image loader or new dependency.

`IconCache` is a 64-entry access-order cache of `Drawable.ConstantState`, including
negative lookups. A mutex coalesces concurrent requests and serializes background
lookups. Each consumer receives a fresh drawable instance to avoid sharing bounds
or mutable state. The cache does not allocate duplicate icon bitmaps. Its bound
is an entry count, not an exact byte budget; OEM resource sizes can vary.

`PackageIcon` uses keyed `produceState` and `remember`, so ordinary recomposition
does not launch another request. Lists have package-name keys and fixed 40 dp
icons. Loading, missing/uninstalled packages, inaccessible/broken resources and
non-cloneable drawables use the same Material Android fallback. Icons are
decorative beside an accessible app name, avoiding duplicate announcements.

A process-scoped application-context provider observes package added, removed,
changed and replaced broadcasts. Configuration changes invalidate cached
resources. Memory callbacks clear the cache; in-flight old loads cannot repopulate
an invalidated cache. A process restart naturally reloads it. Evicted icons can
be reloaded when scrolling back; flicker-free performance on real hardware has
not been measured.

Integration covers Apps/search results, Debloat, Device Recommendations,
app details, preview, dashboard recent operations, history and recovery. History
uses stored labels and package identity; an already-uninstalled app gets fallback.

JVM tests exercise hits, negative caching, LRU eviction, coalescing and invalidation
while a load is active. Platform tests compile for real user/system lookup,
missing package fallback and invalidation. Native icons are **USER-VERIFIED ON
PHYSICAL DEVICE** on POCO F6 / Android 16 / HyperOS, based on developer-reported
manual testing confirmed on 2026-10-26. No per-package-type inventory or quantitative
frame measurements were supplied. Instrumentation execution and broader OEM
coverage remain pending; see the [hardware record](device-management-report.md#hardware-validation--developer-reported-manual-testing).
