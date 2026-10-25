package id.mybase.aether

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.persistence.InMemoryOperationHistoryStore
import id.mybase.aether.ui.history.HistoryViewModel
import id.mybase.aether.ui.navigation.Routes
import id.mybase.aether.ui.onboarding.OnboardingScreen
import id.mybase.aether.ui.settings.SettingsScreen
import id.mybase.aether.ui.settings.SettingsViewModel
import id.mybase.aether.ui.theme.AetherTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real preference transformations with isolated deterministic storage. */
@RunWith(AndroidJUnit4::class)
class PreferencesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    @Test
    fun onboardingContinuePersistsBeforeNavigating() {
        val store = MemoryStore()
        val vm = SettingsViewModel(AetherPreferences(store))
        compose.setContent {
            AetherTheme {
                val nav = rememberNavController()
                NavHost(nav, Routes.ONBOARDING) {
                    composable(Routes.ONBOARDING) { OnboardingScreen(vm, nav) }
                    composable(Routes.DASHBOARD) { Text("Dashboard target") }
                }
            }
        }
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            store.data.value[booleanPreferencesKey("onboarding_completed")] == true
        }
        compose.onNodeWithText("Dashboard target").assertExists()
        compose.onNodeWithText("Welcome to AETHER").assertDoesNotExist()
    }

    @Test
    fun settingsThemeTogglePersists() {
        val store = MemoryStore()
        val prefs = AetherPreferences(store)
        val vm = SettingsViewModel(prefs)
        val history = HistoryViewModel(InMemoryOperationHistoryStore())
        compose.setContent {
            AetherTheme { SettingsScreen(vm, history, rememberNavController(), prefs) }
        }
        compose.onNodeWithContentDescription("Dark theme").assertIsOn().performClick()
        compose.waitUntil(5_000) { store.data.value[booleanPreferencesKey("dark_theme")] == false }
        compose.onNodeWithContentDescription("Dark theme").assertIsOff()
    }
}
