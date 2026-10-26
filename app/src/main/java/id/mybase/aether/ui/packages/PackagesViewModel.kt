package id.mybase.aether.ui.packages

import androidx.lifecycle.SavedStateHandle
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
import id.mybase.aether.packages.DeviceAwareSafetyCatalogue
import id.mybase.aether.packages.DeviceCatalogueState
import id.mybase.aether.packages.PackageOperationExecutor
import id.mybase.aether.packages.PackageRepository
import id.mybase.aether.packages.RecommendationSafety
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
import kotlinx.coroutines.flow.combine
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
    val operation: OperationType = OperationType.DISABLE,
    val safetyFilter: RecommendationSafety? = null,
)

data class SelectionSummary(
    val selected: Int,
    val eligibleVisible: Int,
    val protectedVisible: Int,
    val hiddenSelected: Int,
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
    private val savedState: SavedStateHandle? = null,
    val deviceCatalogue: StateFlow<DeviceCatalogueState> = MutableStateFlow(DeviceCatalogueState()),
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            PackagesUiState(
                selection = savedState?.get<List<String>>("selectedPackages")?.toSet().orEmpty(),
                operation =
                    OperationType.entries.firstOrNull {
                        it.name == savedState?.get<String>("operation") &&
                            it != OperationType.ENABLE
                    } ?: OperationType.DISABLE,
            ),
        )
    val uiState: StateFlow<PackagesUiState> = _uiState.asStateFlow()

    /** Apps after search / kind filter / sort are applied. */
    val visibleApps: StateFlow<List<AppPackage>> =
        combine(uiState, deviceCatalogue) { state, _ -> applyView(state) }
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

    init {
        viewModelScope.launch {
            deviceCatalogue.collect {
                _uiState.update { state ->
                    state.copy(
                        selection =
                            state.selection -
                                state.apps
                                    .filter { protectionReason(it) != null }
                                    .map { it.packageName }
                                    .toSet(),
                    )
                }
                saveSelection()
                val type = _previewType.value
                if (type != null && !_isExecuting.value)
                    _previewItems.value = _previewItems.value.map { toPreviewItem(it.app, type) }
            }
        }
    }

    fun setSafetyFilter(filter: RecommendationSafety?) = _uiState.update {
        it.copy(safetyFilter = filter)
    }

    fun recommendationFor(packageName: String) = deviceCatalogue.value.entries[packageName]

    fun classificationFor(packageName: String): RecommendationSafety =
        recommendationFor(packageName)?.classification
            ?: when (catalogue.ratingFor(packageName)) {
                SafetyRating.ESSENTIAL -> RecommendationSafety.CRITICAL
                SafetyRating.RISKY -> RecommendationSafety.CAUTION
                else -> RecommendationSafety.UNKNOWN
            }

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
                            it.selection.intersect(
                                apps
                                    .filter { app -> protectionReason(app) == null }
                                    .map { app -> app.packageName }
                                    .toSet(),
                            ),
                    )
                }
                saveSelection()
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
        setSelected(packageName, packageName !in _uiState.value.selection)
    }

    fun setSelected(packageName: String, checked: Boolean) {
        _uiState.update { state ->
            val app = state.apps.find { it.packageName == packageName }
            val next =
                if (checked && app != null && protectionReason(app) == null) {
                    state.selection + packageName
                } else {
                    state.selection - packageName
                }
            state.copy(selection = next)
        }
        saveSelection()
    }

    fun selectAllVisible() {
        _uiState.update { state ->
            state.copy(
                selection =
                    state.selection +
                        applyView(state)
                            .filter { protectionReason(it) == null }
                            .map { it.packageName },
            )
        }
        saveSelection()
    }

    fun deselectAllVisible() {
        _uiState.update { state ->
            state.copy(
                selection = state.selection - applyView(state).map { it.packageName }.toSet(),
            )
        }
        saveSelection()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = emptySet()) }
        saveSelection()
    }

    fun setOperation(type: OperationType) {
        if (type == OperationType.ENABLE) return
        _uiState.update { it.copy(operation = type) }
        savedState?.set("operation", type.name)
    }

    fun protectionReason(app: AppPackage): String? =
        when {
            app.packageName in setOf("id.mybase.aether", "id.mybase.aether.debug") ->
                "Protected: AETHER manages this session."
            catalogue.ratingFor(app.packageName) == SafetyRating.ESSENTIAL ->
                "Protected: essential system functionality."
            else -> null
        }

    fun selectionSummary(visible: List<AppPackage>): SelectionSummary {
        val state = _uiState.value
        val selected =
            state.apps
                .filter {
                    it.packageName in state.selection && protectionReason(it) == null
                }
                .map { it.packageName }
                .toSet()
        val visibleIds = visible.map { it.packageName }.toSet()
        val protected = visible.count { protectionReason(it) != null }
        return SelectionSummary(
            selected.size,
            visible.size - protected,
            protected,
            (selected - visibleIds).size,
        )
    }

    private fun saveSelection() {
        savedState?.set("selectedPackages", _uiState.value.selection.toList())
    }

    fun ratingFor(app: AppPackage): SafetyRating = catalogue.ratingFor(app.packageName)

    /** Builds preview items for the current multi-selection. Pure read of state. */
    fun preview(type: OperationType): List<PreviewItem> =
        _uiState.value.apps
            .filter { it.packageName in _uiState.value.selection && protectionReason(it) == null }
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
        val rating =
            (catalogue as? DeviceAwareSafetyCatalogue)?.ratingForOperation(app.packageName, type)
                ?: catalogue.ratingFor(app.packageName)
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
                    (s.safetyFilter == null ||
                        classificationFor(app.packageName) == s.safetyFilter) &&
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
