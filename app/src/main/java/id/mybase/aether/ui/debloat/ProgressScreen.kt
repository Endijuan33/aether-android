package id.mybase.aether.ui.debloat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.EmptyState
import id.mybase.aether.ui.components.OperationBadge
import id.mybase.aether.ui.components.SectionTitle
import id.mybase.aether.ui.components.StatusBadge
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.packages.PackagesViewModel

/**
 * Live execution progress: progress bar, current package, per-package processed list, then a
 * summary with per-result rows and a shortcut to the history screen.
 */
@Composable
fun ProgressScreen(
    packagesVm: PackagesViewModel,
    navController: NavController,
) {
    val isExecuting by packagesVm.isExecuting.collectAsState()
    val execution by packagesVm.execution.collectAsState()
    val completedLabels by packagesVm.completedLabels.collectAsState()
    val lastResults by packagesVm.lastResults.collectAsState()
    val executionError by packagesVm.executionError.collectAsState()

    Scaffold(
        topBar = { AetherTopBar(title = "Executing", navController = navController) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                executionError != null -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            SectionTitle("Execution failed")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                executionError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            packagesVm.clearExecution()
                            navController.popBackStack()
                        },
                    ) {
                        Text("Back")
                    }
                }

                lastResults == null -> {
                    val progress = execution
                    if (progress == null && !isExecuting) {
                        EmptyState(
                            message = "No operation in progress.",
                            actionLabel = "Back",
                            onAction = { navController.popBackStack() },
                        )
                    } else {
                        val done = progress?.done ?: 0
                        val total = progress?.total ?: 0
                        SectionTitle("Working… $done of $total")
                        LinearProgressIndicator(
                            progress = { if (total > 0) done.toFloat() / total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val current = progress?.current.orEmpty()
                        if (current.isNotBlank()) {
                            Text(
                                "Current: $current",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SectionTitle("Processed (${completedLabels.size})")
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(completedLabels) { label ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    val results = lastResults!!
                    val ok = results.count { it.status == OperationStatus.SUCCESS }
                    val failed = results.count { it.status == OperationStatus.FAILED }
                    val skipped = results.count { it.status == OperationStatus.SKIPPED }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            SectionTitle("Finished")
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$ok succeeded · $failed failed · $skipped skipped",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { it.operation.app.packageName }) { result ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            result.operation.app.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        StatusBadge(result.status)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    OperationBadge(result.operation.type)
                                    if (result.message.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            result.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                packagesVm.clearExecution()
                                navController.navigate(Routes.HISTORY)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("View history")
                        }
                        OutlinedButton(
                            onClick = {
                                packagesVm.clearExecution()
                                navController.popBackStack(Routes.APPS, inclusive = false)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Back to apps")
                        }
                    }
                }
            }
        }
    }
}
