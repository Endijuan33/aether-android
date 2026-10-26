package id.mybase.aether.packages

import id.mybase.aether.device.*
import id.mybase.aether.domain.OperationType
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceCatalogueTest {
    private val bytes
        get() = File("src/main/assets/device-catalogue.json").readBytes()

    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun catalogue() =
        parseDeviceCatalogue(bytes, File("src/main/assets/device-catalogue.sha256").readText())

    private val profile =
        DeviceProfileDetector.detect(
            DeviceBuild(
                manufacturer = "Xiaomi",
                brand = "POCO",
                model = "POCO F6",
                device = "peridot",
                sdk = 36,
            ),
            mapOf("ro.mi.os.version.name" to "OS3.0.1"),
        )

    private fun rule(
        id: String,
        layer: CatalogueLayer,
        safety: RecommendationSafety = RecommendationSafety.UNKNOWN,
    ) =
        catalogue()
            .rules
            .first()
            .copy(
                id = id,
                packageName = "com.example.app",
                layer = layer,
                classification = safety,
                disable = OperationRecommendation(safety, false, id),
                uninstall = OperationRecommendation(safety, false, id),
            )

    @Test
    fun shippedCataloguePassesIntegrityAndSchema() {
        assertTrue(catalogue().rules.isNotEmpty())
        assertTrue(catalogue().rules.none { it.disable.recommended || it.uninstall.recommended })
    }

    @Test
    fun layersMatchDeviceAndVersionAndResolveDuplicatesDeterministically() {
        val rules =
            listOf(
                rule("base", CatalogueLayer.BASELINE),
                rule("manufacturer", CatalogueLayer.MANUFACTURER)
                    .copy(manufacturers = setOf("xiaomi")),
                rule("oem", CatalogueLayer.OEM).copy(oemFamilies = setOf(OemFamily.HYPER_OS)),
                rule("device", CatalogueLayer.DEVICE).copy(models = setOf("POCO F6")),
                rule("version", CatalogueLayer.VERSION, RecommendationSafety.CAUTION)
                    .copy(minSdk = 36, maxSdk = 36, oemVersions = setOf("OS3.0.1")),
            )
        val result = DeviceCatalogue("test", rules).resolve(profile)
        assertEquals(1, result.size)
        assertEquals(5, result.values.single().rules.size)
        assertEquals("version", result.values.single().detail.id)
        assertEquals(result, DeviceCatalogue("test", rules.reversed()).resolve(profile))
        assertEquals(
            4,
            DeviceCatalogue("test", rules)
                .resolve(profile.copy(oemVersion = "OS3.0.2"))
                .values
                .single()
                .rules
                .size,
        )
        assertEquals(
            4,
            DeviceCatalogue("test", rules)
                .resolve(profile.copy(build = profile.build.copy(sdk = 35)))
                .values
                .single()
                .rules
                .size,
        )
        val generic = DeviceProfileDetector.detect(DeviceBuild(sdk = 36))
        assertEquals(
            listOf("base"),
            DeviceCatalogue("test", rules).resolve(generic).values.single().rules.map { it.id },
        )
        assertNull(result["com.example.unlisted"])
    }

    @Test
    fun specificSafeRuleCannotWeakenProtectionAndOperationsRemainSeparate() {
        val base = rule("base", CatalogueLayer.BASELINE, RecommendationSafety.CRITICAL)
        val specific =
            rule("specific", CatalogueLayer.DEVICE, RecommendationSafety.SAFE)
                .copy(models = setOf("POCO F6"))
        val result =
            DeviceCatalogue("test", listOf(base, specific)).resolve(profile).values.single()
        assertEquals(RecommendationSafety.CRITICAL, result.classification)
        assertFalse(result.operation(OperationType.DISABLE).recommended)
        val different =
            specific.copy(
                uninstall =
                    OperationRecommendation(
                        RecommendationSafety.UNSAFE,
                        false,
                        "Deletes needed data",
                    ),
            )
        val operations = PackageRecommendation(different.packageName, listOf(different))
        assertEquals(RecommendationSafety.SAFE, operations.operation(OperationType.DISABLE).safety)
        assertEquals(
            RecommendationSafety.UNSAFE,
            operations.operation(OperationType.UNINSTALL_FOR_USER).safety,
        )
    }

    @Test
    fun unknownEvidenceNeverBecomesSafeByMerging() {
        val result =
            DeviceCatalogue(
                    "test",
                    listOf(
                        rule("unknown", CatalogueLayer.BASELINE),
                        rule("safe", CatalogueLayer.DEVICE, RecommendationSafety.SAFE),
                    ),
                )
                .resolve(profile)
                .values
                .single()
        assertEquals(RecommendationSafety.UNKNOWN, result.classification)
        assertFalse(result.operation(OperationType.DISABLE).recommended)
    }

    @Test
    fun invalidSchemaIntegrityDuplicateIdsAndUnsupportedSafetyClaimsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { parseDeviceCatalogue(bytes, "bad") }
        for (edit in
            listOf<(JSONObject) -> Unit>(
                { it.put("schemaVersion", 999) },
                { it.getJSONArray("rules").put(it.getJSONArray("rules").getJSONObject(0)) },
                { it.getJSONArray("rules").getJSONObject(0).put("packageName", "x;reboot") },
                {
                    it.getJSONArray("rules")
                        .getJSONObject(0)
                        .getJSONObject("disable")
                        .put("safety", "SAFE")
                        .put("recommended", true)
                },
                { it.getJSONArray("rules").getJSONObject(0).put("minSdk", 40).put("maxSdk", 30) },
            )) {
            val document = JSONObject(bytes.toString(Charsets.UTF_8))
            edit(document)
            val invalid = document.toString().toByteArray()
            assertThrows(IllegalArgumentException::class.java) {
                parseDeviceCatalogue(invalid, hash(invalid))
            }
        }
    }
}
