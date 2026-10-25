package id.mybase.aether.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.ui.fakes.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `failed write is observable and releases busy state`() = runTest {
        val store =
            object : DataStore<Preferences> {
                override val data = flowOf(emptyPreferences())

                override suspend fun updateData(
                    transform: suspend (Preferences) -> Preferences,
                ): Preferences {
                    throw java.io.IOException("Storage full")
                }
            }
        val vm = SettingsViewModel(AetherPreferences(store))
        vm.completeOnboarding()
        assertNotNull(vm.error.value)
        assertFalse(vm.isSaving.value)
        vm.setDarkTheme(false)
        assertNotNull(vm.error.value)
    }
}
