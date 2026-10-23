package id.mybase.aether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import id.mybase.aether.di.AppContainer
import id.mybase.aether.ui.navigation.AetherNavHost
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.theme.AetherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single-activity host for the AETHER app.
 *
 * Reads the onboarding flag once at startup to pick the start destination (keeping the splash
 * screen up until it resolves), then renders the navigation host inside the branding-owned
 * [AetherTheme].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val container = AppContainer(this)

        var startDestination by mutableStateOf<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDestination == null }
        lifecycleScope.launch {
            val completed = container.prefs.onboardingCompleted.first()
            startDestination = if (completed) Routes.DASHBOARD else Routes.ONBOARDING
        }

        setContent {
            val darkTheme by container.prefs.darkTheme.collectAsState(initial = true)
            AetherTheme(darkTheme = darkTheme) {
                val destination = startDestination
                if (destination != null) {
                    AetherNavHost(container = container, startDestination = destination)
                }
            }
        }
    }
}
