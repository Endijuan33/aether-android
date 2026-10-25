package id.mybase.aether.packages

import android.content.Context
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.persistence.OperationHistoryStore
import id.mybase.aether.persistence.SqliteOperationHistoryStore

/** Composition root for the packages workstream. */
object PackagesModule {
    fun createRepository(context: Context): PackageRepository =
        PackageManagerRepository(context.applicationContext)

    fun createCatalogue(context: Context): SafetyCatalogue =
        AssetSafetyCatalogue(context.applicationContext)

    fun createHistoryStore(context: Context): OperationHistoryStore =
        SqliteOperationHistoryStore(context.applicationContext)

    fun createExecutor(
        shell: AdbShell,
        history: OperationHistoryStore,
        context: Context,
        catalogue: SafetyCatalogue,
    ): PackageOperationExecutor =
        PackageOperationExecutor(shell, history, catalogue, context.packageName) {
            if (
                android.os.UserHandle.getUserHandleForUid(android.os.Process.myUid()) ==
                    android.os.UserHandle.getUserHandleForUid(0)
            )
                0
            else -1
        }
}
