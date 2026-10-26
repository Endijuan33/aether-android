package id.mybase.aether.ui.apps

import android.content.ClipData
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.domain.isReversible
import id.mybase.aether.domain.kind
import id.mybase.aether.domain.label
import id.mybase.aether.packages.SafetyCatalogue
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.EmptyState
import id.mybase.aether.ui.components.KindBadge
import id.mybase.aether.ui.components.MetaRow
import id.mybase.aether.ui.components.SafetyLabel
import id.mybase.aether.ui.components.Section
import id.mybase.aether.ui.components.formatEpochMillis
import id.mybase.aether.ui.connection.ConnectionViewModel
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.packages.PackagesViewModel
import kotlinx.coroutines.launch

/**
 * Details for one installed package: identity, safety rating with catalogue note, permissions,
 * metadata and the available ADB operations.
 *
 * Operation buttons are enabled only while ADB is connected and only when the operation applies
 * (e.g. Enable is offered solely for disabled apps). Each action opens the preview dialog for that
 * single operation.
 */
@Composable
fun AppDetailsScreen(
    packageName: String,
    packagesVm: PackagesViewModel,
    connectionVm: ConnectionViewModel,
    catalogue: SafetyCatalogue,
    navController: NavController,
) {
    val uiState by packagesVm.uiState.collectAsStateWithLifecycle()
    val connected by packagesVm.isConnected.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val app = uiState.apps.find { it.packageName == packageName }

    Scaffold(
        topBar = { AetherTopBar(title = "App details", navController = navController) },
    ) { padding ->
        if (app == null) {
            EmptyState(
                message = "App not found. It may have been removed, or the list was never scanned.",
                actionLabel = "Scan apps",
                onAction = {
                    packagesVm.loadApps()
                    navController.popBackStack()
                },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val rating = packagesVm.ratingFor(app)
        val entry = catalogue.entryFor(app.packageName)

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
                    id.mybase.aether.ui.components.PackageIcon(app.packageName)
                    Text(
                        app.label.ifBlank { app.packageName },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText("Package name", app.packageName),
                                        ),
                                    )
                                }
                            },
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy package name")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KindBadge(app.kind())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Version ${app.versionName ?: "unknown"} (${app.versionCode})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Section(title = "Safety") {
                        SafetyLabel(rating)
                        Spacer(Modifier.height(8.dp))
                        if (entry != null) {
                            Text(
                                text = entry.note,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Source: ${entry.source} · catalogue rev ${entry.revision}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text =
                                    "Not listed in the local safety catalogue — proceed with extra caution.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = ratingGuidance(rating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Section(title = "Permissions (${app.permissions.size})") {
                        if (app.permissions.isEmpty()) {
                            Text(
                                "No permissions declared.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            app.permissions.sorted().forEach { permission ->
                                Text(
                                    permission,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Section(title = "Metadata") {
                        MetaRow("Installer", app.installerPackageName ?: "unknown")
                        MetaRow("Target SDK", app.targetSdk.toString())
                        MetaRow("First installed", formatEpochMillis(app.firstInstallTime))
                        MetaRow("Last updated", formatEpochMillis(app.lastUpdateTime))
                        MetaRow("Enabled", if (app.isEnabled) "yes" else "no")
                    }
                }
            }

            Section(title = "Operations") {
                if (!connected) {
                    Text(
                        text = "Connect ADB to manage this app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { navController.navigate(Routes.SETUP_GUIDE) }) {
                        Text("Go to setup guide")
                    }
                } else {
                    OperationButton(
                        label = OperationType.UNINSTALL_FOR_USER.label(),
                        enabled = true,
                        note =
                            if (OperationType.UNINSTALL_FOR_USER.isReversible())
                                "Reversible: the app can be reinstalled for the user."
                            else "Not reversible via AETHER: needs the original APK.",
                        onClick = {
                            packagesVm.prepareSinglePreview(
                                app.packageName,
                                OperationType.UNINSTALL_FOR_USER,
                            )
                            navController.navigate(Routes.PREVIEW_DIALOG)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    OperationButton(
                        label = OperationType.DISABLE.label(),
                        enabled = app.isEnabled,
                        note =
                            if (app.isEnabled) "Disables the app without removing it."
                            else "Already disabled.",
                        onClick = {
                            packagesVm.prepareSinglePreview(app.packageName, OperationType.DISABLE)
                            navController.navigate(Routes.PREVIEW_DIALOG)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    OperationButton(
                        label = OperationType.ENABLE.label(),
                        enabled = !app.isEnabled,
                        note =
                            if (!app.isEnabled) "Re-enables a disabled app."
                            else "Only disabled apps can be enabled.",
                        onClick = {
                            packagesVm.prepareSinglePreview(app.packageName, OperationType.ENABLE)
                            navController.navigate(Routes.PREVIEW_DIALOG)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationButton(
    label: String,
    enabled: Boolean,
    note: String,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(label)
        }
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Short guidance line for a safety rating. */
private fun ratingGuidance(rating: SafetyRating): String =
    when (rating) {
        SafetyRating.SAFE -> "Rated safe to remove or disable."
        SafetyRating.RISKY -> "Rated risky: removing it may affect features you use."
        SafetyRating.UNKNOWN -> "No safety assessment available."
        SafetyRating.ESSENTIAL ->
            "Rated essential: removing or disabling it can break core device functions."
    }
