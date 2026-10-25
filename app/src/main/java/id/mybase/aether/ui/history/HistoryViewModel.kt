package id.mybase.aether.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.mybase.aether.domain.OperationRecord
import id.mybase.aether.persistence.OperationHistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads and clears the persisted operation history. */
class HistoryViewModel(
    private val history: OperationHistoryStore,
) : ViewModel() {

    private val _records = MutableStateFlow<List<OperationRecord>>(emptyList())
    val records: StateFlow<List<OperationRecord>> = _records.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val mutex = Mutex()

    fun refresh() {
        update { _records.value = history.list() }
    }

    fun clear() {
        update {
            history.clear()
            _records.value = history.list()
        }
    }

    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                _isLoading.value = true
                _error.value = null
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _error.value = "History storage failed. Retry after checking available storage."
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
}
