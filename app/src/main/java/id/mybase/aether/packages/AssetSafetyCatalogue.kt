package id.mybase.aether.packages

import android.content.Context
import id.mybase.aether.domain.CatalogueEntry
import id.mybase.aether.domain.SafetyRating

/**
 * Loads the curated catalogue from assets/catalogue.json.
 *
 * The JSON schema is validated strictly on load; a malformed catalogue throws
 * [IllegalStateException] with a precise message. The DI/startup code must catch it and surface it
 * to the user — it is never silently ignored.
 */
class AssetSafetyCatalogue(context: Context) : SafetyCatalogue {
    private val delegate: SafetyCatalogue

    init {
        val json = context.assets.open("catalogue.json").bufferedReader().use { it.readText() }
        val parsed = parseCatalogueJson(json)
        delegate =
            MapSafetyCatalogue(
                revision = parsed.revision,
                entries = parsed.entries.associateBy { it.packageName },
            )
    }

    override val revision: String
        get() = delegate.revision

    override fun entryFor(packageName: String): CatalogueEntry? = delegate.entryFor(packageName)

    override fun ratingFor(packageName: String): SafetyRating = delegate.ratingFor(packageName)
}
