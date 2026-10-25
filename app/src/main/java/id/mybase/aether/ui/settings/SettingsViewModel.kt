package id.mybase.aether.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.mybase.aether.persistence.AetherPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App settings backed by [AetherPreferences]. */
class SettingsViewModel(
    private val prefs: AetherPreferences,
) : ViewModel() {

    val darkTheme: Flow<Boolean> = prefs.darkTheme
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    val onboardingCompleted: Flow<Boolean> = prefs.onboardingCompleted

    fun setDarkTheme(dark: Boolean) {
        write { prefs.setDarkTheme(dark) }
    }

    fun completeOnboarding() {
        write { prefs.setOnboardingCompleted(true) }
    }

    fun resetOnboarding() {
        write { prefs.setOnboardingCompleted(false) }
    }

    private fun write(action: suspend () -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Settings could not be saved. Check available storage and retry."
            } finally {
                _isSaving.value = false
            }
        }
    }
}
