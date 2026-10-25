package id.mybase.aether.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.mybase.aether.ui.components.SectionTitle
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.settings.SettingsViewModel

private val STEPS =
    listOf(
        "Open Settings on this Android device.",
        "Go to System, then Developer options. If Developer options is hidden, enable it first: Settings > About phone > tap Build number 7 times.",
        "Turn on Wireless debugging.",
        "In AETHER, tap Pair & Connect. Open \"Pair device with pairing code\" in Android and enter its six-digit code in AETHER’s notification.",
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
    val completed by
        settingsVm.onboardingCompleted.collectAsStateWithLifecycle(initialValue = false)
    val saving by settingsVm.isSaving.collectAsStateWithLifecycle()
    val error by settingsVm.error.collectAsStateWithLifecycle()
    LaunchedEffect(completed) {
        if (completed)
            navController.navigate(Routes.DASHBOARD) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
            }
    }

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
                        "AETHER discovers the pairing and connection ports automatically. Follow the system setup steps, then use notification pairing.",
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
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                        )
                    } catch (e: ActivityNotFoundException) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                    context,
                                    "Open Settings manually to continue.",
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                    }
                },
            ) {
                Text("Open Developer Settings")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    settingsVm.completeOnboarding()
                },
                enabled = !saving,
            ) {
                Text("Continue")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
