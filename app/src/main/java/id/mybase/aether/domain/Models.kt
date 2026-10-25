package id.mybase.aether.domain

/** Curated safety assessment of a package. Absence of a catalogue entry means [UNKNOWN]. */
enum class SafetyRating {
    SAFE,
    RISKY,
    UNKNOWN,
    ESSENTIAL,
}

/** Classification of an installed package derived from its flags. */
enum class AppKind {
    USER,
    SYSTEM,
    DISABLED,
    UPDATED_SYSTEM,
}

data class AppPackage(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val isUpdatedSystemApp: Boolean,
    val installerPackageName: String?,
    val targetSdk: Int,
    val permissions: List<String>,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
)

/**
 * Derives the [AppKind] from install flags. Precedence: a disabled package is DISABLED regardless
 * of its origin; an updated system app reports UPDATED_SYSTEM before plain SYSTEM.
 */
fun AppPackage.kind(): AppKind =
    when {
        !isEnabled -> AppKind.DISABLED
        isUpdatedSystemApp -> AppKind.UPDATED_SYSTEM
        isSystem -> AppKind.SYSTEM
        else -> AppKind.USER
    }

data class CatalogueEntry(
    val packageName: String,
    val rating: SafetyRating,
    val note: String,
    val source: String,
    val revision: String,
    val updated: String,
)

enum class OperationType {
    UNINSTALL_FOR_USER,
    DISABLE,
    ENABLE,
}

fun OperationType.label(): String =
    when (this) {
        OperationType.UNINSTALL_FOR_USER -> "Uninstall for user"
        OperationType.DISABLE -> "Disable"
        OperationType.ENABLE -> "Enable"
    }

/**
 * Whether the operation can be undone afterwards. UNINSTALL_FOR_USER drops the app's data and
 * cannot be rolled back by this app; DISABLE/ENABLE are symmetric toggles.
 */
fun OperationType.isReversible(): Boolean =
    when (this) {
        OperationType.UNINSTALL_FOR_USER -> false
        OperationType.DISABLE -> true
        OperationType.ENABLE -> true
    }

data class PlannedOperation(val app: AppPackage, val type: OperationType)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/**
 * Risk matrix for a planned operation. Evaluation order is significant: an ESSENTIAL rating always
 * yields CRITICAL, then system-app uninstalls and RISKY ratings yield HIGH, then unknown system
 * packages and plain user uninstalls yield MEDIUM; everything else is LOW.
 */
fun riskOf(
    type: OperationType,
    rating: SafetyRating,
    kind: AppKind,
    isSystem: Boolean = kind == AppKind.SYSTEM || kind == AppKind.UPDATED_SYSTEM,
): RiskLevel =
    when {
        rating == SafetyRating.ESSENTIAL -> RiskLevel.CRITICAL
        isSystem && type == OperationType.UNINSTALL_FOR_USER -> RiskLevel.HIGH
        rating == SafetyRating.RISKY -> RiskLevel.HIGH
        rating == SafetyRating.UNKNOWN -> RiskLevel.MEDIUM
        type == OperationType.UNINSTALL_FOR_USER -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

enum class OperationStatus {
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    SKIPPED,
}

data class OperationResult(
    val operation: PlannedOperation,
    val status: OperationStatus,
    val message: String,
)

/** Persisted history row for one executed operation. */
data class OperationRecord(
    val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val label: String,
    val operation: String,
    val status: String,
    val message: String,
)
