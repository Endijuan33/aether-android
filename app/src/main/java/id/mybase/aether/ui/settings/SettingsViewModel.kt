package id.mybase.aether.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.mybase.aether.persistence.AetherPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** App settings backed by [AetherPreferences]. */
class SettingsViewModel(
    private val prefs: AetherPreferences,
) : ViewModel() {

    val darkTheme: Flow<Boolean> = prefs.darkTheme

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(dark) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingCompleted(true) }
    }

    fun resetOnboarding() {
        viewModelScope.launch { prefs.setOnboardingCompleted(false) }
    }
}
