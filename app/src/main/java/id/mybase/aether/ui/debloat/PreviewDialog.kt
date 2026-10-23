package id.mybase.aether.ui.debloat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.RiskLevel
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.domain.kind
import id.mybase.aether.domain.label
import id.mybase.aether.ui.components.KindBadge
import id.mybase.aether.ui.components.OperationBadge
import id.mybase.aether.ui.components.RiskBadge
import id.mybase.aether.ui.components.SafetyLabel
import id.mybase.aether.ui.components.SectionTitle
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.packages.PackagesViewModel
import id.mybase.aether.ui.packages.PreviewItem

/**
 * Pre-execution preview shown as a dialog: per item the app, its classification, the operation, the
 * risk badge and reversibility, plus a recovery-limitation note. ESSENTIAL or CRITICAL items raise
 * a blocking warning that requires an explicit "I understand the risk" confirmation. Confirming
 * executes and moves to the progress screen.
 */
@Composable
fun PreviewDialog(
    packagesVm: PackagesViewModel,
    navController: NavController,
) {
    val items by packagesVm.previewItems.collectAsState()
    val type by packagesVm.previewType.collectAsState()
    var acknowledged by rememberSaveable { mutableStateOf(false) }

    val blocking = items.any {
        it.rating == SafetyRating.ESSENTIAL || it.risk == RiskLevel.CRITICAL
    }
    val canConfirm = items.isNotEmpty() && type != null && (!blocking || acknowledged)

    Dialog(
        onDismissRequest = { navController.popBackStack() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                SectionTitle("Preview (${items.size})")
                Spacer(Modifier.height(4.dp))
                type?.let { OperationBadge(it) }
                Spacer(Modifier.height(12.dp))

                if (items.isEmpty()) {
                    Text(
                        "Nothing to preview. Select at least one app first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items, key = { it.app.packageName }) { item ->
                            PreviewItemCard(item = item, type = type!!)
                        }
                    }
                }

                if (blocking) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Blocking warning",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text =
                                    "This selection includes ESSENTIAL apps or CRITICAL-risk operations. " +
                                        "Removing or disabling them can break core device functions, " +
                                        "and recovery may require the original APK or a factory reset.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = acknowledged,
                                    onCheckedChange = { acknowledged = it },
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription = "I understand the risk"
                                        },
                                )
                                Text(
                                    "I understand the risk",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = {
                            packagesVm.clearPreview()
                            navController.popBackStack()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            packagesVm.executePreview()
                            navController.navigate(Routes.PROGRESS) {
                                popUpTo(Routes.PREVIEW_DIALOG) { inclusive = true }
                            }
                        },
                        enabled = canConfirm,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewItemCard(item: PreviewItem, type: OperationType) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                item.app.label.ifBlank { item.app.packageName },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                item.app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindBadge(item.app.kind())
                RiskBadge(item.risk)
            }
            Spacer(Modifier.height(8.dp))
            SafetyLabel(item.rating)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Operation: ${type.label()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    if (item.reversible) {
                        "Reversible: yes — it can be restored without the APK."
                    } else {
                        "Reversible: no — restoring it needs the original APK."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
