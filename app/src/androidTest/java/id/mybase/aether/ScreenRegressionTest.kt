package id.mybase.aether

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.domain.*
import id.mybase.aether.packages.*
import id.mybase.aether.persistence.InMemoryOperationHistoryStore
import id.mybase.aether.ui.apps.AppListScreen
import id.mybase.aether.ui.debloat.DebloatScreen
import id.mybase.aether.ui.debloat.PreviewDialog
import id.mybase.aether.ui.packages.PackagesViewModel
import id.mybase.aether.ui.theme.AetherTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun app(pkg: String, label: String) =
        AppPackage(pkg, label, "1", 1, false, true, false, null, 30, emptyList(), 0, 0)

    private fun vm(
        rating: SafetyRating = SafetyRating.UNKNOWN,
        connected: Boolean = true,
        scanFailure: Boolean = false,
        empty: Boolean = false,
    ): PackagesViewModel {
        val apps =
            if (empty) emptyList()
            else listOf(app("com.example.alpha", "Alpha"), app("com.example.beta", "Beta"))
        val repo =
            object : PackageRepository {
                override suspend fun getInstalledPackages(): List<AppPackage> {
                    check(!scanFailure) { "Inventory unavailable" }
                    return apps
                }
            }
        val catalogue =
            MapSafetyCatalogue(
                "test",
                apps.associate {
                    it.packageName to
                        CatalogueEntry(
                            it.packageName,
                            rating,
                            "Fixture",
                            "test fixture",
                            "test",
                            "2026-10-25",
                        )
                },
            )
        val shell =
            object : AdbShell {
                override val state =
                    MutableStateFlow<AdbConnectionState>(
                        if (connected) AdbConnectionState.Connected("127.0.0.1", 37123)
                        else AdbConnectionState.Disconnected,
                    )

                override suspend fun connect(host: String, port: Int, timeoutMs: Long) =
                    Result.success(Unit)

                override suspend fun disconnect() = Unit

                override suspend fun exec(vararg args: String, timeoutMs: Long) =
                    Result.failure<String>(
                        IllegalStateException("UI fixture does not execute mutations"),
                    )
            }
        val history = InMemoryOperationHistoryStore()
        return PackagesViewModel(
            repo,
            catalogue,
            PackageOperationExecutor(shell, history, catalogue),
            shell,
            history,
        )
    }

    private fun preview(vm: PackagesViewModel) {
        compose.runOnUiThread {
            vm.loadApps()
            vm.prepareSinglePreview("com.example.alpha", OperationType.DISABLE)
        }
        compose.setContent { AetherTheme { PreviewDialog(vm, rememberNavController()) } }
    }

    @Test
    fun unknownPreviewRequiresAcknowledgement() {
        preview(vm())
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithContentDescription("I understand the risk").performClick()
        compose.onNodeWithText("Confirm").assertIsEnabled()
    }

    @Test
    fun essentialPreviewCannotBeConfirmed() {
        preview(vm(SafetyRating.ESSENTIAL))
        compose.onNodeWithText("Protected selection").assertExists()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithContentDescription("I understand the risk").assertDoesNotExist()
    }

    @Test
    fun offlinePreviewCannotBeConfirmed() {
        preview(vm(SafetyRating.SAFE, connected = false))
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Connect and verify the ADB shell before executing.").assertExists()
    }

    @Test
    fun packageSearchFiltersActualInventoryState() {
        val vm = vm()
        compose.runOnUiThread { vm.loadApps() }
        compose.setContent { AetherTheme { AppListScreen(vm, rememberNavController()) } }
        compose.onNodeWithText("Search apps").performTextInput("beta")
        compose.onNodeWithText("Beta").assertExists()
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        compose.onNodeWithContentDescription("Refresh inventory").assertIsEnabled()
    }

    @Test
    fun inventoryFailureShowsRetry() {
        val vm = vm(scanFailure = true)
        compose.runOnUiThread { vm.loadApps() }
        compose.setContent { AetherTheme { AppListScreen(vm, rememberNavController()) } }
        compose.onNodeWithText("Scan failed: Inventory unavailable").assertExists()
        compose.onNodeWithText("Retry").assertExists().performClick()
        compose.onNodeWithText("Scan failed: Inventory unavailable").assertExists()
    }

    @Test
    fun checkboxAreaUnchecksAfterSelectAllAndSurvivesRecomposition() {
        val vm = vm()
        compose.runOnUiThread { vm.loadApps() }
        compose.setContent { AetherTheme { DebloatScreen(vm, rememberNavController()) } }
        compose.onNodeWithText("Select all visible").performClick()
        compose.onNodeWithText("Preview (2)").assertIsEnabled()
        val alpha = compose.onNodeWithContentDescription("Select Alpha")
        alpha.assertIsOn().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width - 24.dp.toPx(), center.y))
        }
        alpha.assertIsOff()
        compose.onNodeWithText("Preview (1)").assertIsEnabled()
        compose.runOnUiThread { vm.setQuery("beta") }
        compose.onNodeWithContentDescription("Select Beta").assertIsOn()
        compose.runOnUiThread { vm.setQuery("") }
        alpha.assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Deselect all visible").performClick()
        compose.onNodeWithText("Preview (0)").assertIsNotEnabled()
    }

    @Test
    fun essentialRowsCannotBeSelectedByBulkOrCheckbox() {
        val vm = vm(SafetyRating.ESSENTIAL)
        compose.runOnUiThread { vm.loadApps() }
        compose.setContent { AetherTheme { DebloatScreen(vm, rememberNavController()) } }
        compose.onNodeWithText("Select all visible").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Select Alpha").assertIsNotEnabled().assertIsOff()
        compose.onNodeWithText("Preview (0)").assertIsNotEnabled()
    }

    @Test
    fun emptyPreviewDoesNotAllowExecution() {
        preview(vm(empty = true))
        compose.onNodeWithText("Nothing to preview. Select at least one app first.").assertExists()
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test
    fun recommendationsNeverSelectAutomaticallyAndUseSharedSelection() {
        val vm = vm()
        compose.runOnUiThread { vm.loadApps() }
        compose.setContent {
            AetherTheme {
                id.mybase.aether.ui.apps.DeviceRecommendationsScreen(
                    vm,
                    rememberNavController(),
                    {},
                )
            }
        }
        compose.onNodeWithText("Preview (0)").assertIsNotEnabled()
        compose
            .onNodeWithContentDescription("Select Alpha")
            .performScrollTo()
            .assertIsOff()
            .performClick()
            .assertIsOn()
        compose.onNodeWithText("Preview (1)").assertIsEnabled()
        compose.runOnUiThread { vm.setQuery("beta") }
        compose.onNodeWithContentDescription("Select Alpha").assertDoesNotExist()
        compose.onNodeWithText("Preview (1)").assertIsEnabled()
        org.junit.Assert.assertEquals(setOf("com.example.alpha"), vm.uiState.value.selection)
        compose.runOnUiThread { vm.setQuery("") }
        compose
            .onNodeWithContentDescription("Select Alpha")
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
        compose.onNodeWithText("Preview (0)").assertIsNotEnabled()
        org.junit.Assert.assertTrue(vm.uiState.value.selection.isEmpty())
    }

    @Test
    fun deviceInformationShowsLimitationsWithoutFingerprint() {
        val profile =
            id.mybase.aether.device.DeviceProfileDetector.detect(
                id.mybase.aether.device.DeviceBuild(
                    manufacturer = "Xiaomi",
                    brand = "POCO",
                    model = "POCO F6",
                    sdk = 36,
                    fingerprint = "private-build-fingerprint",
                ),
            )
        compose.setContent {
            AetherTheme {
                androidx.compose.foundation.layout.Column {
                    id.mybase.aether.ui.apps.DeviceInformation(profile)
                }
            }
        }
        compose.onNodeWithText("android-baseline").assertExists()
        compose
            .onNodeWithText(
                "OEM version unavailable; version-specific recommendations are withheld.",
            )
            .assertExists()
        compose.onNodeWithText("private-build-fingerprint").assertDoesNotExist()
    }
}
