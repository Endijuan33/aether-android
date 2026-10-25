package id.mybase.aether.adb.pairing

import java.io.Closeable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Owns bounded callback registrations, resolution fallback and stale-result rejection. */
internal class ResilientNsdDiscovery(
    private val platform: NsdDiscoveryPlatform,
    private val diagnostics: PairingDiagnostics,
    private val dispatcher: CoroutineDispatcher,
    private val localEndpoint:
        suspend (NsdAdvertisement, NsdEndpointUpdate) -> DiscoveredAdbEndpoint?,
    private val resolutionTimeoutMs: Long = 5_000,
) : AdbServiceDiscovery {
    override fun watch(type: AdbServiceType): Flow<DiscoveredAdbEndpoint?> = callbackFlow {
        class Entry(val advertisement: NsdAdvertisement) {
            val lease = DiscoveryLease()
            var monitor: Closeable? = null
            var job: Job? = null
            var receivedUpdate = false
            var fallbackStarted = false
        }
        val entries = mutableMapOf<String, Entry>()
        val endpoints = linkedMapOf<String, DiscoveredAdbEndpoint>()
        var selected: DiscoveredAdbEndpoint? = null
        var active = true
        fun record(
            event: PairingDiagnosticEvent,
            error: Int? = null,
            failure: DiscoveryFailureReason? = null,
        ) {
            diagnostics.record(
                PairingDiagnostic(event, service = type, platformError = error, failure = failure),
            )
        }
        fun current(entry: Entry) = active && entries[entry.advertisement.name] === entry
        fun publish() {
            selected =
                selected?.takeIf { endpoints[it.serviceName] == it }
                    ?: endpoints.values.firstOrNull()
            trySend(selected)
        }
        fun release(entry: Entry) {
            entry.lease.invalidate()
            entry.job?.cancel()
            entry.monitor?.close()
            entry.monitor = null
        }
        suspend fun resolved(entry: Entry, info: NsdEndpointUpdate): Boolean {
            if (!current(entry)) return true
            val revision = entry.lease.beginUpdate()
            val endpoint = localEndpoint(entry.advertisement, info)
            if (!current(entry) || !entry.lease.isCurrent(revision)) return true
            if (endpoint == null) {
                endpoints.remove(entry.advertisement.name)
                record(
                    PairingDiagnosticEvent.NSD_FAILED,
                    failure = DiscoveryFailureReason.NO_LOCAL_ENDPOINT,
                )
            } else {
                endpoints[entry.advertisement.name] = endpoint
                record(PairingDiagnosticEvent.NSD_RESOLVED)
            }
            publish()
            return endpoint != null
        }
        fun fallback(entry: Entry) {
            if (!current(entry) || entry.fallbackStarted) return
            entry.fallbackStarted = true
            entry.job?.cancel()
            entry.monitor?.close()
            entry.monitor = null
            record(PairingDiagnosticEvent.NSD_RESOLUTION_FALLBACK)
            entry.job = launch {
                var error: Int? = null
                for (attempt in 0..1) {
                    if (!current(entry)) return@launch
                    try {
                        val update =
                            withTimeout(resolutionTimeoutMs) {
                                platform.resolve(entry.advertisement)
                            }
                        resolved(entry, update)
                        return@launch
                    } catch (e: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SecurityException) {
                        close(AdbDiscoveryException(DiscoveryFailureReason.PERMISSION, type))
                        return@launch
                    } catch (e: NsdPlatformException) {
                        error = e.errorCode
                    } catch (e: Exception) {
                        // No platform/advertisement text is logged or shown to users.
                    }
                    record(
                        PairingDiagnosticEvent.NSD_FAILED,
                        error,
                        DiscoveryFailureReason.RESOLUTION,
                    )
                    if (attempt == 0) {
                        record(PairingDiagnosticEvent.NSD_RETRY, error)
                        delay(250)
                    }
                }
                if (current(entry) && endpoints.isEmpty() && entries.size == 1)
                    close(AdbDiscoveryException(DiscoveryFailureReason.RESOLUTION, type, error))
            }
        }
        val listener =
            object : NsdDiscoveryPlatform.DiscoveryListener {
                override fun started() {
                    record(PairingDiagnosticEvent.NSD_STARTED)
                }

                override fun found(service: NsdAdvertisement) {
                    launch {
                        if (
                            !active ||
                                service.type.trimEnd('.') != type.dnsName.trimEnd('.') ||
                                entries.containsKey(service.name) ||
                                entries.size >= 32
                        )
                            return@launch
                        record(PairingDiagnosticEvent.NSD_FOUND)
                        val entry = Entry(service)
                        entries[service.name] = entry
                        if (!platform.supportsMonitoring) {
                            fallback(entry)
                            return@launch
                        }
                        try {
                            entry.monitor =
                                platform.monitor(
                                    service,
                                    object : NsdDiscoveryPlatform.UpdateListener {
                                        override fun updated(endpoint: NsdEndpointUpdate) {
                                            launch {
                                                if (!current(entry) || entry.fallbackStarted)
                                                    return@launch
                                                entry.receivedUpdate = true
                                                if (!resolved(entry, endpoint)) fallback(entry)
                                            }
                                        }

                                        override fun lost() {
                                            launch {
                                                if (!current(entry) || entry.fallbackStarted)
                                                    return@launch
                                                entry.lease.beginUpdate()
                                                endpoints.remove(service.name)
                                                record(PairingDiagnosticEvent.NSD_LOST)
                                                publish()
                                            }
                                        }

                                        override fun failed(errorCode: Int) {
                                            launch {
                                                if (!current(entry) || entry.fallbackStarted)
                                                    return@launch
                                                record(
                                                    PairingDiagnosticEvent.NSD_FAILED,
                                                    errorCode,
                                                    DiscoveryFailureReason.RESOLUTION,
                                                )
                                                fallback(entry)
                                            }
                                        }
                                    },
                                )
                            entry.job = launch {
                                delay(resolutionTimeoutMs)
                                if (current(entry) && !entry.receivedUpdate) fallback(entry)
                            }
                        } catch (e: SecurityException) {
                            close(AdbDiscoveryException(DiscoveryFailureReason.PERMISSION, type))
                        } catch (e: Exception) {
                            record(
                                PairingDiagnosticEvent.NSD_FAILED,
                                failure = DiscoveryFailureReason.RESOLUTION,
                            )
                            fallback(entry)
                        }
                    }
                }

                override fun lost(service: NsdAdvertisement) {
                    launch {
                        if (!active) return@launch
                        entries.remove(service.name)?.let(::release)
                        endpoints.remove(service.name)
                        record(PairingDiagnosticEvent.NSD_LOST)
                        publish()
                    }
                }

                override fun failed(errorCode: Int) {
                    record(
                        PairingDiagnosticEvent.NSD_FAILED,
                        errorCode,
                        DiscoveryFailureReason.START,
                    )
                    close(AdbDiscoveryException(DiscoveryFailureReason.START, type, errorCode))
                }

                override fun stopped() {
                    record(PairingDiagnosticEvent.NSD_STOPPED)
                    if (active) close(AdbDiscoveryException(DiscoveryFailureReason.STOP, type))
                }
            }
        var discovery: Closeable? = null
        var wifi: Closeable? = null
        trySend(null)
        try {
            record(PairingDiagnosticEvent.NSD_REQUESTED)
            discovery = platform.discover(type, listener)
            wifi = platform.watchWifi {
                launch {
                    if (active) close(AdbDiscoveryException(DiscoveryFailureReason.WIFI_LOST, type))
                }
            }
        } catch (e: SecurityException) {
            close(AdbDiscoveryException(DiscoveryFailureReason.PERMISSION, type))
        } catch (e: Exception) {
            close(AdbDiscoveryException(DiscoveryFailureReason.START, type))
        }
        awaitClose {
            active = false
            entries.values.forEach(::release)
            entries.clear()
            endpoints.clear()
            discovery?.close()
            wifi?.close()
            record(PairingDiagnosticEvent.NSD_STOPPED)
        }
    }
        .flowOn(dispatcher)
        .distinctUntilChanged()
}
