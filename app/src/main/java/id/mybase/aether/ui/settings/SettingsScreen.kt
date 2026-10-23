package id.mybase.aether.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.ConfirmDialog
import id.mybase.aether.ui.components.MetaRow
import id.mybase.aether.ui.components.Section
import id.mybase.aether.ui.history.HistoryViewModel
import id.mybase.aether.ui.navigation.Routes

private const val APP_VERSION = "0.1.0"

/**
 * App settings: theme, last-used connection defaults, history clearing, onboarding reset and the
 * app version.
 */
@Composable
fun SettingsScreen(
    settingsVm: SettingsViewModel,
    historyVm: HistoryViewModel,
    navController: NavController,
    prefs: AetherPreferences,
) {
    val darkTheme by settingsVm.darkTheme.collectAsState(initial = true)
    val lastPairingPort by prefs.lastPairingPort.collectAsState(initial = null)
    val lastConnectionPort by prefs.lastConnectionPort.collectAsState(initial = null)
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear history?",
            message =
                "This permanently deletes all recorded operations. It does not undo the operations themselves.",
            confirmLabel = "Clear",
            onConfirm = {
                historyVm.clear()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
            destructive = true,
        )
    }

    Scaffold(
        topBar = { AetherTopBar(title = "Settings", navController = navController) },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(title = "Appearance") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark theme", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Use the dark color scheme.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = settingsVm::setDarkTheme,
                    )
                }
            }

            Section(title = "Connection defaults") {
                MetaRow("Last pairing port", lastPairingPort?.toString() ?: "not set")
                MetaRow("Last connection port", lastConnectionPort?.toString() ?: "not set")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ports are remembered from your last successful pairing / connection and prefill the setup guide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section(title = "Data") {
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear operation history")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        settingsVm.resetOnboarding()
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.DASHBOARD) { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset onboarding")
                }
            }

            Section(title = "About this app") {
                MetaRow("Version", APP_VERSION)
            }
        }
    }
}
