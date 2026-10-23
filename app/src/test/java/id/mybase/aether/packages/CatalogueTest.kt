package id.mybase.aether.packages

import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.security.InputValidators
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Validates the shipped assets/catalogue.json against the loader schema. The asset is located
 * relative to the module dir (Gradle test working dir).
 */
class CatalogueTest {

    private fun assetText(): String {
        val candidates =
            listOf(
                File("src/main/assets/catalogue.json"),
                File("app/src/main/assets/catalogue.json"),
            )
        val f =
            candidates.firstOrNull { it.isFile }
                ?: throw AssertionError(
                    "catalogue.json not found; tried: ${candidates.map { it.path }}",
                )
        return f.readText()
    }

    @Test
    fun `catalogue parses and revision is non-blank`() {
        val parsed = parseCatalogueJson(assetText())
        assertTrue(parsed.revision.isNotBlank())
        assertTrue(parsed.updated.isNotBlank())
        assertTrue(parsed.source.isNotBlank())
    }

    @Test
    fun `catalogue has 40 to 60 entries`() {
        val parsed = parseCatalogueJson(assetText())
        assertTrue(
            "expected 40..60 entries, got ${parsed.entries.size}",
            parsed.entries.size in 40..60,
        )
    }

    @Test
    fun `no duplicate packageNames`() {
        val parsed = parseCatalogueJson(assetText())
        val names = parsed.entries.map { it.packageName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every entry has valid rating note source and package id`() {
        val parsed = parseCatalogueJson(assetText())
        for (e in parsed.entries) {
            // Rating must be a real enum value (parser already enforces; belt and braces).
            assertTrue(SafetyRating.entries.contains(e.rating))
            assertTrue("blank note for ${e.packageName}", e.note.isNotBlank())
            assertTrue("blank source for ${e.packageName}", e.source.isNotBlank())
            assertTrue(
                "invalid package id ${e.packageName}",
                InputValidators.isValidPackageName(e.packageName),
            )
        }
    }

    @Test
    fun `no UNKNOWN entries stored (absence means UNKNOWN)`() {
        val parsed = parseCatalogueJson(assetText())
        assertTrue(
            "catalogue must not store UNKNOWN entries",
            parsed.entries.none { it.rating == SafetyRating.UNKNOWN },
        )
    }

    @Test
    fun `ratings are balanced, not all bloat`() {
        val parsed = parseCatalogueJson(assetText())
        val counts = parsed.entries.groupingBy { it.rating }.eachCount()
        assertTrue("need ESSENTIAL entries", (counts[SafetyRating.ESSENTIAL] ?: 0) >= 5)
        assertTrue("need SAFE entries", (counts[SafetyRating.SAFE] ?: 0) >= 5)
        assertTrue("need RISKY entries", (counts[SafetyRating.RISKY] ?: 0) >= 5)
    }

    // -- MapSafetyCatalogue ------------------------------------------------------

    @Test
    fun `ratingFor returns UNKNOWN for absent packages`() {
        val catalogue = MapSafetyCatalogue("r1", emptyMap())
        assertEquals(SafetyRating.UNKNOWN, catalogue.ratingFor("com.not.listed"))
        assertNull(catalogue.entryFor("com.not.listed"))
        assertEquals("r1", catalogue.revision)
    }

    @Test
    fun `entryFor returns stored entries`() {
        val parsed = parseCatalogueJson(assetText())
        val catalogue =
            MapSafetyCatalogue(
                parsed.revision,
                parsed.entries.associateBy { it.packageName },
            )
        val e = catalogue.entryFor("com.google.android.gms")
        assertEquals(SafetyRating.ESSENTIAL, e?.rating)
        assertEquals(SafetyRating.ESSENTIAL, catalogue.ratingFor("com.google.android.gms"))
    }

    // -- malformed input ----------------------------------------------------------

    private fun expectFailure(json: String, hint: String) {
        try {
            parseCatalogueJson(json)
            fail("expected IllegalStateException for: $hint")
        } catch (e: IllegalStateException) {
            assertTrue(
                "message should mention catalogue.json, got: ${e.message}",
                e.message!!.contains("catalogue.json"),
            )
        }
    }

    @Test
    fun `missing revision fails`() {
        expectFailure("""{"entries": []}""", "missing revision")
    }

    @Test
    fun `blank revision fails`() {
        expectFailure("""{"revision": " ", "entries": []}""", "blank revision")
    }

    @Test
    fun `missing entries array fails`() {
        expectFailure("""{"revision": "r1"}""", "missing entries")
    }

    @Test
    fun `unknown rating fails`() {
        expectFailure(
            """{"revision":"r1","entries":[{"packageName":"com.a.b","rating":"DANGER","note":"n","source":"s"}]}""",
            "unknown rating",
        )
    }

    @Test
    fun `invalid packageName fails`() {
        expectFailure(
            """{"revision":"r1","entries":[{"packageName":"com.evil;rm","rating":"SAFE","note":"n","source":"s"}]}""",
            "invalid package id",
        )
    }

    @Test
    fun `blank note fails`() {
        expectFailure(
            """{"revision":"r1","entries":[{"packageName":"com.a.b","rating":"SAFE","note":" ","source":"s"}]}""",
            "blank note",
        )
    }

    @Test
    fun `duplicate packageNames fail`() {
        expectFailure(
            """{"revision":"r1","entries":[
              {"packageName":"com.a.b","rating":"SAFE","note":"n","source":"s"},
              {"packageName":"com.a.b","rating":"RISKY","note":"n","source":"s"}]}""",
            "duplicate ids",
        )
    }

    @Test
    fun `non-object top level fails`() {
        expectFailure("""[1,2,3]""", "array top level")
    }

    @Test
    fun `truncated JSON fails`() {
        expectFailure("""{"revision": "r1", "entries": [""", "truncated")
    }

    @Test
    fun `garbage fails`() {
        expectFailure("not json at all", "garbage")
    }
}
