package id.mybase.aether.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.mybase.aether.adb.pairing.*
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.InfoCard
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.pairing.PairingActions
import id.mybase.aether.ui.pairing.WirelessConnectionCard

/** Primary workflow: notification pairing; manual endpoints live only in advanced setup. */
@Composable
fun SetupGuideScreen(
    controller: WirelessPairingController,
    actions: PairingActions,
    navController: NavController,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var showInput by remember { mutableStateOf(false) }
    // Secrets deliberately use remember, never rememberSaveable/SavedStateHandle.
    var code by remember { mutableStateOf("") }
    LaunchedEffect(state.replyToken) { code = "" }
    Scaffold(topBar = { AetherTopBar(title = "Pair & Connect", navController = navController) }) {
        padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoCard(
                "Pair this Android device",
                "1. Tap Pair & Connect and allow notifications.\n" +
                    "2. Open Developer options → Wireless debugging. Enable it and open Pair device with pairing code.\n" +
                    "3. Expand AETHER’s notification, tap PAIR and enter the six-digit code.\n" +
                    "AETHER then discovers the separate connection port and verifies shell access automatically.",
            )
            WirelessConnectionCard(state)
            if (state.phase == PairingPhase.WAITING_FOR_PERMISSION) {
                OutlinedButton(
                    onClick = actions.openAppSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open app permissions")
                }
            }
            Button(
                onClick = { actions.start(false, false) },
                enabled = !state.active && !state.ready,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("PAIR & CONNECT")
            }
            OutlinedButton(
                onClick = actions.openDeveloperOptions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Developer options")
            }
            id.mybase.aether.ui.pairing.DeveloperOptionsStatus()
            if (state.active || state.ready) {
                OutlinedButton(onClick = actions.cancel, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.ready) "Disconnect" else "Cancel pairing")
                }
            }
            if (!state.active && !state.ready) {
                TextButton(onClick = { actions.start(false, true) }) {
                    Text("Reconnect with existing pairing identity")
                }
            }
            HorizontalDivider()
            Text("Notification input unavailable?", style = MaterialTheme.typography.titleMedium)
            Text(
                "If your notification shade does not support inline reply, keep AETHER open and use discovered in-app input. No address or port is needed. You may need split screen to keep Android’s pairing-code dialog open.",
            )
            TextButton(onClick = actions.openNotificationSettings) {
                Text("Open notification settings")
            }
            if (!state.active && !state.ready) {
                OutlinedButton(
                    onClick = {
                        showInput = true
                        actions.start(true, false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start in-app discovery fallback")
                }
                TextButton(onClick = { actions.start(true, true) }) { Text("Reconnect in app") }
            }
            if (state.acceptsCode) {
                TextButton(
                    onClick = {
                        showInput = !showInput
                        code = ""
                    },
                ) {
                    Text(if (showInput) "Hide in-app input" else "Use in-app code input")
                }
                if (showInput) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it in '0'..'9' }) code = input
                        },
                        label = { Text("Six-digit pairing code") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            controller.submit(state.sessionId, state.replyToken, code)
                            code = ""
                        },
                        enabled = PairingCode.isValid(code),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Submit pairing code")
                    }
                }
            }
            TextButton(
                onClick = { navController.navigate(Routes.ADVANCED_PAIRING) },
                enabled = !state.active,
            ) {
                Text("Advanced manual troubleshooting")
            }
            TextButton(onClick = { navController.navigate(Routes.DIAGNOSTICS) }) {
                Text("Connection diagnostics")
            }
        }
    }
}
