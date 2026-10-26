package id.mybase.aether.ui.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.mybase.aether.domain.AppKind
import id.mybase.aether.packages.RecommendationSafety
import id.mybase.aether.ui.packages.AppSort
import id.mybase.aether.ui.packages.PackagesViewModel

@Composable
fun SafetyFilters(vm: PackagesViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(state.safetyFilter == null, { vm.setSafetyFilter(null) }, { Text("All safety") })
        RecommendationSafety.entries.forEach { safety ->
            FilterChip(
                state.safetyFilter == safety,
                { vm.setSafetyFilter(safety) },
                { Text(safety.name) },
            )
        }
    }
}

@Composable
fun PackageFilters(vm: PackagesViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(
            if (expanded) "Hide search and filters"
            else
                "Search and filters${if (state.query.isNotBlank() || state.kindFilter != null || state.safetyFilter != null) " (active)" else ""}",
        )
    }
    if (expanded) {
        OutlinedTextField(
            state.query,
            vm::setQuery,
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(state.kindFilter == null, { vm.setKindFilter(null) }, { Text("All apps") })
            AppKind.entries.forEach { kind ->
                FilterChip(
                    state.kindFilter == kind,
                    { vm.setKindFilter(kind) },
                    { Text(kind.name.replace('_', ' ').lowercase()) },
                )
            }
        }
        SafetyFilters(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSort.entries.forEach { sort ->
                FilterChip(
                    state.sortBy == sort,
                    { vm.setSortBy(sort) },
                    { Text("Sort: ${sort.name.lowercase()}") },
                )
            }
        }
    }
}

@Composable
fun PackageSafetyLabel(vm: PackagesViewModel, packageName: String) {
    val snapshot by vm.deviceCatalogue.collectAsStateWithLifecycle()
    val safety = snapshot.entries[packageName]?.classification ?: vm.classificationFor(packageName)
    Text(
        safety.name,
        style = MaterialTheme.typography.labelSmall,
        color =
            if (safety >= RecommendationSafety.UNSAFE) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
