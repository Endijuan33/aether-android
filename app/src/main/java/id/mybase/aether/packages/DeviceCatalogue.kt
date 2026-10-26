package id.mybase.aether.packages

import id.mybase.aether.device.DeviceProfile
import id.mybase.aether.device.OemFamily
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.SafetyRating

enum class RecommendationSafety {
    SAFE,
    UNKNOWN,
    CAUTION,
    UNSAFE,
    CRITICAL,
}

enum class CatalogueLayer {
    BASELINE,
    MANUFACTURER,
    OEM,
    DEVICE,
    VERSION,
}

data class CatalogueEvidence(val kind: String, val source: String, val limitation: String)

data class OperationRecommendation(
    val safety: RecommendationSafety,
    val recommended: Boolean,
    val reason: String,
)

data class DeviceCatalogueRule(
    val id: String,
    val packageName: String,
    val displayName: String,
    val layer: CatalogueLayer,
    val manufacturers: Set<String> = emptySet(),
    val oemFamilies: Set<OemFamily> = emptySet(),
    val models: Set<String> = emptySet(),
    val codenames: Set<String> = emptySet(),
    val profiles: Set<String> = emptySet(),
    val minSdk: Int = 30,
    val maxSdk: Int = Int.MAX_VALUE,
    val oemVersions: Set<String> = emptySet(),
    val classification: RecommendationSafety,
    val disable: OperationRecommendation,
    val uninstall: OperationRecommendation,
    val explanation: String,
    val functionality: String,
    val consequences: String,
    val dependencies: List<String>,
    val evidence: List<CatalogueEvidence>,
    val confidence: String,
    val revision: String,
    val lastVerificationDate: String?,
) {
    fun matches(profile: DeviceProfile): Boolean {
        if (profile.catalogueProfile == "android-baseline" && layer != CatalogueLayer.BASELINE)
            return false
        return (manufacturers.isEmpty() || profile.manufacturerFamily in manufacturers) &&
            (oemFamilies.isEmpty() || profile.oemFamily in oemFamilies) &&
            (models.isEmpty() || models.any { it.equals(profile.build.model, true) }) &&
            (codenames.isEmpty() || profile.build.device in codenames) &&
            (profiles.isEmpty() || profile.catalogueProfile in profiles) &&
            profile.build.sdk in minSdk..maxSdk &&
            (oemVersions.isEmpty() || profile.oemVersion in oemVersions)
    }
}

data class PackageRecommendation(val packageName: String, val rules: List<DeviceCatalogueRule>) {
    val classification: RecommendationSafety = rules.maxOf {
        maxOf(it.classification, it.disable.safety, it.uninstall.safety)
    }
    val detail: DeviceCatalogueRule
        get() = rules.last()

    fun operation(type: OperationType): OperationRecommendation {
        val operations = rules.map {
            if (type == OperationType.DISABLE) it.disable else it.uninstall
        }
        val safety = operations.maxOf { it.safety }
        return OperationRecommendation(
            safety,
            safety == RecommendationSafety.SAFE && operations.all { it.recommended },
            operations
                .filter { it.safety == safety }
                .map { it.reason }
                .distinct()
                .joinToString("\n"),
        )
    }
}

class DeviceCatalogue(val revision: String, val rules: List<DeviceCatalogueRule>) {
    init {
        require(rules.map { it.id }.distinct().size == rules.size) { "Duplicate rule id" }
    }

    fun resolve(profile: DeviceProfile): Map<String, PackageRecommendation> =
        rules
            .filter { it.matches(profile) }
            .sortedWith(compareBy({ it.layer.ordinal }, { it.id }))
            .groupBy { it.packageName }
            .mapValues { (name, entries) -> PackageRecommendation(name, entries) }
}

/**
 * Preserve the historical catalogue as reference evidence, never upgrade it to device verification.
 */
internal fun legacyDeviceRules(parsed: ParsedCatalogue): List<DeviceCatalogueRule> =
    parsed.entries.map { entry ->
        val safety =
            when (entry.rating) {
                SafetyRating.ESSENTIAL -> RecommendationSafety.CRITICAL
                SafetyRating.RISKY -> RecommendationSafety.CAUTION
                else -> RecommendationSafety.UNKNOWN
            }
        DeviceCatalogueRule(
            "legacy:${entry.packageName}",
            entry.packageName,
            entry.packageName,
            CatalogueLayer.BASELINE,
            classification = safety,
            disable =
                OperationRecommendation(
                    safety,
                    false,
                    "${entry.note} Current-device disable safety has not been verified.",
                ),
            uninstall =
                OperationRecommendation(
                    if (safety == RecommendationSafety.CRITICAL) safety
                    else RecommendationSafety.UNKNOWN,
                    false,
                    "Uninstall safety and data restoration have not been verified for this device.",
                ),
            explanation =
                "Historical catalogue reference; not a device-specific removal recommendation.",
            functionality = entry.note,
            consequences = "Functionality may be lost. Uninstall for user can delete app data.",
            dependencies = emptyList(),
            evidence =
                listOf(
                    CatalogueEvidence(
                        "LEGACY_REFERENCE",
                        entry.source,
                        "Generic source label; no per-device operation evidence.",
                    ),
                ),
            confidence = "LOW",
            revision = parsed.revision,
            lastVerificationDate = null,
        )
    }
