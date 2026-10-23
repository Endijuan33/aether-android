package id.mybase.aether.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.mybase.aether.adb.AdbConnectionState
import id.mybase.aether.adb.AdbPairingClient
import id.mybase.aether.adb.AdbShell
import id.mybase.aether.adb.PairingResult
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.security.InputValidators
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the ADB connection lifecycle: wireless-debug pairing, connecting and disconnecting, plus the
 * pending pairing target shared with the pairing dialog and redacted diagnostics for
 * troubleshooting.
 *
 * The pairing code itself is never persisted and never appears in diagnostics.
 */
class ConnectionViewModel(
    private val shell: AdbShell,
    private val pairingClient: AdbPairingClient,
    private val prefs: AetherPreferences,
) : ViewModel() {

    /** Live ADB connection state, sourced directly from [AdbShell]. */
    val state: StateFlow<AdbConnectionState> = shell.state

    private val _pairingError = MutableStateFlow<String?>(null)
    val pairingError: StateFlow<String?> = _pairingError.asStateFlow()

    private val _isPairing = MutableStateFlow(false)
    val isPairing: StateFlow<Boolean> = _isPairing.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

    /** Target the pairing dialog should pair with (set via [beginPairing]). */
    private val _pendingHost = MutableStateFlow<String?>(null)
    val pendingHost: StateFlow<String?> = _pendingHost.asStateFlow()

    private val _pendingPairingPort = MutableStateFlow<Int?>(null)
    val pendingPairingPort: StateFlow<Int?> = _pendingPairingPort.asStateFlow()

    private var lastStateChangeAt: Long = System.currentTimeMillis()
    private var lastEndpoint: String? = null

    init {
        viewModelScope.launch {
            state.collect { s ->
                lastStateChangeAt = System.currentTimeMillis()
                lastEndpoint =
                    when (s) {
                        is AdbConnectionState.Pairing -> "${s.host}:${s.port}"
                        is AdbConnectionState.Connecting -> "${s.host}:${s.port}"
                        is AdbConnectionState.Connected -> "${s.host}:${s.port}"
                        else -> lastEndpoint
                    }
            }
        }
    }

    /** Stages a pairing target for the pairing dialog to consume. */
    fun beginPairing(host: String, pairingPort: Int) {
        _pendingHost.value = host
        _pendingPairingPort.value = pairingPort
        _pairingError.value = null
    }

    fun clearPairingError() {
        _pairingError.value = null
    }

    fun clearConnectError() {
        _connectError.value = null
    }

    /**
     * Pairs with the device. Inputs are validated first; the pairing client is never invoked with
     * invalid input. On success the host and pairing port are persisted (the code is not).
     */
    fun pair(host: String, pairingPort: Int, code: String) {
        val trimmedHost = host.trim()
        val digits = InputValidators.filterPairingDigits(code)
        val validationError =
            when {
                !InputValidators.isValidHost(trimmedHost) ->
                    "Invalid host address. Use the IP shown under Wireless debugging."
                pairingPort !in 1..65535 -> "Pairing port must be between 1 and 65535."
                !InputValidators.isValidPairingCode(digits) -> "The pairing code must be 6 digits."
                else -> null
            }
        if (validationError != null) {
            _pairingError.value = validationError
            return
        }
        _pairingError.value = null
        _isPairing.value = true
        viewModelScope.launch {
            try {
                when (val result = pairingClient.pair(trimmedHost, pairingPort, digits)) {
                    PairingResult.Success -> {
                        prefs.setLastHost(trimmedHost)
                        prefs.setLastPairingPort(pairingPort)
                        _pairingError.value = null
                    }
                    PairingResult.InvalidCode ->
                        _pairingError.value =
                            "Wrong pairing code. Check the code shown on your device and try again."
                    PairingResult.Timeout ->
                        _pairingError.value =
                            "Pairing timed out. Make sure the device is reachable on the same network and try again."
                    PairingResult.ConnectionRefused ->
                        _pairingError.value =
                            "Connection refused. Check the host and pairing port, then retry."
                    is PairingResult.NetworkError ->
                        _pairingError.value = "Network error: ${result.message}"
                }
            } catch (e: Exception) {
                _pairingError.value = "Pairing failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _isPairing.value = false
            }
        }
    }

    /** Connects the ADB shell to a paired device. */
    fun connect(host: String, port: Int) {
        val trimmedHost = host.trim()
        val validationError =
            when {
                !InputValidators.isValidHost(trimmedHost) ->
                    "Invalid host address. Use the IP shown under Wireless debugging."
                port !in 1..65535 -> "Connection port must be between 1 and 65535."
                else -> null
            }
        if (validationError != null) {
            _connectError.value = validationError
            return
        }
        _connectError.value = null
        viewModelScope.launch {
            shell
                .connect(trimmedHost, port)
                .onSuccess {
                    prefs.setLastHost(trimmedHost)
                    prefs.setLastConnectionPort(port)
                }
                .onFailure { e ->
                    _connectError.value = e.message ?: "Connection failed."
                }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            shell.disconnect()
        }
    }

    /**
     * Diagnostics safe to share: connection state, last endpoint and timestamps. Never includes
     * pairing codes or keys (they are not stored).
     */
    fun redactedDiagnostics(): String {
        val stateName = state.value::class.simpleName ?: "Unknown"
        return buildString {
            appendLine("AETHER diagnostics (redacted)")
            appendLine("state=$stateName")
            appendLine("endpoint=${lastEndpoint ?: "none"}")
            appendLine("stateChangedAt=${formatTime(lastStateChangeAt)}")
            appendLine("generatedAt=${formatTime(System.currentTimeMillis())}")
            append("note=pairing codes and keys are never stored or included")
        }
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
}
