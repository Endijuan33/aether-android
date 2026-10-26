package id.mybase.aether.di

import android.content.Context
import id.mybase.aether.adb.AdbConnectionManager
import id.mybase.aether.adb.AdbPairingClient
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.adb.AndroidAdbKeyStore
import id.mybase.aether.adb.TlsPairingClient
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

    val applicationScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
        )

    val wirelessPairing by lazy {
        id.mybase.aether.adb.pairing.WirelessPairingController(
            pairingClient,
            shell,
            id.mybase.aether.adb.pairing.NsdAdbServiceDiscovery(appContext),
            applicationScope,
            rememberConnection = { host, port ->
                prefs.setLastHost(host)
                prefs.setLastConnectionPort(port)
            },
            diagnostics = id.mybase.aether.adb.pairing.AndroidPairingDiagnostics,
        )
    }

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance
                ?: synchronized(this) {
                    instance ?: AppContainer(context.applicationContext).also { instance = it }
                }
    }

    private val appContext: Context = context.applicationContext
    val icons by lazy { id.mybase.aether.packages.PackageIconProvider(appContext) }
    private val keys by lazy { AndroidAdbKeyStore(appContext) }

    val prefs: AetherPreferences by lazy {
        PersistenceModule.createPreferences(appContext)
    }

    val pairingClient: AdbPairingClient by lazy {
        TlsPairingClient(keys, diagnostics = id.mybase.aether.adb.pairing.AndroidPairingDiagnostics)
    }

    val shell: AdbShell by lazy {
        AdbConnectionManager(keys, pairingClient)
    }

    val packageRepository: PackageRepository by lazy {
        PackagesModule.createRepository(appContext)
    }

    val catalogue: SafetyCatalogue by lazy {
        id.mybase.aether.packages.DeviceAwareSafetyCatalogue(
            PackagesModule.createCatalogue(appContext),
            deviceCatalogue.state,
        )
    }

    val deviceCatalogue by lazy {
        id.mybase.aether.packages.DeviceCatalogueRepository(appContext, applicationScope)
    }

    val history: OperationHistoryStore by lazy {
        PackagesModule.createHistoryStore(appContext)
    }

    val executor: PackageOperationExecutor by lazy {
        PackagesModule.createExecutor(shell, history, appContext, catalogue)
    }
}
