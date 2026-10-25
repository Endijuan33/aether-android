package id.mybase.aether.persistence

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PreferencesTest {
    @Test
    fun `defaults do not invent connection or onboarding state`() = runTest {
        val prefs = AetherPreferences(InMemoryPreferencesDataStore())
        assertTrue(prefs.darkTheme.first())
        assertFalse(prefs.onboardingCompleted.first())
        assertNull(prefs.lastHost.first())
        assertNull(prefs.lastPairingPort.first())
        assertNull(prefs.lastConnectionPort.first())
    }

    @Test
    fun `ports remain distinct and theme and onboarding persist`() = runTest {
        val store = InMemoryPreferencesDataStore()
        val prefs = AetherPreferences(store)
        prefs.setLastHost("127.0.0.1")
        prefs.setLastPairingPort(37123)
        prefs.setLastConnectionPort(38123)
        prefs.setDarkTheme(false)
        prefs.setOnboardingCompleted(true)
        val recreated = AetherPreferences(store)
        assertEquals(37123, recreated.lastPairingPort.first())
        assertEquals(38123, recreated.lastConnectionPort.first())
        assertEquals("127.0.0.1", recreated.lastHost.first())
        assertFalse(recreated.darkTheme.first())
        assertTrue(recreated.onboardingCompleted.first())
        prefs.setLastPairingPort(null)
        assertNull(recreated.lastPairingPort.first())
        assertEquals(38123, recreated.lastConnectionPort.first())
    }
}
