package id.mybase.aether.packages

import id.mybase.aether.domain.CatalogueEntry
import id.mybase.aether.domain.SafetyRating

/**
 * Curated safety catalogue for well-known package ids. A package with no entry is
 * [SafetyRating.UNKNOWN] — absence is meaningful and is never stored as an UNKNOWN entry.
 */
interface SafetyCatalogue {
    val revision: String

    fun entryFor(packageName: String): CatalogueEntry?

    fun ratingFor(packageName: String): SafetyRating
}

/** Pure in-memory catalogue; unit-testable without Android. */
internal class MapSafetyCatalogue(
    override val revision: String,
    private val entries: Map<String, CatalogueEntry>,
) : SafetyCatalogue {
    override fun entryFor(packageName: String): CatalogueEntry? = entries[packageName]

    override fun ratingFor(packageName: String): SafetyRating =
        entries[packageName]?.rating ?: SafetyRating.UNKNOWN
}
