package id.mybase.aether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.mybase.aether.domain.AppKind
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.RiskLevel
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.domain.label

/** Colored dot representing a safety rating (name exposed via content description). */
@Composable
fun SafetyDot(rating: SafetyRating, modifier: Modifier = Modifier) {
    val color =
        when (rating) {
            SafetyRating.SAFE -> MaterialTheme.colorScheme.tertiary
            SafetyRating.RISKY -> MaterialTheme.colorScheme.error
            SafetyRating.UNKNOWN -> MaterialTheme.colorScheme.outline
            SafetyRating.ESSENTIAL -> MaterialTheme.colorScheme.error
        }
    Box(
        modifier = modifier.size(12.dp).clip(CircleShape).background(color),
    )
}

/** Dot plus rating name, for detail screens. */
@Composable
fun SafetyLabel(rating: SafetyRating, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SafetyDot(rating)
        Text(
            text = rating.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Small pill badge for the app kind. */
@Composable
fun KindBadge(kind: AppKind, modifier: Modifier = Modifier) {
    Pill(
        text = kind.name,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    )
}

/** Small pill badge for the assessed risk level. */
@Composable
fun RiskBadge(risk: RiskLevel, modifier: Modifier = Modifier) {
    val color =
        when (risk) {
            RiskLevel.LOW -> MaterialTheme.colorScheme.tertiaryContainer
            RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
            RiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
            RiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
        }
    val contentColor =
        when (risk) {
            RiskLevel.CRITICAL -> MaterialTheme.colorScheme.onError
            else -> MaterialTheme.colorScheme.onSurface
        }
    Pill(text = risk.name, color = color, contentColor = contentColor, modifier = modifier)
}

/** Small pill badge for an operation result status. */
@Composable
fun StatusBadge(status: OperationStatus, modifier: Modifier = Modifier) {
    val color =
        when (status) {
            OperationStatus.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
            OperationStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            OperationStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
        }
    Pill(text = status.name, color = color, modifier = modifier)
}

/**
 * String overload for persisted history rows ([OperationRecord.status] is stored as the enum name).
 * Unknown values degrade to FAILED rather than crashing the history screen on corrupt data.
 */
@Composable
fun StatusBadge(statusName: String, modifier: Modifier = Modifier) {
    val status = runCatching {
        OperationStatus.valueOf(statusName)
    }
        .getOrDefault(OperationStatus.FAILED)
    StatusBadge(status, modifier)
}

/** Small pill showing the operation label (e.g. "Uninstall for user"). */
@Composable
fun OperationBadge(type: OperationType, modifier: Modifier = Modifier) {
    Pill(
        text = type.label(),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    )
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
