package id.mybase.aether.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.security.InputValidators
import id.mybase.aether.ui.components.ActionRow
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.InfoCard
import id.mybase.aether.ui.components.Section
import id.mybase.aether.ui.connection.ConnectionViewModel
import id.mybase.aether.ui.navigation.Routes

/**
 * Pairing and connection setup: explains wireless debugging, collects the host plus the pairing and
 * connection ports as separate fields (prefilled from the last used values), and routes into the
 * pairing dialog.
 */
@Composable
fun SetupGuideScreen(
    connectionVm: ConnectionViewModel,
    navController: NavController,
    prefs: AetherPreferences,
) {
    val lastHost by prefs.lastHost.collectAsState(initial = null)
    val lastPairingPort by prefs.lastPairingPort.collectAsState(initial = null)
    val lastConnectionPort by prefs.lastConnectionPort.collectAsState(initial = null)
    val connectError by connectionVm.connectError.collectAsState()

    var host by rememberSaveable(lastHost) { mutableStateOf(lastHost ?: "") }
    var pairingPort by
        rememberSaveable(lastPairingPort) { mutableStateOf(lastPairingPort?.toString() ?: "") }
    var connectionPort by
        rememberSaveable(lastConnectionPort) {
            mutableStateOf(lastConnectionPort?.toString() ?: "")
        }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { AetherTopBar(title = "Connect a device", navController = navController) },
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
                title = "How pairing works",
                body =
                    "1. On the device, open Settings > System > Developer options and enable Wireless debugging.\n" +
                        "2. Tap \"Pair device with pairing code\" — note the IP, pairing port and 6-digit code.\n" +
                        "3. Enter them below and start pairing, then connect with the ADB port.",
            )

            Section(title = "Device address") {
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        formError = null
                    },
                    label = { Text("Device IP / host") },
                    placeholder = { Text("192.168.1.10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pairingPort,
                    onValueChange = {
                        pairingPort = it.filter(Char::isDigit)
                        formError = null
                    },
                    label = { Text("Pairing port") },
                    placeholder = { Text("e.g. 37001") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = connectionPort,
                    onValueChange = {
                        connectionPort = it.filter(Char::isDigit)
                        formError = null
                    },
                    label = { Text("ADB connection port") },
                    placeholder = { Text("e.g. 5555") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            formError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            connectError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ActionRow(
                primaryLabel = "Start Pairing",
                onPrimary = {
                    val parsedPort = InputValidators.parsePort(pairingPort)
                    when {
                        !InputValidators.isValidHost(host.trim()) ->
                            formError = "Enter the device IP shown under Wireless debugging."
                        parsedPort == null ->
                            formError = "Enter the pairing port shown next to the pairing code."
                        else -> {
                            formError = null
                            connectionVm.clearConnectError()
                            connectionVm.beginPairing(host.trim(), parsedPort)
                            navController.navigate(Routes.PAIRING_DIALOG)
                        }
                    }
                },
                secondaryLabel = "Connect",
                onSecondary = {
                    val parsedPort = InputValidators.parsePort(connectionPort)
                    when {
                        !InputValidators.isValidHost(host.trim()) ->
                            formError = "Enter the device IP shown under Wireless debugging."
                        parsedPort == null ->
                            formError =
                                "Enter the ADB connection port shown under Wireless debugging."
                        else -> {
                            formError = null
                            connectionVm.connect(host.trim(), parsedPort)
                        }
                    }
                },
            )

            TextButton(onClick = { navController.navigate(Routes.DIAGNOSTICS) }) {
                Text("Troubleshooting tips")
            }
        }
    }
}
