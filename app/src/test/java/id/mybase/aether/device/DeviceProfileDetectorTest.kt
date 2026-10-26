package id.mybase.aether.device

import org.junit.Assert.*
import org.junit.Test

class DeviceProfileDetectorTest {
    private fun build(vendor: String, model: String = "Known model") =
        DeviceBuild(
            manufacturer = vendor,
            brand = vendor,
            model = model,
            androidRelease = "16",
            sdk = 36,
        )

    @Test
    fun pocoHyperOsMatchesDynamically() {
        val profile =
            DeviceProfileDetector.detect(
                build("Xiaomi", "POCO F6").copy(brand = "POCO"),
                mapOf("ro.mi.os.version.name" to "OS3.0.1"),
            )
        assertEquals(OemFamily.HYPER_OS, profile.oemFamily)
        assertEquals("poco-f6-hyperos-android16", profile.catalogueProfile)
        assertEquals(DetectionConfidence.HIGH, profile.confidence)
        assertTrue(profile.sources.contains("ro.mi.os.version.name"))
    }

    @Test
    fun supportsOemFamiliesWithObservedProperties() {
        val cases =
            listOf(
                Triple("Samsung", "ro.build.version.oneui", OemFamily.ONE_UI),
                Triple("OPPO", "ro.build.version.oplusrom", OemFamily.COLOR_OS),
                Triple("OnePlus", "ro.oxygen.version", OemFamily.OXYGEN_OS),
                Triple("realme", "ro.build.version.realmeui", OemFamily.REALME_UI),
                Triple("Redmi", "ro.miui.ui.version.name", OemFamily.MIUI),
            )
        cases.forEach { (vendor, key, family) ->
            val profile = DeviceProfileDetector.detect(build(vendor), mapOf(key to "16.0"))
            assertEquals(family, profile.oemFamily)
            assertEquals("16.0", profile.oemVersion)
        }
        for ((name, family) in
            listOf("Funtouch" to OemFamily.FUNTOUCH_OS, "OriginOS" to OemFamily.ORIGIN_OS)) {
            assertEquals(
                family,
                DeviceProfileDetector.detect(
                        build("vivo"),
                        mapOf("ro.vivo.os.name" to name, "ro.vivo.os.version" to "6"),
                    )
                    .oemFamily,
            )
        }
        assertEquals(
            OemFamily.PIXEL,
            DeviceProfileDetector.detect(build("Google", "Pixel 9")).oemFamily,
        )
        assertEquals(OemFamily.MOTOROLA, DeviceProfileDetector.detect(build("Motorola")).oemFamily)
        assertEquals(OemFamily.ASUS, DeviceProfileDetector.detect(build("ASUS")).oemFamily)
        assertEquals(OemFamily.SONY, DeviceProfileDetector.detect(build("Sony")).oemFamily)
    }

    @Test
    fun missingPropertiesUnknownModelsAndConflictsFallBack() {
        for (snapshot in
            listOf(
                DeviceBuild(),
                build("unknown"),
                build("Xiaomi"),
                build("Xiaomi", ""),
                build("Samsung").copy(brand = "POCO"),
            )) {
            val profile = DeviceProfileDetector.detect(snapshot)
            assertEquals("android-baseline", profile.catalogueProfile)
            assertNotEquals(DetectionConfidence.HIGH, profile.confidence)
            assertTrue(profile.limitations.isNotEmpty())
            assertNull(profile.oemVersion)
        }
        assertEquals(OemFamily.ANDROID, DeviceProfileDetector.detect(build("OnePlus")).oemFamily)
    }

    @Test
    fun certifiedPocoModelIdentifiersMatchButProDoesNot() {
        val properties = mapOf("ro.mi.os.version.name" to "OS3.0.1")
        for (model in listOf("24069PC21G", "24069PC21I")) {
            assertEquals(
                "poco-f6-hyperos-android16",
                DeviceProfileDetector.detect(
                        build("POCO", model).copy(device = "peridot"),
                        properties,
                    )
                    .catalogueProfile,
            )
        }
        assertNotEquals(
            "poco-f6-hyperos-android16",
            DeviceProfileDetector.detect(
                    build("POCO", "POCO F6 Pro").copy(device = "vermeer"),
                    properties,
                )
                .catalogueProfile,
        )
    }

    @Test
    fun androidVersionAndModelMustMatchSpecificProfile() {
        val properties = mapOf("ro.mi.os.version.name" to "OS3.0.1")
        assertNotEquals(
            "poco-f6-hyperos-android16",
            DeviceProfileDetector.detect(build("Xiaomi", "POCO F6").copy(sdk = 35), properties)
                .catalogueProfile,
        )
        assertNotEquals(
            "poco-f6-hyperos-android16",
            DeviceProfileDetector.detect(build("Xiaomi", "POCO F6 Pro"), properties)
                .catalogueProfile,
        )
    }
}
