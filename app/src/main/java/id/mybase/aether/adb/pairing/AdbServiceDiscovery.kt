package id.mybase.aether.adb.pairing

import kotlinx.coroutines.flow.Flow

/** Null invalidates a lost endpoint. Each collection owns an independent, cancellable session. */
interface AdbServiceDiscovery {
    fun watch(type: AdbServiceType): Flow<DiscoveredAdbEndpoint?>
}
