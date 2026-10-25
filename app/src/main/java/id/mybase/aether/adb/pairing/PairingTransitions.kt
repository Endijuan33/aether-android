package id.mybase.aether.adb.pairing

/**
 * Valid transitions inside one session; a user-initiated start creates a new session separately.
 */
internal object PairingTransitions {
    fun permits(from: PairingPhase, to: PairingPhase): Boolean {
        if (
            to == PairingPhase.IDLE &&
                from in
                    setOf(
                        PairingPhase.IDLE,
                        PairingPhase.WAITING_FOR_PERMISSION,
                        PairingPhase.DISCONNECTED,
                        PairingPhase.PAIRING_FAILED,
                        PairingPhase.CONNECTION_FAILED,
                        PairingPhase.TIMED_OUT,
                        PairingPhase.CANCELLED,
                    )
        )
            return true
        if (
            from == to ||
                to == PairingPhase.DISCONNECTING ||
                to == PairingPhase.PAIRING_FAILED ||
                to == PairingPhase.CONNECTION_FAILED ||
                to == PairingPhase.TIMED_OUT
        )
            return true
        return when (from) {
            PairingPhase.PREPARING ->
                to in
                    setOf(
                        PairingPhase.SEARCHING_PAIRING_SERVICE,
                        PairingPhase.WAITING_FOR_PAIRING_CODE,
                        PairingPhase.DISCOVERING_CONNECT_ENDPOINT,
                    )
            PairingPhase.SEARCHING_PAIRING_SERVICE ->
                to in
                    setOf(
                        PairingPhase.WAITING_FOR_PAIRING_CODE,
                        PairingPhase.PAIRING_IN_PROGRESS,
                    )
            PairingPhase.WAITING_FOR_PAIRING_CODE ->
                to in
                    setOf(PairingPhase.PAIRING_IN_PROGRESS, PairingPhase.SEARCHING_PAIRING_SERVICE)
            PairingPhase.PAIRING_IN_PROGRESS ->
                to in
                    setOf(
                        PairingPhase.PAIRING_SUCCESS,
                        PairingPhase.WAITING_FOR_PAIRING_CODE,
                        PairingPhase.SEARCHING_PAIRING_SERVICE,
                    )
            PairingPhase.PAIRING_SUCCESS -> to == PairingPhase.DISCOVERING_CONNECT_ENDPOINT
            PairingPhase.DISCOVERING_CONNECT_ENDPOINT -> to == PairingPhase.CONNECTING
            PairingPhase.CONNECTING ->
                to in setOf(PairingPhase.VERIFYING_SHELL, PairingPhase.DISCOVERING_CONNECT_ENDPOINT)
            PairingPhase.VERIFYING_SHELL ->
                to in setOf(PairingPhase.CONNECTED, PairingPhase.DISCOVERING_CONNECT_ENDPOINT)
            PairingPhase.DISCONNECTING ->
                to in setOf(PairingPhase.DISCONNECTED, PairingPhase.CANCELLED)
            else -> false
        }
    }
}
