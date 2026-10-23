package id.mybase.aether.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.ui.components.ActionRow
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.InfoCard
import id.mybase.aether.ui.components.Section
import id.mybase.aether.ui.components.SectionTitle
import id.mybase.aether.ui.connection.ConnectionViewModel

/**
 * Connection troubleshooting: current state, last known endpoint, a redacted diagnostics dump (safe
 * to share — no codes or keys), and reconnect / disconnect actions.
 */
@Composable
fun DiagnosticsScreen(
    connectionVm: ConnectionViewModel,
    navController: NavController,
    prefs: AetherPreferences,
) {
    val state by connectionVm.state.collectAsState()
    val connectError by connectionVm.connectError.collectAsState()
    val lastHost by prefs.lastHost.collectAsState(initial = null)
    val lastConnectionPort by prefs.lastConnectionPort.collectAsState(initial = null)
    val clipboard = LocalClipboardManager.current

    val canReconnect =
        lastHost != null &&
            lastConnectionPort != null &&
            state !is AdbConnectionState.Connected &&
            state !is AdbConnectionState.Connecting
    val canDisconnect = state !is AdbConnectionState.Disconnected

    Scaffold(
        topBar = { AetherTopBar(title = "Diagnostics", navController = navController) },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Connection state")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.describe(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    connectError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Last host: ${lastHost ?: "none"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Last connection port: ${lastConnectionPort?.toString() ?: "none"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Section(title = "Redacted diagnostics") {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = connectionVm.redactedDiagnostics(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(connectionVm.redactedDiagnostics()))
                    },
                ) {
                    Text("Copy diagnostics")
                }
            }

            ActionRow(
                primaryLabel = "Reconnect",
                onPrimary = { connectionVm.connect(lastHost!!, lastConnectionPort!!) },
                primaryEnabled = canReconnect,
                secondaryLabel = "Disconnect",
                onSecondary = { connectionVm.disconnect() },
                secondaryEnabled = canDisconnect,
            )
            if (!canReconnect && state !is AdbConnectionState.Connected) {
                Text(
                    text =
                        "Reconnect is unavailable: no previous connection to retry. Pair and connect from the setup guide first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InfoCard(
                title = "Troubleshooting tips",
                body =
                    "• The device and this phone must be on the same Wi-Fi network.\n" +
                        "• Wireless debugging turns itself off when the device restarts — re-enable it and pair again.\n" +
                        "• If pairing times out, tap \"Pair device with pairing code\" again to get a fresh code.\n" +
                        "• \"Connection refused\" usually means the IP or port changed; check the Wireless debugging screen.",
            )
        }
    }
}

private fun AdbConnectionState.describe(): String =
    when (this) {
        is AdbConnectionState.Disconnected -> "Disconnected"
        is AdbConnectionState.Pairing -> "Pairing with $host:$port…"
        is AdbConnectionState.Paired -> "Paired (not yet connected)"
        is AdbConnectionState.Connecting -> "Connecting to $host:$port…"
        is AdbConnectionState.Connected -> "Connected to $host:$port"
        is AdbConnectionState.Failed -> "Failed: $reason"
    }
