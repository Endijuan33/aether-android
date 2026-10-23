package id.mybase.aether.adb

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * One ADB transport connection (TLS socket + CNXN handshake) multiplexing any number of streams.
 *
 * A single reader coroutine ([demuxJob]) owns the socket input: it reads [AdbProtocol] messages and
 * routes them to per-stream state by local id. WRTE flow-control acknowledgements are sent from the
 * demux itself so a slow consumer can never stall the peer. Stream consumers read from a [Channel],
 * which makes [execShell] cancellation clean: cancelling the collector triggers a CLSE for that
 * stream while the session — and any other stream — stays alive.
 *
 * All blocking socket I/O runs on [ioDispatcher]; all writes are serialized through [writeMutex].
 */
internal class AdbSession
private constructor(
    private val socket: SSLSocket,
    parentScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) {
    private val sessionScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob() + ioDispatcher)
    private val input: InputStream = socket.inputStream
    private val output: OutputStream = socket.outputStream
    private val writeMutex = Mutex()
    private val streams = ConcurrentHashMap<Int, StreamState>()
    private val nextLocalId = AtomicInteger(1)
    private val closed = AtomicBoolean(false)
    private val sessionError = AtomicReference<Throwable?>(null)

    private val demuxJob: Job = sessionScope.launch {
        try {
            while (isActive) route(AdbProtocol.readMessage(input))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failAll(e)
        }
    }

    companion object {
        /**
         * Opens the TLS channel and performs the CNXN handshake. In TLS mode no AUTH exchange takes
         * place: presenting our client certificate during the handshake is the authentication.
         *
         * @throws TimeoutCancellationException when the handshake exceeds [timeoutMs]
         * @throws java.net.ConnectException when the port actively refuses
         * @throws AdbException on handshake-level failures
         */
        suspend fun open(
            host: String,
            port: Int,
            keys: AdbKeyMaterial,
            timeoutMs: Long,
            parentScope: CoroutineScope,
            ioDispatcher: CoroutineDispatcher,
        ): AdbSession =
            withContext(ioDispatcher) {
                val socket =
                    AdbTlsTransport.openAdbChannel(
                        host,
                        port,
                        keys,
                        connectTimeoutMs = timeoutMs.coerceAtMost(10_000).toInt().coerceAtLeast(1),
                    )
                try {
                    // The caller (AdbConnectionManager.connect) bounds this with
                    // withTimeout; withSocketGuard makes a hung handshake
                    // unblock on cancellation instead of leaking the thread.
                    withSocketGuard(socket) {
                        val input = socket.inputStream
                        val output = socket.outputStream
                        AdbProtocol.writeMessage(
                            output,
                            AdbProtocol.CMD_CNXN,
                            AdbProtocol.ADB_VERSION,
                            AdbProtocol.MAX_PAYLOAD,
                            AdbProtocol.CNXN_PAYLOAD,
                        )
                        val response = AdbProtocol.readMessage(input)
                        when (response.command) {
                            AdbProtocol.CMD_CNXN -> Unit
                            AdbProtocol.CMD_AUTH ->
                                throw AdbException(
                                    "adbd requested classic AUTH over TLS; this client authenticates " +
                                        "with its TLS client certificate only",
                                )
                            else ->
                                throw AdbException(
                                    "unexpected handshake message: 0x${response.command.toString(16)}",
                                )
                        }
                    }
                    AdbSession(socket, parentScope, ioDispatcher)
                } catch (e: Throwable) {
                    runCatching { socket.close() }
                    throw e
                }
            }
    }

    /**
     * Runs one shell command and returns its combined stdout/stderr output. Cancellable: aborting
     * the caller closes just this stream.
     */
    suspend fun execShell(shellCommand: String, timeoutMs: Long): String {
        val stream = openStream("shell:$shellCommand", timeoutMs)
        try {
            return withTimeout(timeoutMs) {
                val collected = ByteArrayOutputStream()
                for (msg in stream.incoming) {
                    if (msg.command == AdbProtocol.CMD_WRTE) collected.write(msg.payload)
                }
                // The channel only closes when the peer sent CLSE (normal end)
                // or when the session died (failAll). Distinguish the two.
                sessionError.get()?.let { throw it }
                collected.toString(Charsets.UTF_8.name())
            }
        } finally {
            forgetStream(stream)
        }
    }

    /** Idempotent: safe to call any number of times, from any coroutine. */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket.close() } // unblocks the demux reader
            demuxJob.cancel()
            failAll(IOException("ADB session closed"))
            sessionScope.cancel()
        }
    }

    private fun ensureOpen() {
        if (closed.get()) throw AdbException("ADB session is closed")
        sessionError.get()?.let { throw it }
    }

    private suspend fun openStream(destination: String, timeoutMs: Long): StreamState {
        ensureOpen()
        val localId = nextLocalId.getAndIncrement()
        val state = StreamState(localId)
        streams[localId] = state
        try {
            writeMessage(AdbProtocol.CMD_OPEN, localId, 0, destination.toByteArray(Charsets.UTF_8))
            withTimeout(timeoutMs) { state.openAck.await() }
            return state
        } catch (e: Throwable) {
            forgetStream(state)
            throw e
        }
    }

    /**
     * Drops local stream state, asking the peer to tear the stream down when it is still open from
     * our side (timeout/cancel path). The demux already removed and acknowledged server-closed
     * streams, so this never double-sends CLSE.
     */
    private suspend fun forgetStream(state: StreamState) {
        if (streams.remove(state.localId) != null) {
            if (state.remoteId != 0) {
                runCatching {
                    writeMessage(
                        AdbProtocol.CMD_CLSE,
                        state.localId,
                        state.remoteId,
                        AdbProtocol.EMPTY,
                    )
                }
            }
            state.incoming.close()
        }
    }

    private suspend fun writeMessage(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        writeMutex.withLock {
            AdbProtocol.writeMessage(output, command, arg0, arg1, payload)
        }
    }

    private suspend fun route(msg: AdbProtocol.Message) {
        when (msg.command) {
            AdbProtocol.CMD_OKAY -> {
                // Either the OPEN acknowledgement (openAck pending) or a
                // WRTE acknowledgement (no action needed).
                streams[msg.arg1]?.let { state ->
                    if (!state.openAck.isCompleted && msg.arg0 != 0) {
                        state.remoteId = msg.arg0
                        state.openAck.complete(msg.arg0)
                    }
                }
            }
            AdbProtocol.CMD_WRTE -> {
                streams[msg.arg1]?.let { state ->
                    writeMessage(AdbProtocol.CMD_OKAY, state.localId, msg.arg0, AdbProtocol.EMPTY)
                    if (state.remoteId == 0) state.remoteId = msg.arg0
                    state.incoming.trySend(msg)
                }
            }
            AdbProtocol.CMD_CLSE -> {
                streams.remove(msg.arg1)?.let { state ->
                    val remote = if (state.remoteId != 0) state.remoteId else msg.arg0
                    if (remote != 0) {
                        runCatching {
                            writeMessage(
                                AdbProtocol.CMD_CLSE,
                                state.localId,
                                remote,
                                AdbProtocol.EMPTY,
                            )
                        }
                    }
                    if (!state.openAck.isCompleted) {
                        state.openAck.completeExceptionally(
                            AdbException("stream closed before OPEN was acknowledged"),
                        )
                    }
                    state.incoming.close()
                }
            }
            else -> Unit // stray message (e.g. duplicate CNXN); ignore
        }
    }

    private fun failAll(cause: Throwable) {
        sessionError.compareAndSet(null, cause)
        for (state in streams.values) {
            state.openAck.completeExceptionally(cause)
            state.incoming.close(cause)
        }
        streams.clear()
    }

    private class StreamState(val localId: Int) {
        @Volatile var remoteId: Int = 0
        val openAck = CompletableDeferred<Int>()
        val incoming = Channel<AdbProtocol.Message>(Channel.UNLIMITED)
    }
}
