package id.mybase.aether.ui.apps

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.mybase.aether.domain.AppKind
import id.mybase.aether.domain.AppPackage
import id.mybase.aether.domain.kind
import id.mybase.aether.packages.exportInventoryJson
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.EmptyState
import id.mybase.aether.ui.components.KindBadge
import id.mybase.aether.ui.components.LoadingRow
import id.mybase.aether.ui.components.SafetyDot
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.packages.AppSort
import id.mybase.aether.ui.packages.PackagesViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KIND_FILTERS: List<Pair<String, AppKind?>> =
    listOf(
        "All" to null,
        "User" to AppKind.USER,
        "System" to AppKind.SYSTEM,
        "Disabled" to AppKind.DISABLED,
        "Updated" to AppKind.UPDATED_SYSTEM,
    )

/**
 * Searchable, filterable list of installed packages from the real
 * [id.mybase.aether.packages.PackageRepository]. Tap a row for details.
 */
@Composable
fun AppListScreen(
    packagesVm: PackagesViewModel,
    navController: NavController,
) {
    val uiState by packagesVm.uiState.collectAsStateWithLifecycle()
    val apps by packagesVm.visibleApps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                val inventory = uiState.apps.map { it to packagesVm.ratingFor(it) }
                exporting = true
                scope.launch {
                    val message =
                        try {
                            withContext(Dispatchers.IO) {
                                val json = exportInventoryJson(inventory)
                                requireNotNull(
                                        context.contentResolver.openOutputStream(uri, "wt"),
                                    ) {
                                        "Document provider did not open the file"
                                    }
                                    .bufferedWriter(Charsets.UTF_8)
                                    .use { it.write(json) }
                            }
                            "Inventory JSON saved"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            "Export failed. The selected file may be incomplete; retry saving it."
                        } finally {
                            exporting = false
                        }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }

    Scaffold(
        topBar = {
            AetherTopBar(
                title = "Apps",
                navController = navController,
                actions = {
                    IconButton(onClick = packagesVm::loadApps, enabled = !uiState.isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh inventory")
                    }
                    IconButton(
                        onClick = {
                            exportLauncher.launch("aether-inventory.json")
                        },
                        enabled = uiState.apps.isNotEmpty() && !exporting,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Export inventory as JSON")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.query,
                onValueChange = packagesVm::setQuery,
                label = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FilterChipRow(
                filters = KIND_FILTERS,
                selected = uiState.kindFilter,
                onSelect = packagesVm::setKindFilter,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Sort:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterChip(
                    selected = uiState.sortBy == AppSort.NAME,
                    onClick = { packagesVm.setSortBy(AppSort.NAME) },
                    label = { Text("Name") },
                )
                FilterChip(
                    selected = uiState.sortBy == AppSort.KIND,
                    onClick = { packagesVm.setSortBy(AppSort.KIND) },
                    label = { Text("Kind") },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${apps.size} apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Safety catalogue rev ${packagesVm.catalogueRevision}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (uiState.warnings.isNotEmpty()) {
                Text(
                    "Partial inventory: ${uiState.warnings.size} packages could not be read. Refresh to retry.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when {
                uiState.isLoading -> LoadingRow("Scanning installed apps…")
                uiState.error != null ->
                    EmptyState(
                        message = "Scan failed: ${uiState.error}",
                        actionLabel = "Retry",
                        onAction = packagesVm::loadApps,
                    )
                uiState.apps.isEmpty() ->
                    EmptyState(
                        message = "No apps scanned yet.",
                        actionLabel = "Scan now",
                        onAction = packagesVm::loadApps,
                    )
                apps.isEmpty() -> EmptyState(message = "No apps match the current search / filter.")
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                ratingDot = {
                                    SafetyDot(packagesVm.ratingFor(app))
                                },
                                onClick = {
                                    navController.navigate(Routes.appDetails(app.packageName))
                                },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    filters: List<Pair<String, AppKind?>>,
    selected: AppKind?,
    onSelect: (AppKind?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        filters.forEach { (label, kind) ->
            FilterChip(
                selected = selected == kind,
                onClick = { onSelect(kind) },
                label = { Text(label) },
            )
        }
    }
}

/**
 * One app row: label, package id, kind badge and safety dot. An optional [trailing] slot (e.g. a
 * checkbox) is used by the debloat multi-select list.
 */
@Composable
fun AppRow(
    app: AppPackage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ratingDot: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        id.mybase.aether.ui.components.PackageIcon(app.packageName, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label.ifBlank { app.packageName }, style = MaterialTheme.typography.bodyLarge)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                KindBadge(app.kind())
                ratingDot()
            }
        }
        trailing()
    }
}
