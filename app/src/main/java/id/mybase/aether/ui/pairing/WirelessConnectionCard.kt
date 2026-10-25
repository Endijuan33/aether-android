package id.mybase.aether.ui.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.mybase.aether.adb.pairing.*
import id.mybase.aether.ui.components.MetaRow
import id.mybase.aether.ui.components.formatEpochMillis

@Composable
fun WirelessConnectionCard(state: WirelessPairingState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wireless Debugging", style = MaterialTheme.typography.titleMedium)
            Text(
                when (state.phase) {
                    PairingPhase.IDLE ->
                        if (state.paired) "Paired — reconnect to verify shell"
                        else "Ready to pair this device"
                    PairingPhase.WAITING_FOR_PERMISSION -> "Permission needed"
                    PairingPhase.PREPARING -> "Preparing pairing…"
                    PairingPhase.SEARCHING_PAIRING_SERVICE -> "Searching for pairing service…"
                    PairingPhase.WAITING_FOR_PAIRING_CODE ->
                        if (state.pairingEndpoint == null)
                            "Waiting for pairing code — discovery is running"
                        else "Pairing service found — enter the code in AETHER’s notification"
                    PairingPhase.PAIRING_IN_PROGRESS -> "Pairing authentication in progress…"
                    PairingPhase.PAIRING_SUCCESS -> "Pairing verified"
                    PairingPhase.DISCOVERING_CONNECT_ENDPOINT ->
                        "Discovering ADB connection endpoint…"
                    PairingPhase.CONNECTING -> "Connecting ADB transport…"
                    PairingPhase.VERIFYING_SHELL -> "Transport established — verifying shell…"
                    PairingPhase.CONNECTED -> "Connected — shell verified"
                    PairingPhase.DISCONNECTING -> "Disconnecting…"
                    PairingPhase.DISCONNECTED -> "Disconnected"
                    PairingPhase.PAIRING_FAILED -> "Pairing failed"
                    PairingPhase.CONNECTION_FAILED -> "Connection failed"
                    PairingPhase.TIMED_OUT -> "Discovery or session timed out — retry available"
                    PairingPhase.CANCELLED -> "Pairing cancelled"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            MetaRow("Pairing", if (state.paired) "Authenticated" else "Not verified")
            MetaRow("ADB transport", if (state.transportVerified) "Established" else "Not verified")
            MetaRow("Shell", if (state.ready) "UID 2000 verified" else "Not available")
            state.connectedAt?.let { MetaRow("Last connection", formatEpochMillis(it)) }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (state.active && state.phase != PairingPhase.WAITING_FOR_PAIRING_CODE) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
