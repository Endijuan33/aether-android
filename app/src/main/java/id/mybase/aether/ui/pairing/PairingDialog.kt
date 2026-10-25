package id.mybase.aether.ui.pairing

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.mybase.aether.security.InputValidators
import id.mybase.aether.ui.components.SectionTitle
import id.mybase.aether.ui.connection.ConnectionViewModel

/**
 * Six-box pairing-code entry shown as a dialog.
 *
 * Digits are filtered through [InputValidators.filterPairingDigits], so pasting a code works; the
 * boxes reflect the filtered value. Pairing runs through [ConnectionViewModel.pair] and the dialog
 * dismisses itself after the pairing client reports success. Navigation owns the dialog window.
 */
@Composable
fun PairingDialog(
    connectionVm: ConnectionViewModel,
    navController: NavController,
) {
    val host by connectionVm.pendingHost.collectAsStateWithLifecycle()
    val port by connectionVm.pendingPairingPort.collectAsStateWithLifecycle()
    val pairingError by connectionVm.pairingError.collectAsStateWithLifecycle()
    val isPairing by connectionVm.isPairing.collectAsStateWithLifecycle()
    val pairingSucceeded by connectionVm.pairingSucceeded.collectAsStateWithLifecycle()

    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        connectionVm.clearPairingError()
        focusRequester.requestFocus()
    }
    LaunchedEffect(pairingSucceeded) {
        if (pairingSucceeded) {
            navController.popBackStack()
        }
    }
    DisposableEffect(connectionVm) {
        onDispose { connectionVm.cancelPairing() }
    }

    val targetReady = host != null && port != null
    val canConfirm =
        targetReady && code.length == 6 && InputValidators.isValidPairingCode(code) && !isPairing

    Card {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionTitle("Enter pairing code")
            Spacer(Modifier.height(8.dp))
            if (targetReady) {
                Text(
                    text = "Pairing with $host:$port. Type the 6-digit code shown on the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "No pairing target set. Go back and start pairing again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))

            BasicTextField(
                value = code,
                onValueChange = { code = InputValidators.filterPairingDigits(it).take(6) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                enabled = !isPairing,
                textStyle =
                    androidx.compose.ui.text.TextStyle(
                        color = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                modifier =
                    Modifier.fillMaxWidth().focusRequester(focusRequester).semantics {
                        contentDescription = "Six-digit pairing code"
                    },
                decorationBox = { innerTextField ->
                    Box {
                        innerTextField()
                        CodeBoxes(code = code)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            pairingError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (isPairing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Pairing…", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        connectionVm.cancelPairing()
                        navController.popBackStack()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        connectionVm.pair(host!!, port!!, code)
                        code = ""
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

@Composable
private fun CodeBoxes(code: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(6) { index ->
            val digit = code.getOrNull(index)?.toString() ?: ""
            val filled = digit.isNotEmpty()
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(48.dp)
                        .border(
                            width = 1.dp,
                            color =
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = digit,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
