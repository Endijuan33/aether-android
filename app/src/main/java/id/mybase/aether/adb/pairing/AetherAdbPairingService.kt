package id.mybase.aether.adb.pairing

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import id.mybase.aether.di.AppContainer
import kotlinx.coroutines.*

/** Short, user-initiated pairing foreground service. Never restarts or replays a submitted code. */
class AetherAdbPairingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: WirelessPairingController
    private lateinit var notifications: AetherPairingNotifications
    private var observing = false
    private var ownsSession = false
    private var completed = false
    private var latestStartId = 0
    private var ownedSessionId: String? = null

    override fun onCreate() {
        super.onCreate()
        controller = AppContainer.get(this).wirelessPairing
        notifications = AetherPairingNotifications(this)
        notifications.createChannel()
        AndroidPairingDiagnostics.record(
            PairingDiagnostic(PairingDiagnosticEvent.SERVICE_STARTED, controller.state.value.phase),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent == null) {
            stopSelf(latestStartId)
            return START_NOT_STICKY
        }
        // All getForegroundService deliveries must meet the foreground deadline, including stale
        // replies.
        try {
            val notification = notifications.build(controller.state.value)
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(
                    AetherPairingNotifications.ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
                )
            else startForeground(AetherPairingNotifications.ID, notification)
            AndroidPairingDiagnostics.record(
                PairingDiagnostic(
                    PairingDiagnosticEvent.NOTIFICATION_POSTED,
                    controller.state.value.phase,
                ),
            )
        } catch (e: RuntimeException) {
            AndroidPairingDiagnostics.record(
                PairingDiagnostic(
                    PairingDiagnosticEvent.NOTIFICATION_FAILED,
                    controller.state.value.phase,
                ),
            )
            controller.reportStartFailure()
            stopSelf(latestStartId)
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START,
            ACTION_RECONNECT -> {
                val unavailable = notifications.unavailableReason()
                if (unavailable != null) {
                    controller.permissionRequired(unavailable)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(latestStartId)
                    return START_NOT_STICKY
                }
                if (controller.start(intent.action == ACTION_RECONNECT)) {
                    ownsSession = true
                    completed = false
                    ownedSessionId = controller.state.value.sessionId
                }
                if (!ownsSession) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(latestStartId)
                }
            }
            ACTION_REPLY -> {
                AndroidPairingDiagnostics.record(
                    PairingDiagnostic(
                        PairingDiagnosticEvent.REMOTE_INPUT_RECEIVED,
                        controller.state.value.phase,
                    ),
                )
                val session = intent.getStringExtra(AetherPairingNotifications.SESSION_KEY)
                val token = intent.getStringExtra(AetherPairingNotifications.TOKEN_KEY)
                val code = AetherPairingNotifications.consumeCode(intent)
                if (ownsSession && session == ownedSessionId)
                    controller.submit(session, token, code)
                intent.replaceExtras(null)
                // A recreated process has no matching session/token: do not revive an old pairing
                // attempt.
                if (!ownsSession || !controller.state.value.active) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(latestStartId)
                }
            }
            ACTION_CANCEL -> {
                if (
                    ownsSession &&
                        intent.getStringExtra(AetherPairingNotifications.SESSION_KEY) ==
                            ownedSessionId &&
                        ownedSessionId == controller.state.value.sessionId
                ) {
                    scope.launch { controller.cancel() }
                }
                if (!ownsSession) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(latestStartId)
                }
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(latestStartId)
            }
        }
        if (!observing && ownsSession) {
            observing = true
            scope.launch {
                controller.state.collect { state ->
                    if (state.sessionId != ownedSessionId) return@collect
                    if (!state.active) {
                        completed = true
                        stopForeground(STOP_FOREGROUND_DETACH)
                        publish(state)
                        stopSelf(latestStartId)
                    } else {
                        if (!publish(state)) controller.notificationFailed()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun publish(state: WirelessPairingState): Boolean =
        try {
            if (state.active && notifications.unavailableReason() != null) {
                throw IllegalStateException("Pairing notification unavailable")
            }
            getSystemService(android.app.NotificationManager::class.java)
                .notify(AetherPairingNotifications.ID, notifications.build(state))
            AndroidPairingDiagnostics.record(
                PairingDiagnostic(PairingDiagnosticEvent.NOTIFICATION_POSTED, state.phase),
            )
            true
        } catch (_: RuntimeException) {
            AndroidPairingDiagnostics.record(
                PairingDiagnostic(PairingDiagnosticEvent.NOTIFICATION_FAILED, state.phase),
            )
            false
        }

    override fun onTimeout(startId: Int) {
        scope.launch {
            controller.timeout()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(latestStartId)
        }
    }

    override fun onDestroy() {
        AndroidPairingDiagnostics.record(
            PairingDiagnostic(PairingDiagnosticEvent.SERVICE_STOPPED, controller.state.value.phase),
        )
        scope.cancel()
        val ownedId = ownedSessionId
        if (ownsSession && !completed && controller.state.value.sessionId == ownedId) {
            AppContainer.get(this).applicationScope.launch {
                if (controller.state.value.sessionId == ownedId)
                    controller.cancel("Pairing service stopped. Open AETHER to restart discovery.")
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "id.mybase.aether.PAIR_AND_CONNECT"
        const val ACTION_RECONNECT = "id.mybase.aether.RECONNECT"
        const val ACTION_REPLY = "id.mybase.aether.PAIRING_REPLY"
        const val ACTION_CANCEL = "id.mybase.aether.CANCEL_PAIRING"

        fun start(context: Context, reconnect: Boolean = false) {
            context.startForegroundService(
                Intent(context, AetherAdbPairingService::class.java)
                    .setAction(if (reconnect) ACTION_RECONNECT else ACTION_START),
            )
        }
    }
}
