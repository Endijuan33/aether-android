package id.mybase.aether.packages

import id.mybase.aether.domain.CatalogueEntry
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.SafetyRating
import kotlinx.coroutines.flow.StateFlow

/** Compatibility bridge: the existing executor retains every legacy protection. */
class DeviceAwareSafetyCatalogue(
    private val legacy: SafetyCatalogue,
    private val state: StateFlow<DeviceCatalogueState>,
) : SafetyCatalogue {
    override val revision: String
        get() = state.value.revision.takeUnless { it == "unavailable" } ?: legacy.revision

    override fun entryFor(packageName: String): CatalogueEntry? {
        val recommendation =
            state.value.entries[packageName]
                ?: return legacy.entryFor(packageName)?.copy(rating = ratingFor(packageName))
        return CatalogueEntry(
            packageName,
            ratingFor(packageName),
            recommendation.detail.explanation,
            recommendation.rules
                .flatMap { it.evidence }
                .map { it.source }
                .distinct()
                .joinToString("\n"),
            revision,
            recommendation.detail.lastVerificationDate.orEmpty(),
        )
    }

    override fun ratingFor(packageName: String): SafetyRating =
        assessed(packageName, state.value.entries[packageName]?.classification)

    fun ratingForOperation(packageName: String, operation: OperationType): SafetyRating =
        assessed(packageName, state.value.entries[packageName]?.operation(operation)?.safety)

    private fun assessed(packageName: String, safety: RecommendationSafety?): SafetyRating {
        val known =
            legacy.ratingFor(packageName).let {
                if (it == SafetyRating.UNKNOWN && legacy.entryFor(packageName) == null)
                    SafetyRating.SAFE
                else it
            }
        val current =
            when (safety) {
                RecommendationSafety.CRITICAL -> SafetyRating.ESSENTIAL
                RecommendationSafety.UNSAFE,
                RecommendationSafety.CAUTION -> SafetyRating.RISKY
                RecommendationSafety.SAFE -> SafetyRating.SAFE
                else -> SafetyRating.UNKNOWN
            }
        val order =
            listOf(
                SafetyRating.SAFE,
                SafetyRating.UNKNOWN,
                SafetyRating.RISKY,
                SafetyRating.ESSENTIAL,
            )
        return if (order.indexOf(known) > order.indexOf(current)) known else current
    }
}
