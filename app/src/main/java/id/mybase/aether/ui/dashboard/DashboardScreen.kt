package id.mybase.aether.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.mybase.aether.adb.pairing.WirelessPairingController
import id.mybase.aether.domain.AppKind
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.kind
import id.mybase.aether.domain.label
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.ui.components.MetaRow
import id.mybase.aether.ui.components.Section
import id.mybase.aether.ui.components.StatusBadge
import id.mybase.aether.ui.components.formatEpochMillis
import id.mybase.aether.ui.connection.ConnectionViewModel
import id.mybase.aether.ui.history.HistoryViewModel
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.packages.PackagesViewModel
import id.mybase.aether.ui.pairing.PairingActions
import id.mybase.aether.ui.pairing.WirelessConnectionCard

/**
 * Home screen: connection state, scanned package counts, recent operations, and the three primary
 * actions (connect, scan, manage).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    connectionVm: ConnectionViewModel,
    packagesVm: PackagesViewModel,
    historyVm: HistoryViewModel,
    navController: NavController,
    prefs: AetherPreferences,
    pairingController: WirelessPairingController,
    pairingActions: PairingActions,
) {
    val state by pairingController.state.collectAsStateWithLifecycle()
    val uiState by packagesVm.uiState.collectAsStateWithLifecycle()
    val records by historyVm.records.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { historyVm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AETHER") },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.HISTORY) }) {
                        Icon(Icons.Filled.History, contentDescription = "Operation history")
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { WirelessConnectionCard(state = state) }
            item {
                OutlinedButton(
                    onClick = { navController.navigate(Routes.DEVICE_RECOMMENDATIONS) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Device information and recommendations")
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Section(title = "Packages") {
                            if (uiState.apps.isEmpty()) {
                                Text(
                                    text =
                                        "Not scanned yet. Scan to see what is installed on this device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val apps = uiState.apps
                                MetaRow(
                                    "User apps",
                                    apps.count { it.kind() == AppKind.USER }.toString(),
                                )
                                MetaRow(
                                    "System apps",
                                    apps
                                        .count {
                                            it.kind() == AppKind.SYSTEM ||
                                                it.kind() == AppKind.UPDATED_SYSTEM
                                        }
                                        .toString(),
                                )
                                MetaRow(
                                    "Disabled",
                                    apps.count { it.kind() == AppKind.DISABLED }.toString(),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            pairingActions.start(false, false)
                            navController.navigate(Routes.SETUP_GUIDE)
                        },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (state.active || state.ready) "Connection details"
                            else "PAIR & CONNECT",
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            packagesVm.loadApps()
                            navController.navigate(Routes.APPS)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Scan Apps")
                    }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.DEBLOAT) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Manage Apps")
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Section(title = "Recent operations") {
                            val recent = records.take(5)
                            if (recent.isEmpty()) {
                                Text(
                                    text = "No operations recorded yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                recent.forEachIndexed { index, record ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        id.mybase.aether.ui.components.PackageIcon(
                                            record.packageName,
                                            Modifier.padding(end = 12.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                record.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                "${operationLabel(record.operation)} · ${formatEpochMillis(record.timestamp)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        StatusBadge(record.status)
                                    }
                                    if (index < recent.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** History stores the operation as its enum name; map it back for display. */
private fun operationLabel(stored: String): String =
    OperationType.entries.firstOrNull { it.name == stored }?.label() ?: stored
