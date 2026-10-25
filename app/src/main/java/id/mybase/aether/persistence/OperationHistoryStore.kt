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

    /** Finalizes a previously durable pending row; missing rows must raise an error. */
    suspend fun complete(id: Long, status: String, message: String)

    suspend fun list(limit: Int = 200): List<OperationRecord>

    suspend fun clear()
}
