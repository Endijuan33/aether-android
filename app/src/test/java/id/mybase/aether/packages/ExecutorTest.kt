package id.mybase.aether.packages

import app.cash.turbine.test
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.domain.AppPackage
import id.mybase.aether.domain.CatalogueEntry
import id.mybase.aether.domain.OperationResult
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.PlannedOperation
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.persistence.OperationHistoryStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExecutorTest {

    private class FakeAdbShell : AdbShell {
        private val _state =
            MutableStateFlow<AdbConnectionState>(AdbConnectionState.Connected("192.168.1.5", 5555))
        override val state: StateFlow<AdbConnectionState> = _state
        val calls = mutableListOf<List<String>>()
        val removed = mutableSetOf<String>()
        val disabled = mutableSetOf<String>()
        var applyMutation = true
        var handler: suspend (List<String>) -> Result<String> = { args ->
            Result.success(
                when (args[1]) {
                    "uninstall" -> "Success"
                    "disable-user" -> "Package ${args.last()} new state: disabled-user"
                    else -> "Package ${args.last()} new state: enabled"
                },
            )
        }

        override suspend fun connect(host: String, port: Int, timeoutMs: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun disconnect() {}

        override suspend fun exec(vararg args: String, timeoutMs: Long): Result<String> {
            val a = args.toList()
            if (a == listOf("id", "-u")) return Result.success("2000\n")
            if (a.take(3) == listOf("pm", "list", "packages")) {
                val pkg = a.last()
                val matches =
                    pkg !in removed &&
                        ("-d" !in a || pkg in disabled) &&
                        ("-e" !in a || pkg !in disabled)
                return Result.success(if (matches) "package:$pkg\n" else "")
            }
            calls.add(a)
            return handler(a).also { result ->
                if (result.isSuccess && applyMutation) {
                    when (a[1]) {
                        "uninstall" -> if (result.getOrNull() == "Success") removed.add(a.last())
                        "disable-user" -> disabled.add(a.last())
                        "enable" -> disabled.remove(a.last())
                    }
                }
            }
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

        override suspend fun complete(id: Long, status: String, message: String) {
            if (failOnRecord) throw RuntimeException("history down")
            val index = id.toInt() - 1
            rows[index] = rows[index].copy(status = status, message = message)
        }

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
    fun `essential and own packages never reach destructive shell command`() = runTest {
        val shell = FakeAdbShell()
        val catalogue =
            MapSafetyCatalogue(
                "test",
                mapOf(
                    "com.example.core" to
                        CatalogueEntry(
                            "com.example.core",
                            SafetyRating.ESSENTIAL,
                            "Critical component",
                            "test fixture",
                            "test",
                            "2026-10-25",
                        ),
                ),
            )
        val executor = PackageOperationExecutor(shell, FakeHistoryStore(), catalogue = catalogue)
        for (pkg in listOf("com.example.core", "id.mybase.aether")) {
            for (type in listOf(OperationType.DISABLE, OperationType.UNINSTALL_FOR_USER)) {
                assertEquals(
                    OperationStatus.SKIPPED,
                    executor.execute(listOf(PlannedOperation(app(pkg), type))).single().status,
                )
            }
        }
        assertTrue(shell.calls.isEmpty())
        assertEquals(
            OperationStatus.SUCCESS,
            executor
                .execute(listOf(PlannedOperation(app("com.example.core"), OperationType.ENABLE)))
                .single()
                .status,
        )
    }

    @Test
    fun `concurrent plans serialize complete plans and create history before mutation`() = runTest {
        val shell = FakeAdbShell()
        val history = FakeHistoryStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        shell.handler = { args ->
            assertEquals(OperationStatus.IN_PROGRESS.name, history.rows.last().status)
            if (args.last() == "com.example.first") {
                entered.complete(Unit)
                release.await()
            }
            Result.success("Package ${args.last()} new state: disabled-user")
        }
        val executor = PackageOperationExecutor(shell, history)
        val first = async {
            executor.execute(
                listOf("com.example.first", "com.example.second").map {
                    PlannedOperation(app(it), OperationType.DISABLE)
                },
            )
        }
        entered.await()
        val other = async {
            executor.execute(
                listOf(PlannedOperation(app("com.example.third"), OperationType.DISABLE)),
            )
        }
        runCurrent()
        assertEquals(1, shell.calls.size)
        release.complete(Unit)
        assertTrue((first.await() + other.await()).all { it.status == OperationStatus.SUCCESS })
        assertEquals(
            listOf("com.example.first", "com.example.second", "com.example.third"),
            shell.calls.map { it.last() },
        )
    }

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
    fun `enable explicitly targets user zero`() = runTest {
        val shell = FakeAdbShell()
        val executor = PackageOperationExecutor(shell, FakeHistoryStore())
        val results =
            executor.execute(
                listOf(PlannedOperation(app("com.a.one"), OperationType.ENABLE)),
            )
        assertEquals(OperationStatus.SUCCESS, results.single().status)
        assertEquals(listOf(listOf("pm", "enable", "--user", "0", "com.a.one")), shell.calls)
    }

    // -- failures --------------------------------------------------------------------

    @Test
    fun `pm failure yields FAILED and plan continues`() = runTest {
        val shell = FakeAdbShell()
        shell.handler = { args ->
            if (args.last() == "com.bad.one")
                Result.failure(RuntimeException("Failure [DELETE_FAILED_INTERNAL]"))
            else Result.success("Package ${args.last()} new state: disabled-user")
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
    fun `failure stdout is never reported as success`() = runTest {
        val shell =
            FakeAdbShell().apply {
                handler = { Result.success("Failure [DELETE_FAILED_INTERNAL]") }
            }
        val results =
            PackageOperationExecutor(shell, FakeHistoryStore())
                .execute(
                    listOf(PlannedOperation(app("com.a.one"), OperationType.UNINSTALL_FOR_USER)),
                )
        assertEquals(OperationStatus.FAILED, results.single().status)
    }

    @Test
    fun `successful command without changed package state fails verification`() = runTest {
        val shell = FakeAdbShell().apply { applyMutation = false }
        val results =
            PackageOperationExecutor(shell, FakeHistoryStore())
                .execute(
                    listOf(PlannedOperation(app("com.a.one"), OperationType.DISABLE)),
                )
        assertEquals(OperationStatus.FAILED, results.single().status)
        assertTrue(results.single().message.contains("not verified"))
    }

    @Test
    fun `secondary user inventory never reaches shell`() = runTest {
        val shell = FakeAdbShell()
        val results =
            PackageOperationExecutor(shell, FakeHistoryStore()) { 10 }
                .execute(
                    listOf(PlannedOperation(app("com.a.one"), OperationType.DISABLE)),
                )
        assertEquals(OperationStatus.SKIPPED, results.single().status)
        assertTrue(shell.calls.isEmpty())
    }

    @Test
    fun `cancellation stops the plan and records uncertain outcome`() = runTest {
        val shell =
            FakeAdbShell().apply {
                handler = { throw kotlinx.coroutines.CancellationException("cancelled") }
            }
        val history = FakeHistoryStore()
        try {
            PackageOperationExecutor(shell, history)
                .execute(
                    listOf(
                        PlannedOperation(app("com.a.one"), OperationType.DISABLE),
                        PlannedOperation(app("com.a.two"), OperationType.DISABLE),
                    ),
                )
            throw AssertionError("Cancellation must propagate")
        } catch (expected: kotlinx.coroutines.CancellationException) {
            assertEquals(1, shell.calls.size)
            assertEquals(OperationStatus.IN_PROGRESS.name, history.rows.single().status)
            assertTrue(history.rows.single().message.contains("may have changed"))
        }
    }

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
        assertTrue(results.all { it.status == OperationStatus.FAILED })
        assertTrue(shell.calls.isEmpty())
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
