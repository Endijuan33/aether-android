package id.mybase.aether.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.mybase.aether.di.AppContainer
import id.mybase.aether.ui.about.AboutScreen
import id.mybase.aether.ui.apps.AppDetailsScreen
import id.mybase.aether.ui.apps.AppListScreen
import id.mybase.aether.ui.apps.DeviceRecommendationsScreen
import id.mybase.aether.ui.connection.ConnectionViewModel
import id.mybase.aether.ui.dashboard.DashboardScreen
import id.mybase.aether.ui.debloat.DebloatScreen
import id.mybase.aether.ui.debloat.PreviewDialog
import id.mybase.aether.ui.debloat.ProgressScreen
import id.mybase.aether.ui.diagnostics.DiagnosticsScreen
import id.mybase.aether.ui.history.HistoryScreen
import id.mybase.aether.ui.history.HistoryViewModel
import id.mybase.aether.ui.onboarding.OnboardingScreen
import id.mybase.aether.ui.packages.PackagesViewModel
import id.mybase.aether.ui.pairing.PairingDialog
import id.mybase.aether.ui.pairing.rememberPairingActions
import id.mybase.aether.ui.recovery.RecoveryScreen
import id.mybase.aether.ui.recovery.RecoveryViewModel
import id.mybase.aether.ui.settings.SettingsScreen
import id.mybase.aether.ui.settings.SettingsViewModel
import id.mybase.aether.ui.setup.AdvancedPairingScreen
import id.mybase.aether.ui.setup.SetupGuideScreen

/**
 * App navigation graph. Shared view models are activity-scoped (see [activityViewModel]) so dialogs
 * and screens observe the same state.
 */
@Composable
fun AetherNavHost(
    container: AppContainer,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val pairingActions = rememberPairingActions(container.wirelessPairing)

    val connectionVm: ConnectionViewModel = activityViewModel {
        ConnectionViewModel(
            container.shell,
            container.pairingClient,
            container.prefs,
            container.wirelessPairing,
        )
    }
    val packagesVm: PackagesViewModel = activitySavedStateViewModel { savedState ->
        PackagesViewModel(
            container.packageRepository,
            container.catalogue,
            container.executor,
            container.shell,
            container.history,
            savedState = savedState,
            deviceCatalogue = container.deviceCatalogue.state,
        )
    }
    val historyVm: HistoryViewModel = activityViewModel {
        HistoryViewModel(container.history)
    }
    val settingsVm: SettingsViewModel = activityViewModel {
        SettingsViewModel(container.prefs)
    }
    val recoveryVm: RecoveryViewModel = activityViewModel {
        RecoveryViewModel(container.packageRepository, container.executor, container.history)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(settingsVm = settingsVm, navController = navController)
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                connectionVm = connectionVm,
                packagesVm = packagesVm,
                historyVm = historyVm,
                navController = navController,
                prefs = container.prefs,
                pairingController = container.wirelessPairing,
                pairingActions = pairingActions,
            )
        }
        composable(Routes.SETUP_GUIDE) {
            SetupGuideScreen(
                controller = container.wirelessPairing,
                actions = pairingActions,
                navController = navController,
            )
        }
        composable(Routes.ADVANCED_PAIRING) {
            AdvancedPairingScreen(
                connectionVm = connectionVm,
                navController = navController,
                prefs = container.prefs,
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(
                connectionVm = connectionVm,
                navController = navController,
                prefs = container.prefs,
            )
        }
        composable(Routes.APPS) {
            AppListScreen(
                packagesVm = packagesVm,
                navController = navController,
            )
        }
        composable(
            route = Routes.APP_DETAILS,
            arguments = listOf(navArgument(Routes.ARG_PACKAGE_NAME) { type = NavType.StringType }),
        ) { entry ->
            val packageName = entry.arguments?.getString(Routes.ARG_PACKAGE_NAME).orEmpty()
            AppDetailsScreen(
                packageName = packageName,
                packagesVm = packagesVm,
                connectionVm = connectionVm,
                catalogue = container.catalogue,
                navController = navController,
            )
        }
        composable(Routes.DEBLOAT) {
            DebloatScreen(packagesVm = packagesVm, navController = navController)
        }
        composable(Routes.DEVICE_RECOMMENDATIONS) {
            DeviceRecommendationsScreen(
                packagesVm,
                navController,
                container.deviceCatalogue::refresh,
            )
        }
        composable(Routes.PROGRESS) {
            ProgressScreen(packagesVm = packagesVm, navController = navController)
        }
        composable(Routes.HISTORY) {
            HistoryScreen(historyVm = historyVm, navController = navController)
        }
        composable(Routes.RECOVERY) {
            RecoveryScreen(
                recoveryVm = recoveryVm,
                connectionVm = connectionVm,
                navController = navController,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsVm = settingsVm,
                historyVm = historyVm,
                navController = navController,
                prefs = container.prefs,
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(navController = navController)
        }
        dialog(Routes.PAIRING_DIALOG) {
            PairingDialog(connectionVm = connectionVm, navController = navController)
        }
        dialog(
            Routes.PREVIEW_DIALOG,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            PreviewDialog(packagesVm = packagesVm, navController = navController)
        }
    }
}
