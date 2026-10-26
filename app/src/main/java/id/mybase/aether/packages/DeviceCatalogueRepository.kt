package id.mybase.aether.packages

import android.content.Context
import id.mybase.aether.device.AndroidDeviceInfoProvider
import id.mybase.aether.device.DeviceProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceCatalogueState(
    val loading: Boolean = false,
    val profile: DeviceProfile? = null,
    val revision: String = "unavailable",
    val entries: Map<String, PackageRecommendation> = emptyMap(),
    val error: String? = null,
)

/**
 * One immutable offline snapshot per process. Failed updates retain the last validated snapshot.
 */
class DeviceCatalogueRepository(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val _state = MutableStateFlow(DeviceCatalogueState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val snapshot =
                    withContext(Dispatchers.IO) {
                        val profile = AndroidDeviceInfoProvider().detect()
                        val bytes =
                            context.assets.open("device-catalogue.json").use { it.readBytes() }
                        val hash =
                            context.assets.open("device-catalogue.sha256").bufferedReader().use {
                                it.readText()
                            }
                        val layered = parseDeviceCatalogue(bytes, hash)
                        val legacy =
                            parseCatalogueJson(
                                context.assets.open("catalogue.json").bufferedReader().use {
                                    it.readText()
                                },
                            )
                        val catalogue =
                            DeviceCatalogue(
                                layered.revision,
                                legacyDeviceRules(legacy) + layered.rules,
                            )
                        DeviceCatalogueState(
                            profile = profile,
                            revision = catalogue.revision,
                            entries = catalogue.resolve(profile),
                        )
                    }
                _state.value = snapshot
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        loading = false,
                        error =
                            "Device catalogue could not be validated. Existing protections remain active; unverified packages stay unknown.",
                    )
            }
        }
    }
}
