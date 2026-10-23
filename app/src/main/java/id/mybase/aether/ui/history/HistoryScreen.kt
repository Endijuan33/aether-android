package id.mybase.aether.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.label
import id.mybase.aether.ui.components.AetherTopBar
import id.mybase.aether.ui.components.ConfirmDialog
import id.mybase.aether.ui.components.EmptyState
import id.mybase.aether.ui.components.StatusBadge
import id.mybase.aether.ui.components.formatEpochMillis

/**
 * Persisted operation history: timestamp, package, operation, status and message per record, with a
 * confirmed clear action.
 */
@Composable
fun HistoryScreen(
    historyVm: HistoryViewModel,
    navController: NavController,
) {
    val records by historyVm.records.collectAsState()
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { historyVm.refresh() }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear history?",
            message =
                "This permanently deletes all recorded operations. It does not undo the operations themselves.",
            confirmLabel = "Clear",
            onConfirm = {
                historyVm.clear()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
            destructive = true,
        )
    }

    Scaffold(
        topBar = {
            AetherTopBar(
                title = "History",
                navController = navController,
                actions = {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = records.isNotEmpty(),
                        contentDescription = "Clear history",
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState(
                message = "No operations recorded yet.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    record.label.ifBlank { record.packageName },
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusBadge(record.status)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                record.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${operationLabel(record.operation)} · ${formatEpochMillis(record.timestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (record.message.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(record.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** History stores the operation as its enum name; map it back for display. */
private fun operationLabel(stored: String): String =
    OperationType.entries.firstOrNull { it.name == stored }?.label() ?: stored
