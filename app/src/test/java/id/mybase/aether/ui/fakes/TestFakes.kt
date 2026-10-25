package id.mybase.aether.ui.fakes

import android.content.Context
import android.content.ContextWrapper
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.adb.AdbPairingClient
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.adb.PairingResult
import id.mybase.aether.domain.AppPackage
import id.mybase.aether.domain.CatalogueEntry
import id.mybase.aether.domain.OperationRecord
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.packages.PackageRepository
import id.mybase.aether.packages.SafetyCatalogue
import id.mybase.aether.persistence.OperationHistoryStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that installs an [UnconfinedTestDispatcher] as the Main dispatcher, so
 * `viewModelScope` (Main.immediate) executes eagerly and deterministically in unit tests.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FakeAdbShell : AdbShell {
    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
    override val state: StateFlow<AdbConnectionState> = _state.asStateFlow()

    var connectCalls = 0
    var lastConnectArgs: Pair<String, Int>? = null
    var connectResult: Result<Unit> = Result.success(Unit)
    var disconnectCalls = 0
    var execResult: Result<String> = Result.success("2000")

    override suspend fun connect(host: String, port: Int, timeoutMs: Long): Result<Unit> {
        connectCalls++
        lastConnectArgs = host to port
        if (connectResult.isSuccess) _state.value = AdbConnectionState.Connected(host, port)
        return connectResult
    }

    override suspend fun disconnect() {
        disconnectCalls++
        _state.value = AdbConnectionState.Disconnected
    }

    override suspend fun exec(vararg args: String, timeoutMs: Long): Result<String> = execResult

    fun emit(state: AdbConnectionState) {
        _state.value = state
    }
}

class FakePairingClient(var result: PairingResult = PairingResult.Success) : AdbPairingClient {
    var calls = 0
    var lastArgs: Triple<String, Int, String>? = null

    override suspend fun pair(
        host: String,
        port: Int,
        pairingCode: String,
        timeoutMs: Long,
    ): PairingResult {
        calls++
        lastArgs = Triple(host, port, pairingCode)
        return result
    }
}

class FakePackageRepository(
    var apps: List<AppPackage> = emptyList(),
    var error: Throwable? = null,
) : PackageRepository {
    override suspend fun getInstalledPackages(): List<AppPackage> {
        error?.let { throw it }
        return apps
    }
}

class FakeCatalogue(
    private val ratings: Map<String, SafetyRating> = emptyMap(),
) : SafetyCatalogue {
    override val revision: String = "test-rev"

    override fun entryFor(packageName: String): CatalogueEntry? = null

    override fun ratingFor(packageName: String): SafetyRating =
        ratings[packageName] ?: SafetyRating.UNKNOWN
}

class FakeHistoryStore : OperationHistoryStore {
    private val records = mutableListOf<OperationRecord>()
    private var nextId = 1L

    override suspend fun complete(id: Long, status: String, message: String) {
        val index = records.indexOfFirst { it.id == id }
        check(index >= 0)
        records[index] = records[index].copy(status = status, message = message)
    }

    override suspend fun record(
        timestamp: Long,
        packageName: String,
        label: String,
        operation: String,
        status: String,
        message: String,
    ): Long {
        val record =
            OperationRecord(
                id = nextId++,
                timestamp = timestamp,
                packageName = packageName,
                label = label,
                operation = operation,
                status = status,
                message = message,
            )
        records.add(0, record)
        return record.id
    }

    override suspend fun list(limit: Int): List<OperationRecord> = records.take(limit)

    override suspend fun clear() {
        records.clear()
    }

    fun seed(vararg seed: OperationRecord) {
        records.addAll(seed)
    }
}

/**
 * Minimal Context for unit tests. Only [getApplicationContext] and [getFilesDir] (backed by a temp
 * dir) are functional — enough for a DataStore-backed
 * [id.mybase.aether.persistence.AetherPreferences]. Everything else delegates to a null base and
 * throws if touched.
 */
class FakeContext : ContextWrapper(null) {
    private val dir: File = Files.createTempDirectory("aether-prefs-test").toFile()

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = dir

    override fun getCacheDir(): File = dir

    override fun getPackageName(): String = "id.mybase.aether.test"
}

/** Builds an [AppPackage] for tests (numeric literals work for Int or Long fields). */
fun testApp(
    packageName: String,
    label: String = packageName,
    isSystem: Boolean = false,
    isEnabled: Boolean = true,
    isUpdatedSystemApp: Boolean = false,
): AppPackage =
    AppPackage(
        packageName = packageName,
        label = label,
        versionName = "1.0",
        versionCode = 1,
        isSystem = isSystem,
        isEnabled = isEnabled,
        isUpdatedSystemApp = isUpdatedSystemApp,
        installerPackageName = null,
        targetSdk = 34,
        permissions = emptyList(),
        firstInstallTime = 0,
        lastUpdateTime = 0,
    )
