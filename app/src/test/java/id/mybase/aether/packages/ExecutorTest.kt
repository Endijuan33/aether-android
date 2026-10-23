package id.mybase.aether.packages

import app.cash.turbine.test
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.domain.AppPackage
import id.mybase.aether.domain.OperationResult
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.PlannedOperation
import id.mybase.aether.persistence.OperationHistoryStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutorTest {

    private class FakeAdbShell : AdbShell {
        private val _state =
            MutableStateFlow<AdbConnectionState>(AdbConnectionState.Connected("192.168.1.5", 5555))
        override val state: StateFlow<AdbConnectionState> = _state
        val calls = mutableListOf<List<String>>()
        var handler: (List<String>) -> Result<String> = { Result.success("Success") }

        override suspend fun connect(host: String, port: Int, timeoutMs: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun disconnect() {}

        override suspend fun exec(vararg args: String, timeoutMs: Long): Result<String> {
            val a = args.toList()
            calls.add(a)
            return handler(a)
        }
    }

    private class FakeHistoryStore(
        private val failOnRecord: Boolean = false,
    ) : OperationHistoryStore {
        data class Row(
            val timestamp: Long,
            val packageName: String,
            val label: String,
            val operation: String,
            val status: String,
            val message: String,
        )

        val rows = mutableListOf<Row>()

        override suspend fun record(
            timestamp: Long,
            packageName: String,
            label: String,
            operation: String,
            status: String,
            message: String,
        ): Long {
            if (failOnRecord) throw RuntimeException("history down")
            rows.add(Row(timestamp, packageName, label, operation, status, message))
            return rows.size.toLong()
        }

        override suspend fun list(limit: Int) = emptyList<id.mybase.aether.domain.OperationRecord>()

        override suspend fun clear() {
            rows.clear()
        }
    }

    private fun app(pkg: String, label: String = pkg) =
        AppPackage(
            packageName = pkg,
            label = label,
            versionName = "1.0",
            versionCode = 1L,
            isSystem = false,
            isEnabled = true,
            isUpdatedSystemApp = false,
            installerPackageName = null,
            targetSdk = 34,
            permissions = emptyList(),
            firstInstallTime = 0L,
            lastUpdateTime = 0L,
        )

    // -- success path --------------------------------------------------------------

    @Test
    fun `success path sends structured args and reports progress`() = runTest {
        val shell = FakeAdbShell()
        val history = FakeHistoryStore()
        val executor = PackageOperationExecutor(shell, history)

        val op1 = PlannedOperation(app("com.a.one"), OperationType.UNINSTALL_FOR_USER)
        val op2 = PlannedOperation(app("com.a.two"), OperationType.DISABLE)
        val progress = mutableListOf<Triple<Int, Int, PlannedOperation>>()

        val results: List<OperationResult> =
            executor.execute(listOf(op1, op2)) { done, total, current ->
                progress.add(Triple(done, total, current))
            }

        assertEquals(2, results.size)
        assertTrue(results.all { it.status == OperationStatus.SUCCESS })
        assertEquals(
            listOf(
                listOf("pm", "uninstall", "--user", "0", "com.a.one"),
                listOf("pm", "disable-user", "--user", "0", "com.a.two"),
            ),
            shell.calls,
        )
        assertEquals(
            listOf(Triple(1, 2, op1), Triple(2, 2, op2)),
            progress,
        )
        // History recorded for every op with a real timestamp.
        assertEquals(2, history.rows.size)
        assertEquals("com.a.one", history.rows[0].packageName)
        assertEquals("UNINSTALL_FOR_USER", history.rows[0].operation)
        assertEquals("SUCCESS", history.rows[0].status)
        assertTrue(history.rows[0].timestamp > 0)
    }

    @Test
    fun `enable maps to pm enable without user flag`() = runTest {
        val shell = FakeAdbShell()
        val executor = PackageOperationExecutor(shell, FakeHistoryStore())
        val results =
            executor.execute(
                listOf(PlannedOperation(app("com.a.one"), OperationType.ENABLE)),
            )
        assertEquals(OperationStatus.SUCCESS, results.single().status)
        assertEquals(listOf(listOf("pm", "enable", "com.a.one")), shell.calls)
    }

    // -- failures --------------------------------------------------------------------

    @Test
    fun `pm failure yields FAILED and plan continues`() = runTest {
        val shell = FakeAdbShell()
        shell.handler = { args ->
            if (args.last() == "com.bad.one")
                Result.failure(RuntimeException("Failure [DELETE_FAILED_INTERNAL]"))
            else Result.success("Success")
        }
        val history = FakeHistoryStore()
        val executor = PackageOperationExecutor(shell, history)

        val results =
            executor.execute(
                listOf(
                    PlannedOperation(app("com.bad.one"), OperationType.UNINSTALL_FOR_USER),
                    PlannedOperation(app("com.good.two"), OperationType.DISABLE),
                ),
            )

        assertEquals(2, results.size)
        assertEquals(OperationStatus.FAILED, results[0].status)
        assertTrue(results[0].message.contains("DELETE_FAILED_INTERNAL"))
        assertTrue(results[0].message.startsWith("pm failed"))
        // Partial failure must not abort the remaining plan.
        assertEquals(OperationStatus.SUCCESS, results[1].status)
        assertEquals(2, shell.calls.size)
        assertEquals(2, history.rows.size)
        assertEquals("FAILED", history.rows[0].status)
        assertEquals("SUCCESS", history.rows[1].status)
    }

    @Test
    fun `shell exception yields FAILED with message`() = runTest {
        val shell = FakeAdbShell()
        shell.handler = { throw RuntimeException("connection reset") }
        val executor = PackageOperationExecutor(shell, FakeHistoryStore())
        val results =
            executor.execute(
                listOf(PlannedOperation(app("com.a.one"), OperationType.DISABLE)),
            )
        assertEquals(OperationStatus.FAILED, results.single().status)
        assertTrue(results.single().message.contains("connection reset"))
    }

    // -- validation --------------------------------------------------------------------

    @Test
    fun `invalid package id is SKIPPED without touching the shell`() = runTest {
        val shell = FakeAdbShell()
        val history = FakeHistoryStore()
        val executor = PackageOperationExecutor(shell, history)

        val evil = PlannedOperation(app("com.evil;rm -rf"), OperationType.UNINSTALL_FOR_USER)
        val good = PlannedOperation(app("com.good.one"), OperationType.DISABLE)
        val results = executor.execute(listOf(evil, good))

        assertEquals(OperationStatus.SKIPPED, results[0].status)
        assertTrue(results[0].message.contains("not a valid Android package id"))
        assertEquals(OperationStatus.SUCCESS, results[1].status)
        // The shell must never see the malicious id.
        assertEquals(1, shell.calls.size)
        assertEquals(listOf("pm", "disable-user", "--user", "0", "com.good.one"), shell.calls[0])
        // ...but the skip is still recorded in history.
        assertEquals(2, history.rows.size)
        assertEquals("SKIPPED", history.rows[0].status)
        assertEquals("com.evil;rm -rf", history.rows[0].packageName)
    }

    @Test
    fun `history failure does not break the plan`() = runTest {
        val shell = FakeAdbShell()
        val executor = PackageOperationExecutor(shell, FakeHistoryStore(failOnRecord = true))
        val results =
            executor.execute(
                listOf(
                    PlannedOperation(app("com.a.one"), OperationType.DISABLE),
                    PlannedOperation(app("com.a.two"), OperationType.ENABLE),
                ),
            )
        assertEquals(2, results.size)
        assertTrue(results.all { it.status == OperationStatus.SUCCESS })
        assertEquals(2, shell.calls.size)
    }

    @Test
    fun `empty plan returns empty and never calls shell`() = runTest {
        val shell = FakeAdbShell()
        var progressCalls = 0
        val results =
            PackageOperationExecutor(shell, FakeHistoryStore()).execute(emptyList()) { _, _, _ ->
                progressCalls++
            }
        assertTrue(results.isEmpty())
        assertTrue(shell.calls.isEmpty())
        assertEquals(0, progressCalls)
    }

    // -- shell state contract (Turbine) ---------------------------------------------------

    @Test
    fun `shell exposes connection state flow`() = runTest {
        val shell = FakeAdbShell()
        shell.state.test {
            assertEquals(AdbConnectionState.Connected("192.168.1.5", 5555), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -- progress as a flow (Turbine) --------------------------------------------------

    @Test
    fun `progress callbacks arrive in order for every op`() = runTest {
        val shell = FakeAdbShell()
        val executor = PackageOperationExecutor(shell, FakeHistoryStore())
        val plan =
            listOf(
                PlannedOperation(app("com.a.one"), OperationType.UNINSTALL_FOR_USER),
                PlannedOperation(app("com.a.two"), OperationType.DISABLE),
                PlannedOperation(app("com.a.three"), OperationType.ENABLE),
            )
        // Bridge the callback into a hot flow to verify ordering/totals with Turbine.
        val progress = MutableSharedFlow<Triple<Int, Int, String>>(extraBufferCapacity = 8)
        backgroundScope.launch {
            executor.execute(plan) { done, total, current ->
                check(progress.tryEmit(Triple(done, total, current.app.packageName)))
            }
        }
        progress.test {
            assertEquals(Triple(1, 3, "com.a.one"), awaitItem())
            assertEquals(Triple(2, 3, "com.a.two"), awaitItem())
            assertEquals(Triple(3, 3, "com.a.three"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
