package id.mybase.aether.packages

import androidx.lifecycle.SavedStateHandle
import id.mybase.aether.device.*
import id.mybase.aether.domain.*
import id.mybase.aether.ui.fakes.*
import id.mybase.aether.ui.fakes.FakeHistoryStore
import id.mybase.aether.ui.packages.PackagesViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceCatalogueIntegrationTest {
    @get:Rule val main = MainDispatcherRule()

    private fun snapshot(): DeviceCatalogueState {
        val profile =
            DeviceProfileDetector.detect(
                DeviceBuild(
                    manufacturer = "Xiaomi",
                    brand = "POCO",
                    model = "24069PC21G",
                    device = "peridot",
                    sdk = 36,
                ),
                mapOf("ro.mi.os.version.name" to "OS3.0.1"),
            )
        val base = parseCatalogueJson(File("src/main/assets/catalogue.json").readText())
        val extra =
            parseDeviceCatalogue(
                File("src/main/assets/device-catalogue.json").readBytes(),
                File("src/main/assets/device-catalogue.sha256").readText(),
            )
        val catalogue = DeviceCatalogue(extra.revision, legacyDeviceRules(base) + extra.rules)
        return DeviceCatalogueState(
            profile = profile,
            revision = catalogue.revision,
            entries = catalogue.resolve(profile),
        )
    }

    @Test
    fun initialPocoProfileContainsOnlyEvidenceBackedReferencesAndNoSafeRemovals() {
        val snapshot = snapshot()
        assertEquals("poco-f6-hyperos-android16", snapshot.profile!!.catalogueProfile)
        assertEquals(55, snapshot.entries.size)
        assertEquals(
            16,
            snapshot.entries.values.count { it.classification == RecommendationSafety.CRITICAL },
        )
        assertEquals(
            17,
            snapshot.entries.values.count { it.classification == RecommendationSafety.CAUTION },
        )
        assertEquals(
            22,
            snapshot.entries.values.count { it.classification == RecommendationSafety.UNKNOWN },
        )
        assertTrue(
            snapshot.entries.values.none {
                it.operation(OperationType.DISABLE).recommended ||
                    it.operation(OperationType.UNINSTALL_FOR_USER).recommended
            },
        )
    }

    @Test
    fun bridgePreservesProtectionsAndNeverPromotesHistoricalSafeEvidence() {
        val state = MutableStateFlow(snapshot())
        val legacy =
            FakeCatalogue(
                mapOf(
                    "com.legacy.critical" to SafetyRating.ESSENTIAL,
                    "com.legacy.safe" to SafetyRating.SAFE,
                ),
            )
        val catalogue = DeviceAwareSafetyCatalogue(legacy, state)
        assertEquals(SafetyRating.ESSENTIAL, catalogue.ratingFor("com.legacy.critical"))
        assertEquals(SafetyRating.UNKNOWN, catalogue.ratingFor("com.legacy.safe"))
        assertEquals(SafetyRating.ESSENTIAL, catalogue.ratingFor("com.miui.securitycenter"))
        assertEquals(SafetyRating.UNKNOWN, catalogue.ratingFor("com.miui.analytics"))
        assertEquals(SafetyRating.UNKNOWN, catalogue.ratingFor("com.example.absent"))
        val rule =
            state.value.entries
                .getValue("com.android.systemui")
                .detail
                .copy(
                    packageName = "com.example.operation",
                    classification = RecommendationSafety.UNSAFE,
                    disable =
                        OperationRecommendation(RecommendationSafety.UNKNOWN, false, "Unverified"),
                    uninstall = OperationRecommendation(RecommendationSafety.UNSAFE, false, "Loss"),
                )
        state.value =
            state.value.copy(
                entries =
                    state.value.entries +
                        (rule.packageName to PackageRecommendation(rule.packageName, listOf(rule))),
            )
        assertEquals(
            SafetyRating.UNKNOWN,
            catalogue.ratingForOperation(rule.packageName, OperationType.DISABLE),
        )
        assertEquals(
            SafetyRating.RISKY,
            catalogue.ratingForOperation(rule.packageName, OperationType.UNINSTALL_FOR_USER),
        )
    }

    @Test
    fun arrivingCataloguePrunesProtectedSelectionAndKeepsHiddenSelectionsAndPreviewConsistent() {
        val state = MutableStateFlow(DeviceCatalogueState(loading = true))
        val catalogue = DeviceAwareSafetyCatalogue(FakeCatalogue(), state)
        val repo =
            FakePackageRepository().apply {
                apps =
                    listOf(
                        testApp("com.miui.securitycenter"),
                        testApp("com.miui.analytics"),
                        testApp("com.android.phone"),
                    )
            }
        val shell = FakeAdbShell()
        val history = FakeHistoryStore()
        val vm =
            PackagesViewModel(
                repo,
                catalogue,
                PackageOperationExecutor(shell, history, catalogue),
                shell,
                history,
                main.dispatcher,
                SavedStateHandle(),
                state,
            )
        vm.loadApps()
        vm.selectAllVisible()
        vm.preparePreview(OperationType.DISABLE)
        state.value = snapshot()
        assertEquals(setOf("com.miui.analytics"), vm.uiState.value.selection)
        assertEquals(1, vm.preview(OperationType.DISABLE).size)
        assertEquals(
            RiskLevel.CRITICAL,
            vm.previewItems.value.first { it.app.packageName == "com.miui.securitycenter" }.risk,
        )
        vm.setSafetyFilter(RecommendationSafety.CRITICAL)
        assertEquals(2, vm.visibleApps.value.size)
        assertEquals(1, vm.selectionSummary(vm.visibleApps.value).hiddenSelected)
        vm.selectAllVisible()
        assertEquals(setOf("com.miui.analytics"), vm.uiState.value.selection)
        assertTrue(kotlinx.coroutines.runBlocking { history.list(10).isEmpty() })
    }
}
