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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val darkTheme by settingsVm.darkTheme.collectAsStateWithLifecycle(initialValue = true)
    val saving by settingsVm.isSaving.collectAsStateWithLifecycle()
    val error by settingsVm.error.collectAsStateWithLifecycle()
    val historyError by historyVm.error.collectAsStateWithLifecycle()
    val onboardingCompleted by
        settingsVm.onboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
    var resetRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(resetRequested, onboardingCompleted) {
        if (resetRequested && onboardingCompleted == false) {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(Routes.DASHBOARD) { inclusive = true }
            }
        }
    }
    val lastPairingPort by prefs.lastPairingPort.collectAsStateWithLifecycle(initialValue = null)
    val lastConnectionPort by
        prefs.lastConnectionPort.collectAsStateWithLifecycle(initialValue = null)
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear history?",
            message =
                "This deletes completed history without undoing operations. Unverified interrupted records are retained for safety.",
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
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                        modifier = Modifier.semantics { contentDescription = "Dark theme" },
                        checked = darkTheme,
                        onCheckedChange = settingsVm::setDarkTheme,
                        enabled = !saving,
                    )
                }
            }

            Section(title = "Connection defaults") {
                MetaRow("Last pairing port", lastPairingPort?.toString() ?: "not set")
                MetaRow("Last connection port", lastConnectionPort?.toString() ?: "not set")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Normal pairing discovers fresh endpoints. Remembered ports are for advanced troubleshooting only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = { navController.navigate(Routes.ADVANCED_PAIRING) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Advanced manual pairing & connection")
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
                        resetRequested = true
                    },
                    enabled = !saving,
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
