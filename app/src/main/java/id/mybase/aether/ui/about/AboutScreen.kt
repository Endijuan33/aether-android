package id.mybase.aether.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.InfoCard

private val ATTRIBUTIONS =
    listOf(
        "Kotlin — programming language (Apache-2.0)",
        "Jetpack Compose — declarative UI toolkit (Apache-2.0)",
        "Material 3 — design components (Apache-2.0)",
        "Navigation Compose — in-app navigation (Apache-2.0)",
        "Kotlin Coroutines & Flow — async work (Apache-2.0)",
        "DataStore — preferences storage (Apache-2.0)",
        "AndroidX SplashScreen — launch screen (Apache-2.0)",
    )

/**
 * About AETHER: what it does, licensing, open-source attributions, privacy summary and
 * hardware-validation status.
 */
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = { AetherTopBar(title = "About", navController = navController) },
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
                title = "AETHER",
                body =
                    "AETHER is an on-device Android debloat companion. It pairs with your " +
                        "device over wireless debugging (ADB), scans installed packages, classifies " +
                        "them against a local safety catalogue, and lets you uninstall-for-user, " +
                        "disable or re-enable apps — with a preview, a risk assessment and a full " +
                        "operation history for every change.",
            )
            InfoCard(
                title = "License",
                body =
                    "Licensed under the Apache License, Version 2.0. You may use, modify and " +
                        "distribute this software under the terms of that license, including the " +
                        "patent grant and attribution requirements. The full license text ships " +
                        "with the source distribution.",
            )
            InfoCard(
                title = "Open-source attributions",
                body = ATTRIBUTIONS.joinToString("\n• ", prefix = "• "),
            )
            InfoCard(
                title = "Privacy",
                body =
                    "Everything runs on your devices. AETHER talks only to the Android device " +
                        "you pair it with over your local network. It collects no analytics, creates " +
                        "no accounts and sends nothing to the internet. Pairing codes are kept in " +
                        "memory only for the pairing attempt and are never stored or logged.",
            )
            InfoCard(
                title = "Validation status",
                body =
                    "Hardware-validated against real-device wireless debugging (ADB over Wi-Fi). " +
                        "No emulator or rooted device is required; wireless debugging is available on " +
                        "stock Android 11 and later.",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "AETHER 0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
