package id.mybase.aether.ui

import android.provider.Settings
import id.mybase.aether.ui.pairing.*
import org.junit.Assert.*
import org.junit.Test

class DeveloperSettingsNavigatorTest {
    @Test
    fun officialActionIsFirstAndSuccessfulLaunchDoesNotFallback() {
        val actions = mutableListOf<String>()
        val result = DeveloperSettingsNavigator {
            actions += it
            true
        }
            .open()
        assertEquals(SettingsDestination.DEVELOPER_OPTIONS, result)
        assertEquals(listOf("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"), actions)
    }

    @Test
    fun rejectedDeveloperIntentFallsBackToNativeSettings() {
        val actions = mutableListOf<String>()
        val result = DeveloperSettingsNavigator {
            actions += it
            it == Settings.ACTION_SETTINGS
        }
            .open()
        assertEquals(SettingsDestination.SETTINGS, result)
        assertEquals(
            listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Settings.ACTION_SETTINGS),
            actions,
        )
    }

    @Test
    fun unavailableSettingsIsReported() {
        assertEquals(SettingsDestination.UNAVAILABLE, DeveloperSettingsNavigator { false }.open())
    }
}
