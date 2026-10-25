package id.mybase.aether.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import id.mybase.aether.domain.OperationRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLite-backed [OperationHistoryStore]. Table op_history(id INTEGER PK AUTOINCREMENT, ts INTEGER,
 * package TEXT, label TEXT, operation TEXT, status TEXT, message TEXT).
 */
class SqliteOperationHistoryStore(context: Context, databaseName: String = DB_NAME) :
    OperationHistoryStore {

    private val helper = HistoryDb(context.applicationContext, databaseName)

    override suspend fun record(
        timestamp: Long,
        packageName: String,
        label: String,
        operation: String,
        status: String,
        message: String,
    ): Long =
        withContext(Dispatchers.IO) {
            val values =
                ContentValues().apply {
                    put(COL_TS, timestamp)
                    put(COL_PACKAGE, packageName)
                    put(COL_LABEL, label)
                    put(COL_OPERATION, operation)
                    put(COL_STATUS, status)
                    put(COL_MESSAGE, message)
                }
            helper.writableDatabase.insertOrThrow(TABLE, null, values)
        }

    override suspend fun list(limit: Int): List<OperationRecord> =
        withContext(Dispatchers.IO) {
            val safeLimit = limit.coerceAtLeast(0)
            val out = ArrayList<OperationRecord>()
            helper.readableDatabase
                .query(
                    TABLE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "$COL_ID DESC",
                    safeLimit.toString(),
                )
                .use { c ->
                    val iId = c.getColumnIndexOrThrow(COL_ID)
                    val iTs = c.getColumnIndexOrThrow(COL_TS)
                    val iPkg = c.getColumnIndexOrThrow(COL_PACKAGE)
                    val iLabel = c.getColumnIndexOrThrow(COL_LABEL)
                    val iOp = c.getColumnIndexOrThrow(COL_OPERATION)
                    val iStatus = c.getColumnIndexOrThrow(COL_STATUS)
                    val iMsg = c.getColumnIndexOrThrow(COL_MESSAGE)
                    while (c.moveToNext()) {
                        out.add(
                            OperationRecord(
                                id = c.getLong(iId),
                                timestamp = c.getLong(iTs),
                                packageName = c.getString(iPkg),
                                label = c.getString(iLabel),
                                operation = c.getString(iOp),
                                status = c.getString(iStatus),
                                message = c.getString(iMsg),
                            ),
                        )
                    }
                }
            out
        }

    override suspend fun clear(): Unit =
        withContext(Dispatchers.IO) {
            // Do not erase the durable marker of an active or interrupted mutation.
            helper.writableDatabase.delete(TABLE, "$COL_STATUS != ?", arrayOf("IN_PROGRESS"))
        }

    override suspend fun complete(id: Long, status: String, message: String): Unit =
        withContext(Dispatchers.IO) {
            val values =
                ContentValues().apply {
                    put(COL_STATUS, status)
                    put(COL_MESSAGE, message)
                }
            check(
                helper.writableDatabase.update(
                    TABLE,
                    values,
                    "$COL_ID = ?",
                    arrayOf(id.toString()),
                ) == 1,
            ) {
                "Operation history row is missing"
            }
        }

    private class HistoryDb(context: Context, databaseName: String) :
        SQLiteOpenHelper(context, databaseName, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                    "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COL_TS INTEGER NOT NULL, " +
                    "$COL_PACKAGE TEXT NOT NULL, " +
                    "$COL_LABEL TEXT NOT NULL, " +
                    "$COL_OPERATION TEXT NOT NULL, " +
                    "$COL_STATUS TEXT NOT NULL, " +
                    "$COL_MESSAGE TEXT NOT NULL)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // v1: nothing to migrate yet.
        }
    }

    companion object {
        private const val DB_NAME = "aether_history.db"
        private const val DB_VERSION = 1
        private const val TABLE = "op_history"
        private const val COL_ID = "id"
        private const val COL_TS = "ts"
        private const val COL_PACKAGE = "package"
        private const val COL_LABEL = "label"
        private const val COL_OPERATION = "operation"
        private const val COL_STATUS = "status"
        private const val COL_MESSAGE = "message"
    }
}
