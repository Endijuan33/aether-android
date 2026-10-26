package id.mybase.aether.ui.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.mybase.aether.device.DeviceProfile
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.label
import id.mybase.aether.packages.*
import id.mybase.aether.ui.components.*
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.packages.PackagesViewModel

@Composable
fun DeviceInformation(profile: DeviceProfile?) {
    if (profile == null) {
        Text("Device information unavailable while detection is pending.")
        return
    }
    val build = profile.build
    Text("Device Information", style = MaterialTheme.typography.titleMedium)
    MetaRow("Device", "${build.brand} ${build.model}")
    MetaRow("Manufacturer", build.manufacturer.ifBlank { "Unknown" })
    MetaRow("Model", build.model.ifBlank { "Unknown" })
    MetaRow("Codename", build.device.ifBlank { "Unknown" })
    MetaRow("Android", "${build.androidRelease} (API ${build.sdk})")
    MetaRow("OEM software", profile.oemFamily.name.replace('_', ' '))
    MetaRow("OEM version", profile.oemVersion ?: "Unavailable")
    MetaRow("Active catalogue", profile.catalogueProfile)
    MetaRow("Detection confidence", profile.confidence.name)
    profile.limitations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun RecommendationDetails(recommendation: PackageRecommendation?) {
    var expanded by rememberSaveable(recommendation?.packageName) { mutableStateOf(false) }
    if (recommendation == null) {
        Text(
            "UNKNOWN: No applicable package evidence. Disable and uninstall safety have not been established.",
        )
        return
    }
    Text(
        "Device assessment: ${recommendation.classification}",
        style = MaterialTheme.typography.titleMedium,
    )
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Hide evidence and consequences" else "Show evidence and consequences")
    }
    if (!expanded) return
    for (type in listOf(OperationType.DISABLE, OperationType.UNINSTALL_FOR_USER)) {
        val operation = recommendation.operation(type)
        Text(
            "${type.label()}: ${operation.safety} · ${if (operation.recommended) "Recommended" else "Not recommended"}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(operation.reason, style = MaterialTheme.typography.bodySmall)
    }
    recommendation.rules.forEach { rule ->
        Text(
            "${rule.layer} · ${rule.revision} · ${rule.confidence} confidence",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(rule.explanation, style = MaterialTheme.typography.bodySmall)
        Text(
            "Function: ${rule.functionality}\nConsequences: ${rule.consequences}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Dependencies: ${rule.dependencies.joinToString().ifBlank { "Not verified; an empty list does not establish independence." }}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Applicable Android APIs ${rule.minSdk}–${if (rule.maxSdk == Int.MAX_VALUE) "current" else rule.maxSdk}; OEM versions: ${rule.oemVersions.joinToString().ifBlank { "general reference; no exact firmware verification" }}",
            style = MaterialTheme.typography.bodySmall,
        )
        rule.evidence.forEach {
            Text("${it.source}\n${it.limitation}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Last operation verification: ${rule.lastVerificationDate ?: "Not device verified"}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun DeviceRecommendationsScreen(
    vm: PackagesViewModel,
    navController: NavController,
    retry: () -> Unit,
) {
    val snapshot by vm.deviceCatalogue.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    var showDevice by rememberSaveable { mutableStateOf(false) }
    val summary = vm.selectionSummary(apps)
    Scaffold(
        topBar = { AetherTopBar("Device Recommendations", navController) },
        bottomBar = {
            Button(
                onClick = {
                    vm.preparePreview(state.operation)
                    navController.navigate(Routes.PREVIEW_DIALOG)
                },
                enabled = summary.selected > 0,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text("Preview (${summary.selected})")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    snapshot.profile?.let {
                        "${it.build.brand} ${it.build.model} · ${it.oemFamily.name.replace('_', ' ')}"
                    }
                        ?: if (snapshot.loading) "Detecting device…"
                        else "Device information unavailable",
                )
                Text(
                    "Profile: ${snapshot.profile?.catalogueProfile ?: "pending"} · Revision ${snapshot.revision}",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { showDevice = !showDevice }) {
                    Text(if (showDevice) "Hide device information" else "Device information")
                }
                if (showDevice) DeviceInformation(snapshot.profile)
                if (snapshot.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                snapshot.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(retry) { Text("Retry device catalogue") }
                }
                val matched = state.apps.count { it.packageName in snapshot.entries }
                val recommended =
                    state.apps.count {
                        snapshot.entries[it.packageName]?.operation(state.operation)?.recommended ==
                            true
                    }
                Text("Matched $matched / ${state.apps.size} installed · Recommended $recommended")
                Text(
                    RecommendationSafety.entries.joinToString(" · ") { safety ->
                        "${safety.name} ${state.apps.count { vm.classificationFor(it.packageName) == safety }}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Research is not device-tested removal evidence. Select packages explicitly and review both operations.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(OperationType.DISABLE, OperationType.UNINSTALL_FOR_USER).forEach { type
                        ->
                        FilterChip(
                            state.operation == type,
                            { vm.setOperation(type) },
                            { Text(type.label()) },
                        )
                    }
                }
                PackageFilters(vm)
                Row {
                    TextButton(vm::selectAllVisible, enabled = summary.eligibleVisible > 0) {
                        Text("Select all visible")
                    }
                    TextButton(
                        vm::deselectAllVisible,
                        enabled = summary.selected > summary.hiddenSelected,
                    ) {
                        Text("Deselect all visible")
                    }
                }
                Text(
                    "Selected ${summary.selected} · Eligible visible ${summary.eligibleVisible} · Protected ${summary.protectedVisible} · Hidden selected ${summary.hiddenSelected}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.isLoading) LoadingRow("Scanning installed apps…")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.apps.isEmpty()) TextButton(vm::loadApps) { Text("Scan installed apps") }
                else if (apps.isEmpty()) Text("No apps match the current search / filter.")
            }
            items(apps, key = { it.packageName }) { app ->
                Column {
                    AppRow(
                        app,
                        onClick = {},
                        ratingDot = { PackageSafetyLabel(vm, app.packageName) },
                        selection =
                            AppRowSelection(
                                app.packageName in state.selection,
                                { vm.setSelected(app.packageName, it) },
                                vm.protectionReason(app),
                            ),
                    )
                    TextButton(
                        onClick = { navController.navigate(Routes.appDetails(app.packageName)) },
                    ) {
                        Text("Why listed: ${app.label}")
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
