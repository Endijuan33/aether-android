package id.mybase.aether.ui.recovery

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.EmptyState
import id.mybase.aether.ui.components.InfoCard
import id.mybase.aether.ui.components.Section
import id.mybase.aether.ui.components.StatusBadge
import id.mybase.aether.ui.components.formatEpochMillis
import id.mybase.aether.ui.connection.ConnectionViewModel

/**
 * Recovery guidance plus the "re-enable everything AETHER disabled" action, driven by the persisted
 * operation history.
 */
@Composable
fun RecoveryScreen(
    recoveryVm: RecoveryViewModel,
    connectionVm: ConnectionViewModel,
    navController: NavController,
) {
    val refreshing by recoveryVm.isRefreshing.collectAsStateWithLifecycle()
    val warnings by recoveryVm.warnings.collectAsStateWithLifecycle()
    val disabled by recoveryVm.disabledByAether.collectAsStateWithLifecycle()
    val isWorking by recoveryVm.isWorking.collectAsStateWithLifecycle()
    val result by recoveryVm.result.collectAsStateWithLifecycle()
    val error by recoveryVm.error.collectAsStateWithLifecycle()
    val connected by connectionVm.state.collectAsStateWithLifecycle()
    val isConnected = connected is AdbConnectionState.Connected

    LaunchedEffect(Unit) { recoveryVm.refresh() }

    Scaffold(
        topBar = { AetherTopBar(title = "Recovery", navController = navController) },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoCard(
                title = "What the operations mean",
                body =
                    "Uninstall for user: removes the app for device owner user 0, including its data. " +
                        "The system image keeps a copy for system apps.\n\n" +
                        "Disable: switches the app off without removing it. Nothing is deleted.\n\n" +
                        "Enable: switches a disabled app back on.",
            )
            InfoCard(
                title = "How to restore",
                body =
                    "• Disabled apps can be re-enabled below, or manually with: " +
                        "adb shell pm enable --user 0 <package>\n" +
                        "• For a system app whose factory APK remains, install-existing --user 0 may restore availability. OEM policy can deny it.\n" +
                        "• Reinstallation does not recover deleted data. Other removed apps need their original APK or a trusted installer. AETHER provides no uninstall rollback or APK/data backup.",
            )

            Section(title = "Disabled by AETHER (${disabled.size})") {
                if (refreshing) Text("Refreshing recovery inventory…")
                if (warnings.isNotEmpty())
                    Text(
                        "Partial inventory: ${warnings.size} packages could not be read.",
                        color = MaterialTheme.colorScheme.error,
                    )
                if (disabled.isEmpty()) {
                    Text(
                        "No disabled-by-AETHER apps on record.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Fixed-height list inside the scrollable column.
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(disabled, key = { it.id }) { record ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            record.label.ifBlank { record.packageName },
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            "${record.packageName} · ${formatEpochMillis(record.timestamp)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    StatusBadge(OperationStatus.SUCCESS)
                                }
                            }
                        }
                    }
                }
            }

            if (isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Re-enabling…", style = MaterialTheme.typography.bodyMedium)
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            result?.let { results ->
                val ok = results.count { it.status == OperationStatus.SUCCESS }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Re-enabled $ok of ${results.size} apps.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            Button(
                onClick = { recoveryVm.reenableAll() },
                enabled = disabled.isNotEmpty() && isConnected && !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Re-enable all disabled by AETHER")
            }
            if (!isConnected) {
                Text(
                    "Connect ADB to re-enable apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (disabled.isEmpty()) {
                EmptyState(message = "Nothing to recover.")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
