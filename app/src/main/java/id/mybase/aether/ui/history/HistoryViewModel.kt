package id.mybase.aether.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.mybase.aether.domain.OperationRecord
import id.mybase.aether.persistence.OperationHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reads and clears the persisted operation history. */
class HistoryViewModel(
    private val history: OperationHistoryStore,
) : ViewModel() {

    private val _records = MutableStateFlow<List<OperationRecord>>(emptyList())
    val records: StateFlow<List<OperationRecord>> = _records.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _records.value = history.list()
        }
    }

    fun clear() {
        viewModelScope.launch {
            history.clear()
            _records.value = emptyList()
        }
    }
}
