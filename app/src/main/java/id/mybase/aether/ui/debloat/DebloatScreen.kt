package id.mybase.aether.ui.debloat

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.navigation.NavController
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.isReversible
import id.mybase.aether.domain.label
import id.mybase.aether.ui.apps.AppRow
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.EmptyState
import id.mybase.aether.ui.components.LoadingRow
import id.mybase.aether.ui.components.SafetyDot
import id.mybase.aether.ui.components.SectionTitle
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.packages.PackagesViewModel

private val OPERATIONS =
    listOf(
        OperationType.UNINSTALL_FOR_USER,
        OperationType.DISABLE,
    )

/**
 * Multi-select debloat list: pick apps with checkboxes, choose the operation, then continue to the
 * preview dialog.
 */
@Composable
fun DebloatScreen(
    packagesVm: PackagesViewModel,
    navController: NavController,
) {
    val uiState by packagesVm.uiState.collectAsState()
    val apps by packagesVm.visibleApps.collectAsState()
    var operation by rememberSaveable { mutableStateOf(OperationType.UNINSTALL_FOR_USER) }

    Scaffold(
        topBar = { AetherTopBar(title = "Debloat", navController = navController) },
        bottomBar = {
            Button(
                onClick = {
                    packagesVm.preparePreview(operation)
                    navController.navigate(Routes.PREVIEW_DIALOG)
                },
                enabled = uiState.selection.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text("Preview (${uiState.selection.size})")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionTitle("Operation")
            OPERATIONS.forEach { type ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { operation = type }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = operation == type,
                        onClick = { operation = type },
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(type.label(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text =
                                if (type.isReversible()) "Reversible"
                                else "Needs the original APK to restore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Apps (${apps.size})")
                TextButton(
                    onClick = {
                        apps.forEach { app ->
                            if (app.packageName !in uiState.selection) {
                                packagesVm.toggleSelect(app.packageName)
                            }
                        }
                    },
                    enabled = apps.isNotEmpty(),
                ) {
                    Text("Select all visible")
                }
            }
            Spacer(Modifier.height(4.dp))

            when {
                uiState.isLoading -> LoadingRow("Scanning installed apps…")
                uiState.error != null ->
                    EmptyState(
                        message = "Scan failed: ${uiState.error}",
                        actionLabel = "Retry",
                        onAction = packagesVm::loadApps,
                    )
                uiState.apps.isEmpty() ->
                    EmptyState(
                        message = "No apps scanned yet.",
                        actionLabel = "Scan now",
                        onAction = packagesVm::loadApps,
                    )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            val checked = app.packageName in uiState.selection
                            AppRow(
                                app = app,
                                onClick = { packagesVm.toggleSelect(app.packageName) },
                                ratingDot = { SafetyDot(packagesVm.ratingFor(app)) },
                                trailing = {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            packagesVm.toggleSelect(app.packageName)
                                        },
                                        modifier =
                                            Modifier.semantics {
                                                contentDescription = "Select ${app.label}"
                                            },
                                    )
                                },
                            )
                        }
                    }
            }
        }
    }
}
