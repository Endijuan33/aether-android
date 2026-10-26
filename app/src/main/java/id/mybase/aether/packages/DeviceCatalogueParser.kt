package id.mybase.aether.packages

import id.mybase.aether.device.OemFamily
import id.mybase.aether.security.InputValidators
import java.security.MessageDigest
import java.time.LocalDate

/** Offline updates are accepted only as a whole, after digest and schema checks. */
internal fun parseDeviceCatalogue(bytes: ByteArray, sha256: String): DeviceCatalogue {
    val actual =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(actual == sha256.trim()) { "Device catalogue integrity check failed" }
    require(bytes.size <= 1_000_000) { "Catalogue too large" }
    val root =
        JsonParser(bytes.toString(Charsets.UTF_8)).parse() as? JsonObject
            ?: error("Expected catalogue object")
    fun JsonObject.string(key: String): String =
        (this[key] as? JsonString)?.value?.takeIf { it.isNotBlank() }
            ?: error("Missing string $key")
    fun JsonObject.array(key: String): List<JsonValue> =
        (this[key] as? JsonArray)?.values ?: error("Missing array $key")
    fun JsonObject.strings(key: String): List<String> =
        array(key).map {
            (it as? JsonString)?.value?.takeIf(String::isNotBlank) ?: error("Invalid $key")
        }
    fun JsonObject.number(key: String): Int =
        (this[key] as? JsonNumber)?.value?.toIntOrNull() ?: error("Invalid $key")
    fun JsonObject.operation(key: String): OperationRecommendation {
        val obj = this[key] as? JsonObject ?: error("Missing operation $key")
        val recommended =
            when (obj["recommended"]) {
                JsonTrue -> true
                JsonFalse -> false
                else -> error("Invalid recommendation")
            }
        return OperationRecommendation(
            RecommendationSafety.valueOf(obj.string("safety")),
            recommended,
            obj.string("reason"),
        )
    }
    require(root.number("schemaVersion") == 1) { "Unsupported catalogue schema" }
    val revision = root.string("revision")
    LocalDate.parse(root.string("updated"))
    val rules =
        root.array("rules").map { value ->
            val obj = value as? JsonObject ?: error("Invalid rule")
            val evidence =
                obj.array("evidence").map {
                    val source = it as? JsonObject ?: error("Invalid evidence")
                    CatalogueEvidence(
                        source.string("kind"),
                        source.string("source"),
                        source.string("limitation"),
                    )
                }
            val verified =
                when (val date = obj["lastVerificationDate"]) {
                    JsonNull -> null
                    is JsonString -> LocalDate.parse(date.value).toString()
                    else -> error("Invalid verification date")
                }
            val rule =
                DeviceCatalogueRule(
                    obj.string("id"),
                    obj.string("packageName"),
                    obj.string("displayName"),
                    CatalogueLayer.valueOf(obj.string("layer")),
                    obj.strings("manufacturers").toSet(),
                    obj.strings("oemFamilies").map(OemFamily::valueOf).toSet(),
                    obj.strings("models").toSet(),
                    obj.strings("codenames").toSet(),
                    obj.strings("profiles").toSet(),
                    obj.number("minSdk"),
                    obj.number("maxSdk"),
                    obj.strings("oemVersions").toSet(),
                    RecommendationSafety.valueOf(obj.string("classification")),
                    obj.operation("disable"),
                    obj.operation("uninstall"),
                    obj.string("explanation"),
                    obj.string("functionality"),
                    obj.string("consequences"),
                    obj.strings("dependencies"),
                    evidence,
                    obj.string("confidence"),
                    revision,
                    verified,
                )
            require(InputValidators.isValidPackageName(rule.packageName)) { "Invalid package name" }
            require(rule.minSdk >= 30 && rule.maxSdk >= rule.minSdk) {
                "Invalid Android compatibility"
            }
            require(evidence.isNotEmpty() && evidence.all { it.source.startsWith("https://") }) {
                "Evidence URL required"
            }
            require(rule.confidence in setOf("LOW", "MEDIUM", "HIGH")) { "Invalid confidence" }
            when (rule.layer) {
                CatalogueLayer.BASELINE ->
                    require(
                        rule.manufacturers.isEmpty() &&
                            rule.oemFamilies.isEmpty() &&
                            rule.models.isEmpty() &&
                            rule.codenames.isEmpty() &&
                            rule.profiles.isEmpty(),
                    )
                CatalogueLayer.MANUFACTURER -> require(rule.manufacturers.isNotEmpty())
                CatalogueLayer.OEM -> require(rule.oemFamilies.isNotEmpty())
                CatalogueLayer.DEVICE ->
                    require(
                        rule.models.isNotEmpty() ||
                            rule.codenames.isNotEmpty() ||
                            rule.profiles.isNotEmpty(),
                    )
                CatalogueLayer.VERSION ->
                    require(
                        rule.minSdk > 30 ||
                            rule.maxSdk < Int.MAX_VALUE ||
                            rule.oemVersions.isNotEmpty(),
                    )
            }
            for (operation in listOf(rule.disable, rule.uninstall)) {
                require(!operation.recommended || operation.safety == RecommendationSafety.SAFE) {
                    "Only verified SAFE operations may be recommended"
                }
                if (
                    operation.safety == RecommendationSafety.SAFE ||
                        rule.classification == RecommendationSafety.SAFE
                ) {
                    require(
                        verified != null &&
                            rule.confidence == "HIGH" &&
                            evidence.any { it.kind == "DEVICE_TEST" } &&
                            (rule.models.isNotEmpty() || rule.codenames.isNotEmpty()) &&
                            rule.oemVersions.isNotEmpty() &&
                            rule.minSdk == rule.maxSdk,
                    ) {
                        "SAFE requires exact firmware and device operation evidence"
                    }
                }
            }
            rule
        }
    return DeviceCatalogue(revision, rules)
}
