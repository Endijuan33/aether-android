package id.mybase.aether.di

import android.content.Context
import id.mybase.aether.adb.AdbModule
import id.mybase.aether.adb.AdbPairingClient
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.packages.PackageOperationExecutor
import id.mybase.aether.packages.PackageRepository
import id.mybase.aether.packages.PackagesModule
import id.mybase.aether.packages.SafetyCatalogue
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.persistence.OperationHistoryStore
import id.mybase.aether.persistence.PersistenceModule

/**
 * Manual dependency graph for the app (no DI framework).
 *
 * Everything is built from [context.applicationContext] via the module factories owned by the adb /
 * packages / persistence workstreams. Instances are lazy singletons scoped to the process.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val prefs: AetherPreferences by lazy {
        PersistenceModule.createPreferences(appContext)
    }

    val pairingClient: AdbPairingClient by lazy {
        AdbModule.createPairingClient(appContext)
    }

    val shell: AdbShell by lazy {
        AdbModule.createShell(appContext)
    }

    val packageRepository: PackageRepository by lazy {
        PackagesModule.createRepository(appContext)
    }

    val catalogue: SafetyCatalogue by lazy {
        PackagesModule.createCatalogue(appContext)
    }

    val history: OperationHistoryStore by lazy {
        PackagesModule.createHistoryStore(appContext)
    }

    val executor: PackageOperationExecutor by lazy {
        PackagesModule.createExecutor(shell, history)
    }
}
