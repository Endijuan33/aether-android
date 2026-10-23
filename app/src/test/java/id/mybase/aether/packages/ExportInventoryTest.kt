package id.mybase.aether.packages

import id.mybase.aether.domain.AppKind
import id.mybase.aether.domain.AppPackage
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.domain.kind
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportInventoryTest {

    private fun app(
        pkg: String,
        isSystem: Boolean = false,
        isEnabled: Boolean = true,
        versionName: String? = "2.1",
    ) =
        AppPackage(
            packageName = pkg,
            label = "Label $pkg",
            versionName = versionName,
            versionCode = 42L,
            isSystem = isSystem,
            isEnabled = isEnabled,
            isUpdatedSystemApp = false,
            installerPackageName = "com.android.vending",
            targetSdk = 34,
            permissions = listOf("android.permission.INTERNET", "android.permission.CAMERA"),
            firstInstallTime = 1700000000000L,
            lastUpdateTime = 1700000001000L,
        )

    @Test
    fun `export produces documented shape`() {
        val json =
            exportInventoryJson(
                listOf(
                    app("com.a.one") to SafetyRating.SAFE,
                    app("com.b.two", isSystem = true, isEnabled = false, versionName = null) to
                        SafetyRating.ESSENTIAL,
                ),
            )
        val root = JSONObject(json)

        assertEquals("0.1.0", root.getString("appVersion"))
        // exportedAt must be a parseable ISO-8601 instant, close to now.
        val exportedAt = Instant.parse(root.getString("exportedAt"))
        val skew = Math.abs(Instant.now().toEpochMilli() - exportedAt.toEpochMilli())
        assertTrue("exportedAt skew too large: $skew ms", skew < 60_000)

        val packages: JSONArray = root.getJSONArray("packages")
        assertEquals(2, packages.length())

        val first = packages.getJSONObject(0)
        assertEquals("com.a.one", first.getString("packageName"))
        assertEquals("Label com.a.one", first.getString("label"))
        assertEquals("2.1", first.getString("versionName"))
        assertEquals(42L, first.getLong("versionCode"))
        assertEquals(AppKind.USER.name, first.getString("kind"))
        assertEquals(SafetyRating.SAFE.name, first.getString("safetyRating"))
        assertTrue(first.getBoolean("isEnabled"))
        val perms = first.getJSONArray("permissions")
        assertEquals(2, perms.length())
        assertEquals("android.permission.INTERNET", perms.getString(0))

        val second = packages.getJSONObject(1)
        assertEquals(AppKind.DISABLED.name, second.getString("kind"))
        assertEquals(SafetyRating.ESSENTIAL.name, second.getString("safetyRating"))
        assertTrue(second.isNull("versionName"))
    }

    @Test
    fun `kind in export matches domain kind()`() {
        val sys = app("com.s.one", isSystem = true)
        assertEquals(
            sys.kind().name,
            JSONObject(exportInventoryJson(listOf(sys to SafetyRating.UNKNOWN)))
                .getJSONArray("packages")
                .getJSONObject(0)
                .getString("kind"),
        )
    }

    @Test
    fun `empty inventory exports empty packages array`() {
        val root = JSONObject(exportInventoryJson(emptyList()))
        assertEquals(0, root.getJSONArray("packages").length())
        assertEquals("0.1.0", root.getString("appVersion"))
    }
}
