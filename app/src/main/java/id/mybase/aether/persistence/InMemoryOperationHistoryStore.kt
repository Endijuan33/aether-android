package id.mybase.aether.persistence

import id.mybase.aether.domain.OperationRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [OperationHistoryStore] for JVM unit tests. Ids are assigned sequentially starting at
 * 1; [list] returns newest first.
 */
class InMemoryOperationHistoryStore : OperationHistoryStore {
    private val mutex = Mutex()
    private val rows = ArrayList<StoredRow>()
    private var nextId = 1L

    private data class StoredRow(
        val id: Long,
        val timestamp: Long,
        val packageName: String,
        val label: String,
        val operation: String,
        val status: String,
        val message: String,
    )

    override suspend fun record(
        timestamp: Long,
        packageName: String,
        label: String,
        operation: String,
        status: String,
        message: String,
    ): Long = mutex.withLock {
        val id = nextId++
        rows.add(StoredRow(id, timestamp, packageName, label, operation, status, message))
        id
    }

    override suspend fun list(limit: Int): List<OperationRecord> = mutex.withLock {
        rows.asReversed().take(limit.coerceAtLeast(0)).map { r ->
            OperationRecord(
                id = r.id,
                timestamp = r.timestamp,
                packageName = r.packageName,
                label = r.label,
                operation = r.operation,
                status = r.status,
                message = r.message,
            )
        }
    }

    override suspend fun complete(id: Long, status: String, message: String): Unit =
        mutex.withLock {
            val index = rows.indexOfFirst { it.id == id }
            check(index >= 0) { "Operation history row is missing" }
            rows[index] = rows[index].copy(status = status, message = message)
        }

    override suspend fun clear(): Unit = mutex.withLock {
        rows.removeAll { it.status != "IN_PROGRESS" }
    }
}
