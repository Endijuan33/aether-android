package id.mybase.aether.persistence

import id.mybase.aether.domain.OperationRecord

/** Append-mostly store for executed operation history. */
interface OperationHistoryStore {
    suspend fun record(
        timestamp: Long,
        packageName: String,
        label: String,
        operation: String,
        status: String,
        message: String,
    ): Long

    suspend fun list(limit: Int = 200): List<OperationRecord>

    suspend fun clear()
}
