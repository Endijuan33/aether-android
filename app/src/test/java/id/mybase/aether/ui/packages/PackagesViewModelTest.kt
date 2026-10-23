package id.mybase.aether.ui.packages

import app.cash.turbine.test
import id.mybase.aether.domain.AppKind
import id.mybase.aether.domain.OperationType
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.domain.isReversible
import id.mybase.aether.domain.riskOf
import id.mybase.aether.packages.PackageOperationExecutor
import id.mybase.aether.ui.fakes.FakeAdbShell
import id.mybase.aether.ui.fakes.FakeCatalogue
import id.mybase.aether.ui.fakes.FakeHistoryStore
import id.mybase.aether.ui.fakes.FakePackageRepository
import id.mybase.aether.ui.fakes.MainDispatcherRule
import id.mybase.aether.ui.fakes.testApp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [PackagesViewModel] behavior: loading, search / filter / sort, selection and preview risk
 * mapping. Uses hand fakes and Turbine.
 */
class PackagesViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: FakePackageRepository
    private lateinit var catalogue: FakeCatalogue
    private lateinit var shell: FakeAdbShell
    private lateinit var history: FakeHistoryStore
    private lateinit var executor: PackageOperationExecutor
    private lateinit var vm: PackagesViewModel

    private val apps =
        listOf(
            testApp("com.beta", label = "Beta"),
            testApp("com.alpha", label = "alpha", isSystem = true),
            testApp("com.gamma", label = "Gamma", isEnabled = false),
            testApp("com.essential", label = "Essential", isSystem = true),
        )

    @Before
    fun setUp() {
        repo = FakePackageRepository()
        catalogue =
            FakeCatalogue(
                mapOf(
                    "com.essential" to SafetyRating.ESSENTIAL,
                    "com.beta" to SafetyRating.RISKY,
                ),
            )
        shell = FakeAdbShell()
        history = FakeHistoryStore()
        executor = PackageOperationExecutor(shell, history)
        vm = PackagesViewModel(repo, catalogue, executor, shell, history)
    }

    @Test
    fun `load success populates apps`() = runBlocking {
        repo.apps = apps

        vm.uiState.test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertTrue(initial.apps.isEmpty())
            assertFalse(initial.isLoading)

            vm.loadApps()

            assertTrue(awaitItem().isLoading)
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(4, loaded.apps.size)
            assertNull(loaded.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load empty shows empty state without error`() {
        repo.apps = emptyList()

        vm.loadApps()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.apps.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `load error surfaces message`() {
        repo.error = RuntimeException("boom")

        vm.loadApps()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("boom", state.error)
    }

    @Test
    fun `search filters by label or package name`() {
        repo.apps = apps
        vm.loadApps()

        vm.setQuery("alpha")
        assertEquals(listOf("com.alpha"), vm.visibleApps.value.map { it.packageName })

        vm.setQuery("com.b")
        assertEquals(listOf("com.beta"), vm.visibleApps.value.map { it.packageName })

        vm.setQuery("GaMmA")
        assertEquals(listOf("com.gamma"), vm.visibleApps.value.map { it.packageName })

        vm.setQuery("")
        assertEquals(4, vm.visibleApps.value.size)
    }

    @Test
    fun `kind filter selects matching apps`() {
        repo.apps = apps
        vm.loadApps()

        vm.setKindFilter(AppKind.SYSTEM)
        assertEquals(
            listOf("com.alpha", "com.essential"),
            vm.visibleApps.value.map { it.packageName }.sorted(),
        )

        vm.setKindFilter(AppKind.DISABLED)
        assertEquals(listOf("com.gamma"), vm.visibleApps.value.map { it.packageName })

        vm.setKindFilter(AppKind.USER)
        assertEquals(listOf("com.beta"), vm.visibleApps.value.map { it.packageName })

        vm.setKindFilter(null)
        assertEquals(4, vm.visibleApps.value.size)
    }

    @Test
    fun `sort orders by name case-insensitively then by kind`() {
        repo.apps = apps
        vm.loadApps()

        // Default: NAME, case-insensitive.
        assertEquals(
            listOf("alpha", "Beta", "Essential", "Gamma"),
            vm.visibleApps.value.map { it.label },
        )

        vm.setSortBy(AppSort.KIND)
        // AppKind order: USER, SYSTEM, DISABLED, UPDATED_SYSTEM.
        assertEquals(
            listOf("com.beta", "com.alpha", "com.essential", "com.gamma"),
            vm.visibleApps.value.map { it.packageName },
        )
    }

    @Test
    fun `selection toggles and clears`() {
        vm.toggleSelect("com.alpha")
        assertTrue("com.alpha" in vm.uiState.value.selection)

        vm.toggleSelect("com.alpha")
        assertFalse("com.alpha" in vm.uiState.value.selection)

        vm.toggleSelect("com.alpha")
        vm.toggleSelect("com.beta")
        assertEquals(2, vm.uiState.value.selection.size)

        vm.clearSelection()
        assertTrue(vm.uiState.value.selection.isEmpty())
    }

    @Test
    fun `preview maps rating risk and reversibility`() {
        repo.apps = apps
        vm.loadApps()
        assertTrue(vm.preview(OperationType.DISABLE).isEmpty())

        vm.toggleSelect("com.alpha") // SYSTEM, UNKNOWN rating
        vm.toggleSelect("com.essential") // SYSTEM, ESSENTIAL rating

        val items = vm.preview(OperationType.DISABLE)
        assertEquals(2, items.size)
        val byPackage = items.associateBy { it.app.packageName }

        val alpha = byPackage.getValue("com.alpha")
        assertEquals(SafetyRating.UNKNOWN, alpha.rating)
        assertEquals(
            riskOf(OperationType.DISABLE, SafetyRating.UNKNOWN, AppKind.SYSTEM),
            alpha.risk,
        )
        assertEquals(OperationType.DISABLE.isReversible(), alpha.reversible)

        val essential = byPackage.getValue("com.essential")
        assertEquals(SafetyRating.ESSENTIAL, essential.rating)
        assertEquals(
            riskOf(OperationType.DISABLE, SafetyRating.ESSENTIAL, AppKind.SYSTEM),
            essential.risk,
        )
    }

    @Test
    fun `isConnected follows shell state`() = runBlocking {
        vm.isConnected.test(timeout = 5.seconds) {
            assertFalse(awaitItem())
            shell.emit(
                id.mybase.aether.adb.AdbConnectionState.Connected("192.168.1.5", 5555),
            )
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
