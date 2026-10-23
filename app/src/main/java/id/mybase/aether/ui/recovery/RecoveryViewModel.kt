package id.mybase.aether.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.mybase.aether.domain.OperationRecord
import id.mybase.aether.domain.OperationResult
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.PlannedOperation
import id.mybase.aether.packages.PackageOperationExecutor
import id.mybase.aether.packages.PackageRepository
import id.mybase.aether.persistence.OperationHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Recovery: finds apps that AETHER disabled (per the operation history) and re-enables them. Only
 * apps still present on the device can be re-enabled; fully uninstalled apps need their original
 * APK.
 */
class RecoveryViewModel(
    private val repo: PackageRepository,
    private val executor: PackageOperationExecutor,
    private val history: OperationHistoryStore,
) : ViewModel() {

    private val _disabledByAether = MutableStateFlow<List<OperationRecord>>(emptyList())
    val disabledByAether: StateFlow<List<OperationRecord>> = _disabledByAether.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _result = MutableStateFlow<List<OperationResult>?>(null)
    val result: StateFlow<List<OperationResult>?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _disabledByAether.value =
                history.list().filter {
                    it.operation == OperationType.DISABLE.name &&
                        it.status == OperationStatus.SUCCESS.name
                }
        }
    }

    fun reenableAll(
        onProgress: (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> },
    ) {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _error.value = null
            _result.value = null
            try {
                val apps = repo.getInstalledPackages().associateBy { it.packageName }
                val plan =
                    _disabledByAether.value.mapNotNull { record ->
                        apps[record.packageName]?.let { PlannedOperation(it, OperationType.ENABLE) }
                    }
                _result.value =
                    executor.execute(plan) { done, total, current ->
                        onProgress(done, total, current.app.label)
                    }
            } catch (e: Exception) {
                _error.value = e.message ?: "Re-enable failed."
            } finally {
                _isWorking.value = false
                refresh()
            }
        }
    }

    fun clearResult() {
        _result.value = null
        _error.value = null
    }
}
