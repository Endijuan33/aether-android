package id.mybase.aether.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.mybase.aether.ui.components.SectionTitle
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.settings.SettingsViewModel

private val STEPS =
    listOf(
        "Open Settings on this Android device.",
        "Go to System, then Developer options. If Developer options is hidden, enable it first: Settings > About phone > tap Build number 7 times.",
        "Turn on Wireless debugging.",
        "Tap \"Pair device with pairing code\" and keep the code on screen — AETHER will ask for it next.",
    )

/**
 * First-run guide for enabling wireless debugging, which AETHER needs in order to manage apps over
 * ADB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settingsVm: SettingsViewModel,
    navController: NavController,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Welcome to AETHER") }) },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Set up wireless debugging")
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    "AETHER manages apps on this device over ADB. " +
                        "Follow these steps once — afterwards pairing takes seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            STEPS.forEachIndexed { index, step ->
                NumberedStep(number = index + 1, text = step)
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                },
            ) {
                Text("Open Developer Settings")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    settingsVm.completeOnboarding()
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            ) {
                Text("Continue")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You can revisit this guide any time from the setup screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
