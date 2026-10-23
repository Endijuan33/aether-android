package id.mybase.aether.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aetherDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "aether_prefs")

/**
 * App settings backed by DataStore preferences file "aether_prefs". All writes are suspending; all
 * reads are cold [Flow]s.
 */
class AetherPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_HOST = stringPreferencesKey("last_host")
        val LAST_PAIRING_PORT = intPreferencesKey("last_pairing_port")
        val LAST_CONNECTION_PORT = intPreferencesKey("last_connection_port")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val onboardingCompleted: Flow<Boolean> =
        context.aetherDataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.aetherDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    val lastHost: Flow<String?> = context.aetherDataStore.data.map { it[Keys.LAST_HOST] }

    suspend fun setLastHost(host: String?) {
        context.aetherDataStore.edit {
            if (host == null) it.remove(Keys.LAST_HOST) else it[Keys.LAST_HOST] = host
        }
    }

    val lastPairingPort: Flow<Int?> =
        context.aetherDataStore.data.map { it[Keys.LAST_PAIRING_PORT] }

    suspend fun setLastPairingPort(port: Int?) {
        context.aetherDataStore.edit {
            if (port == null) it.remove(Keys.LAST_PAIRING_PORT)
            else it[Keys.LAST_PAIRING_PORT] = port
        }
    }

    val lastConnectionPort: Flow<Int?> =
        context.aetherDataStore.data.map { it[Keys.LAST_CONNECTION_PORT] }

    suspend fun setLastConnectionPort(port: Int?) {
        context.aetherDataStore.edit {
            if (port == null) it.remove(Keys.LAST_CONNECTION_PORT)
            else it[Keys.LAST_CONNECTION_PORT] = port
        }
    }

    val darkTheme: Flow<Boolean> = context.aetherDataStore.data.map { it[Keys.DARK_THEME] ?: true }

    suspend fun setDarkTheme(dark: Boolean) {
        context.aetherDataStore.edit { it[Keys.DARK_THEME] = dark }
    }
}
