package id.mybase.aether.packages

import id.mybase.aether.adb.AdbShell
import id.mybase.aether.domain.OperationResult
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.PlannedOperation
import id.mybase.aether.persistence.OperationHistoryStore
import id.mybase.aether.security.InputValidators

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
) {
    suspend fun execute(
        plan: List<PlannedOperation>,
        onProgress: (done: Int, total: Int, current: PlannedOperation) -> Unit = { _, _, _ -> },
    ): List<OperationResult> {
        val results = ArrayList<OperationResult>(plan.size)
        plan.forEachIndexed { index, op ->
            val pkg = op.app.packageName
            val result =
                if (!InputValidators.isValidPackageName(pkg)) {
                    OperationResult(
                        operation = op,
                        status = OperationStatus.SKIPPED,
                        message = "Skipped: \"$pkg\" is not a valid Android package id",
                    )
                } else {
                    runOperation(op)
                }
            recordToHistory(op, result)
            results.add(result)
            onProgress(index + 1, plan.size, op)
        }
        return results
    }

    private suspend fun runOperation(op: PlannedOperation): OperationResult {
        val args: Array<String> =
            when (op.type) {
                OperationType.UNINSTALL_FOR_USER ->
                    arrayOf("pm", "uninstall", "--user", "0", op.app.packageName)
                OperationType.DISABLE ->
                    arrayOf("pm", "disable-user", "--user", "0", op.app.packageName)
                OperationType.ENABLE -> arrayOf("pm", "enable", op.app.packageName)
            }
        return try {
            // The adb workstream's contract: Result<String> carries pm stdout on
            // success; any failure (non-zero exit, transport error) arrives as
            // Result.failure with the detail in the exception message.
            shell
                .exec(*args)
                .fold(
                    onSuccess = { out ->
                        OperationResult(
                            op,
                            OperationStatus.SUCCESS,
                            out.trim().ifBlank { "OK" }.take(MAX_MESSAGE),
                        )
                    },
                    onFailure = { e ->
                        val detail = (e.message ?: e.javaClass.simpleName).take(MAX_MESSAGE)
                        OperationResult(op, OperationStatus.FAILED, "pm failed: $detail")
                    },
                )
        } catch (e: Exception) {
            OperationResult(
                op,
                OperationStatus.FAILED,
                "Shell error: ${(e.message ?: e.javaClass.simpleName).take(MAX_MESSAGE)}",
            )
        }
    }

    private suspend fun recordToHistory(op: PlannedOperation, result: OperationResult) {
        try {
            history.record(
                timestamp = System.currentTimeMillis(),
                packageName = op.app.packageName,
                label = op.app.label,
                operation = op.type.name,
                status = result.status.name,
                message = result.message,
            )
        } catch (e: Exception) {
            // History is bookkeeping; it must never break an operation plan.
        }
    }

    companion object {
        private const val MAX_MESSAGE = 1000
    }
}
