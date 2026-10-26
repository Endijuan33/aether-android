package id.mybase.aether.device

enum class OemFamily {
    HYPER_OS,
    MIUI,
    ONE_UI,
    COLOR_OS,
    OXYGEN_OS,
    FUNTOUCH_OS,
    ORIGIN_OS,
    REALME_UI,
    MOTOROLA,
    ASUS,
    SONY,
    PIXEL,
    ANDROID,
}

enum class DetectionConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class DeviceBuild(
    val manufacturer: String = "",
    val brand: String = "",
    val model: String = "",
    val device: String = "",
    val product: String = "",
    val fingerprint: String = "",
    val androidRelease: String = "",
    val sdk: Int = 0,
    val buildId: String = "",
    val display: String = "",
    val securityPatch: String = "",
)

data class DeviceProfile(
    val build: DeviceBuild,
    val manufacturerFamily: String,
    val oemFamily: OemFamily,
    val oemVersion: String?,
    val catalogueProfile: String,
    val confidence: DetectionConfidence,
    val sources: List<String>,
    val limitations: List<String>,
)

/**
 * Pure matching over a public Build snapshot and optional, non-privileged property observations.
 */
object DeviceProfileDetector {
    fun detect(build: DeviceBuild, properties: Map<String, String> = emptyMap()): DeviceProfile {
        fun family(raw: String): String? =
            when (raw.trim().lowercase()) {
                "xiaomi",
                "redmi",
                "poco" -> "xiaomi"
                "samsung" -> "samsung"
                "oppo" -> "oppo"
                "oneplus" -> "oneplus"
                "vivo",
                "iqoo" -> "vivo"
                "realme" -> "realme"
                "motorola",
                "lenovo" -> "motorola"
                "asus" -> "asus"
                "sony" -> "sony"
                "google" -> "google"
                else -> null
            }
        val manufacturer = family(build.manufacturer)
        val brand = family(build.brand)
        val conflict =
            manufacturer != null &&
                brand != null &&
                manufacturer != brand &&
                setOf(manufacturer, brand) !in
                    listOf(setOf("oppo", "oneplus"), setOf("oppo", "realme"))
        val vendor = if (conflict) "generic" else brand ?: manufacturer ?: "generic"
        val sources = mutableListOf("Public android.os.Build fields")
        fun property(key: String): String? =
            properties[key]
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "unknown" }
                ?.also { sources += key }
        var version: String? = null
        val skin =
            when (vendor) {
                "xiaomi" -> {
                    val hyper = property("ro.mi.os.version.name")
                    val miui = property("ro.miui.ui.version.name")
                    version = hyper ?: miui
                    when {
                        hyper != null -> OemFamily.HYPER_OS
                        miui != null -> OemFamily.MIUI
                        else -> OemFamily.ANDROID
                    }
                }
                "samsung" -> {
                    version = property("ro.build.version.oneui")
                    OemFamily.ONE_UI
                }
                "oppo" -> {
                    version =
                        property("ro.build.version.oplusrom")
                            ?: property("ro.build.version.opporom")
                    OemFamily.COLOR_OS
                }
                "oneplus" -> {
                    version = property("ro.oxygen.version")
                    if (version != null) OemFamily.OXYGEN_OS else OemFamily.ANDROID
                }
                "vivo" -> {
                    val name = property("ro.vivo.os.name")
                    version = property("ro.vivo.os.version")
                    when {
                        name.equals("OriginOS", true) -> OemFamily.ORIGIN_OS
                        name.equals("Funtouch", true) || name.equals("Funtouch OS", true) ->
                            OemFamily.FUNTOUCH_OS
                        else -> OemFamily.ANDROID
                    }
                }
                "realme" -> {
                    version = property("ro.build.version.realmeui")
                    OemFamily.REALME_UI
                }
                "motorola" -> OemFamily.MOTOROLA
                "asus" -> OemFamily.ASUS
                "sony" -> OemFamily.SONY
                "google" ->
                    if (build.model.startsWith("Pixel", true)) OemFamily.PIXEL
                    else OemFamily.ANDROID
                else -> OemFamily.ANDROID
            }
        val limitations = buildList {
            if (conflict) add("Manufacturer and brand disagree; generic catalogue selected.")
            if (vendor == "generic") add("Manufacturer is not recognized.")
            if (build.model.isBlank() || build.model.equals("unknown", true))
                add("Device model is unavailable.")
            if (skin == OemFamily.ANDROID && vendor != "generic")
                add(
                    "OEM software could not be established; brand alone does not identify the installed ROM.",
                )
            if (version == null && skin != OemFamily.PIXEL)
                add("OEM version unavailable; version-specific recommendations are withheld.")
            if (build.sdk < 30) add("Android version is unavailable or unsupported.")
        }
        val usable = limitations.isEmpty()
        val profile =
            when {
                !usable -> "android-baseline"
                vendor == "xiaomi" &&
                    skin == OemFamily.HYPER_OS &&
                    build.sdk == 36 &&
                    listOf("POCO F6", "24069PC21G", "24069PC21I").any {
                        it.equals(build.model, true)
                    } &&
                    (build.device.isBlank() || build.device == "peridot") ->
                    "poco-f6-hyperos-android16"
                else -> "$vendor-${skin.name.lowercase()}"
            }
        return DeviceProfile(
            build,
            vendor,
            skin,
            version,
            profile,
            when {
                usable -> DetectionConfidence.HIGH
                vendor != "generic" && build.model.isNotBlank() -> DetectionConfidence.MEDIUM
                else -> DetectionConfidence.LOW
            },
            sources.distinct(),
            limitations,
        )
    }
}
