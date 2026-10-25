package id.mybase.aether.ui.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.domain.AppKind
import id.mybase.aether.domain.AppPackage
import id.mybase.aether.domain.OperationResult
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.PlannedOperation
import id.mybase.aether.domain.RiskLevel
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.domain.isReversible
import id.mybase.aether.domain.kind
import id.mybase.aether.domain.riskOf
import id.mybase.aether.packages.PackageOperationExecutor
import id.mybase.aether.packages.PackageRepository
import id.mybase.aether.packages.SafetyCatalogue
import id.mybase.aether.persistence.OperationHistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sort order for the app list. */
enum class AppSort {
    NAME,
    KIND,
}

/** Editable list state for the package browser. */
data class PackagesUiState(
    val apps: List<AppPackage> = emptyList(),
    val warnings: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val kindFilter: AppKind? = null,
    val sortBy: AppSort = AppSort.NAME,
    val selection: Set<String> = emptySet(),
)

/** One row of the pre-execution preview, with its assessed risk. */
data class PreviewItem(
    val app: AppPackage,
    val rating: SafetyRating,
    val risk: RiskLevel,
    val reversible: Boolean,
)

/** Live execution progress reported by [PackageOperationExecutor]. */
data class ExecutionProgress(val done: Int, val total: Int, val current: String)

/**
 * Browsing, filtering, selecting and operating on installed packages. All package data comes from
 * [PackageRepository]; nothing is fabricated.
 */
class PackagesViewModel(
    private val repo: PackageRepository,
    private val catalogue: SafetyCatalogue,
    private val executor: PackageOperationExecutor,
    private val shell: AdbShell,
    private val history: OperationHistoryStore,
    private val filterDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PackagesUiState())
    val uiState: StateFlow<PackagesUiState> = _uiState.asStateFlow()

    /** Apps after search / kind filter / sort are applied. */
    val visibleApps: StateFlow<List<AppPackage>> =
        uiState
            .map { applyView(it) }
            .flowOn(filterDispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isConnected: StateFlow<Boolean> =
        shell.state
            .map { it is AdbConnectionState.Connected }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Safety catalogue revision, for display in the app list header. */
    val catalogueRevision: String
        get() = catalogue.revision

    private val _execution = MutableStateFlow<ExecutionProgress?>(null)
    val execution: StateFlow<ExecutionProgress?> = _execution.asStateFlow()

    /** Labels reported as processed, in order — drives the live results list. */
    private val _completedLabels = MutableStateFlow<List<String>>(emptyList())
    val completedLabels: StateFlow<List<String>> = _completedLabels.asStateFlow()

    private val _lastResults = MutableStateFlow<List<OperationResult>?>(null)
    val lastResults: StateFlow<List<OperationResult>?> = _lastResults.asStateFlow()

    private val _executionError = MutableStateFlow<String?>(null)
    val executionError: StateFlow<String?> = _executionError.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _previewItems = MutableStateFlow<List<PreviewItem>>(emptyList())
    val previewItems: StateFlow<List<PreviewItem>> = _previewItems.asStateFlow()

    private val _previewType = MutableStateFlow<OperationType?>(null)
    val previewType: StateFlow<OperationType?> = _previewType.asStateFlow()
    private var executionJob: Job? = null

    fun loadApps() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val inventory = repo.getInventory()
                val apps = inventory.apps
                _uiState.update {
                    it.copy(
                        apps = apps,
                        warnings = inventory.warnings,
                        isLoading = false,
                        error = null,
                        selection =
                            it.selection.intersect(apps.map { app -> app.packageName }.toSet()),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load apps.")
                }
            }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setKindFilter(filter: AppKind?) = _uiState.update { it.copy(kindFilter = filter) }

    fun setSortBy(sort: AppSort) = _uiState.update { it.copy(sortBy = sort) }

    fun toggleSelect(packageName: String) {
        _uiState.update { s ->
            val sel = s.selection.toMutableSet()
            if (!sel.add(packageName)) sel.remove(packageName)
            s.copy(selection = sel)
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selection = emptySet()) }

    fun ratingFor(app: AppPackage): SafetyRating = catalogue.ratingFor(app.packageName)

    /** Builds preview items for the current multi-selection. Pure read of state. */
    fun preview(type: OperationType): List<PreviewItem> =
        _uiState.value.apps
            .filter { it.packageName in _uiState.value.selection }
            .map { toPreviewItem(it, type) }

    fun preparePreview(type: OperationType) {
        _previewType.value = type
        _previewItems.value = preview(type)
    }

    fun prepareSinglePreview(packageName: String, type: OperationType) {
        val app = _uiState.value.apps.find { it.packageName == packageName }
        _previewType.value = type
        _previewItems.value = if (app != null) listOf(toPreviewItem(app, type)) else emptyList()
    }

    fun clearPreview() {
        _previewItems.value = emptyList()
        _previewType.value = null
    }

    fun executeSelected(
        type: OperationType,
        onProgress: (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> },
    ) = executeItems(preview(type), type, onProgress)

    fun executePreview(
        onProgress: (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> },
    ) {
        val type = _previewType.value ?: return
        executeItems(_previewItems.value, type, onProgress)
    }

    fun clearExecution() {
        if (_isExecuting.value) return
        _execution.value = null
        _completedLabels.value = emptyList()
        _lastResults.value = null
        _executionError.value = null
    }

    private fun toPreviewItem(app: AppPackage, type: OperationType): PreviewItem {
        val rating = catalogue.ratingFor(app.packageName)
        return PreviewItem(
            app = app,
            rating = rating,
            risk = riskOf(type, rating, app.kind(), app.isSystem || app.isUpdatedSystemApp),
            reversible = type.isReversible(),
        )
    }

    private fun executeItems(
        items: List<PreviewItem>,
        type: OperationType,
        onProgress: (Int, Int, String) -> Unit,
    ) {
        if (items.isEmpty() || _isExecuting.value) return
        _isExecuting.value = true
        _executionError.value = null
        _lastResults.value = null
        _completedLabels.value = emptyList()
        _execution.value = ExecutionProgress(0, items.size, "")
        executionJob = viewModelScope.launch {
            try {
                val plan = items.map { PlannedOperation(it.app, type) }
                val results =
                    executor.execute(plan) { done, total, current ->
                        val label = current.app.label
                        _execution.value = ExecutionProgress(done, total, label)
                        if (label.isNotBlank()) _completedLabels.update { it + label }
                        onProgress(done, total, label)
                    }
                _lastResults.value = results
                loadApps()
            } catch (e: CancellationException) {
                _executionError.value =
                    "Cancelled. Completed operations remain applied; refresh inventory and review history."
                throw e
            } catch (e: Exception) {
                _executionError.value = e.message ?: "Execution failed."
            } finally {
                _isExecuting.value = false
                _execution.value = _execution.value?.copy(current = "")
                clearSelection()
            }
        }
    }

    fun cancelExecution() {
        executionJob?.cancel()
    }

    private fun applyView(s: PackagesUiState): List<AppPackage> {
        val q = s.query.trim().lowercase()
        val filtered =
            s.apps.filter { app ->
                (s.kindFilter == null || app.kind() == s.kindFilter) &&
                    (q.isEmpty() ||
                        app.label.lowercase().contains(q) ||
                        app.packageName.lowercase().contains(q))
            }
        return when (s.sortBy) {
            AppSort.NAME -> filtered.sortedBy { it.label.lowercase() }
            AppSort.KIND ->
                filtered.sortedWith(
                    compareBy({ it.kind().ordinal }, { it.label.lowercase() }),
                )
        }
    }
}
