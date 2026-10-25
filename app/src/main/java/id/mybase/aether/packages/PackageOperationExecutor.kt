package id.mybase.aether.packages

import id.mybase.aether.adb.AdbShell
import id.mybase.aether.domain.OperationResult
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.PlannedOperation
import id.mybase.aether.persistence.OperationHistoryStore
import id.mybase.aether.security.InputValidators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Executes a plan of package operations over the ADB shell, strictly sequentially (conflicting pm
 * operations must never run concurrently).
 *
 * - Every package id is validated with [InputValidators.isValidPackageName] before anything is sent
 *   to the shell; invalid ids become SKIPPED results and never reach the shell.
 * - Each operation is wrapped in try/catch: a failure yields a FAILED result and the remaining plan
 *   continues (partial failure never aborts the plan).
 * - EVERY operation — success, failure, or skip — is recorded to [OperationHistoryStore]. A history
 *   failure is contained and never breaks the plan.
 * - [onProgress] fires after each operation with (done, total, current).
 */
class PackageOperationExecutor(
    private val shell: AdbShell,
    private val history: OperationHistoryStore,
    private val catalogue: SafetyCatalogue? = null,
    private val ownPackageName: String? = "id.mybase.aether",
    private val inventoryUserId: () -> Int = { 0 },
) {
    private val executionMutex = Mutex()

    suspend fun execute(
        plan: List<PlannedOperation>,
        onProgress: (done: Int, total: Int, current: PlannedOperation) -> Unit = { _, _, _ -> },
    ): List<OperationResult> = executionMutex.withLock {
        val results = ArrayList<OperationResult>(plan.size)
        plan.forEachIndexed { index, op ->
            currentCoroutineContext().ensureActive()
            val pkg = op.app.packageName
            var historyId: Long? = null
            val result =
                if (inventoryUserId() != 0) {
                    OperationResult(
                        op,
                        OperationStatus.SKIPPED,
                        "Operations support the device owner (user 0) only; this inventory belongs to another user",
                    )
                } else if (!InputValidators.isValidPackageName(pkg)) {
                    OperationResult(
                        operation = op,
                        status = OperationStatus.SKIPPED,
                        message = "Skipped: \"$pkg\" is not a valid Android package id",
                    )
                } else if (
                    op.type != OperationType.ENABLE &&
                        (pkg == ownPackageName ||
                            catalogue?.ratingFor(pkg) ==
                                id.mybase.aether.domain.SafetyRating.ESSENTIAL)
                ) {
                    OperationResult(
                        op,
                        OperationStatus.SKIPPED,
                        "Protected package: essential components and AETHER cannot be removed or disabled",
                    )
                } else {
                    try {
                        historyId =
                            history.record(
                                System.currentTimeMillis(),
                                pkg,
                                op.app.label,
                                op.type.name,
                                OperationStatus.IN_PROGRESS.name,
                                "Outcome unverified. If interrupted, refresh inventory before retrying; do not assume rollback.",
                            )
                        runOperation(op, checkNotNull(historyId))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        OperationResult(
                            op,
                            OperationStatus.FAILED,
                            "Could not create durable operation history; no package command was sent",
                        )
                    }
                }
            val historySaved =
                withContext(NonCancellable) {
                    withTimeoutOrNull(5_000) {
                        if (historyId == null) recordToHistory(op, result)
                        else completeHistory(checkNotNull(historyId), result)
                    } ?: false
                }
            results.add(
                if (historySaved) result
                else result.copy(message = result.message + "; History could not be saved"),
            )
            onProgress(index + 1, plan.size, op)
        }
        results
    }

    private suspend fun runOperation(op: PlannedOperation, historyId: Long): OperationResult {
        val args: Array<String> =
            when (op.type) {
                OperationType.UNINSTALL_FOR_USER ->
                    arrayOf("pm", "uninstall", "--user", "0", op.app.packageName)
                OperationType.DISABLE ->
                    arrayOf("pm", "disable-user", "--user", "0", op.app.packageName)
                OperationType.ENABLE -> arrayOf("pm", "enable", "--user", "0", op.app.packageName)
            }
        return try {
            require(shell.exec("id", "-u").getOrThrow().trim() == "2000") {
                "ADB shell privileges have not been verified"
            }
            val pkg = op.app.packageName
            val before = shell.exec("pm", "list", "packages", "--user", "0", pkg).getOrThrow()
            require(parsePackageList(before).contains(pkg)) {
                "Package is not installed for user 0"
            }
            val out = shell.exec(*args).getOrThrow().trim()
            val expected =
                when (op.type) {
                    OperationType.UNINSTALL_FOR_USER -> "Success"
                    OperationType.DISABLE -> "Package $pkg new state: disabled-user"
                    OperationType.ENABLE -> "Package $pkg new state: enabled"
                }
            if (out != expected) {
                return OperationResult(
                    op,
                    OperationStatus.FAILED,
                    "pm failed or returned an unexpected response: ${out.ifBlank { "empty output" }.take(MAX_MESSAGE)}",
                )
            }
            val verification =
                when (op.type) {
                    OperationType.UNINSTALL_FOR_USER ->
                        arrayOf("pm", "list", "packages", "--user", "0", pkg)
                    OperationType.DISABLE ->
                        arrayOf("pm", "list", "packages", "-d", "--user", "0", pkg)
                    OperationType.ENABLE ->
                        arrayOf("pm", "list", "packages", "-e", "--user", "0", pkg)
                }
            val packages = parsePackageList(shell.exec(*verification).getOrThrow())
            val verified =
                if (op.type == OperationType.UNINSTALL_FOR_USER) pkg !in packages
                else pkg in packages
            OperationResult(
                op,
                if (verified) OperationStatus.SUCCESS else OperationStatus.FAILED,
                if (verified) "Verified for user 0: $out"
                else "Command returned success but resulting package state was not verified",
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                withTimeoutOrNull(5_000) {
                    completeHistory(
                        historyId,
                        OperationResult(
                            op,
                            OperationStatus.IN_PROGRESS,
                            "Cancelled; package state may have changed. Refresh inventory before retrying.",
                        ),
                    )
                }
            }
            throw e
        } catch (e: Exception) {
            OperationResult(
                op,
                OperationStatus.FAILED,
                "pm failed: ${(e.message ?: e.javaClass.simpleName).take(MAX_MESSAGE)}",
            )
        }
    }

    private suspend fun recordToHistory(op: PlannedOperation, result: OperationResult): Boolean {
        try {
            history.record(
                timestamp = System.currentTimeMillis(),
                packageName = op.app.packageName,
                label = op.app.label,
                operation = op.type.name,
                status = result.status.name,
                message = result.message,
            )
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun completeHistory(id: Long, result: OperationResult): Boolean =
        try {
            history.complete(id, result.status.name, result.message)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    companion object {
        private const val MAX_MESSAGE = 1000

        internal fun parsePackageList(output: String): Set<String> =
            output
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { line ->
                    require(line.startsWith("package:")) { "Package state query failed" }
                    line.removePrefix("package:").also {
                        require(InputValidators.isValidPackageName(it)) {
                            "Invalid package state response"
                        }
                    }
                }
                .toSet()
    }
}
