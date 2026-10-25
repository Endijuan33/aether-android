package id.mybase.aether.adb.pairing

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import id.mybase.aether.MainActivity
import id.mybase.aether.R

/** Native inline input is mutable by necessity; every other PendingIntent is immutable. */
class AetherPairingNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var replyPendingIntent: PendingIntent? = null
    private var replyIdentity: String? = null

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                    CHANNEL,
                    "Wireless Debugging pairing",
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply {
                    description =
                        "Enter a pairing code and track AETHER's Wireless Debugging connection."
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
        )
    }

    fun unavailableReason(): String? =
        when {
            Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED ->
                "Notification permission is required for inline pairing. Allow notifications or use in-app discovery."
            !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                "AETHER notifications are disabled. Enable them in Android settings or use in-app discovery."
            manager.getNotificationChannel(CHANNEL)?.importance ==
                NotificationManager.IMPORTANCE_NONE ->
                "The Wireless Debugging pairing channel is disabled. Enable this channel or use in-app discovery."
            else -> null
        }

    fun build(state: WirelessPairingState): Notification {
        val content = state.notificationContent()
        val identity = if (content.acceptsCode) "${state.sessionId}/${state.replyToken}" else null
        if (replyIdentity != identity) {
            replyPendingIntent?.cancel()
            replyPendingIntent = null
            replyIdentity = identity
        }
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_pairing_notification)
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(state.active)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        // This is a completion event, not an indefinitely maintained background connection badge.
        if (state.ready) builder.setTimeoutAfter(8_000)
        if (content.acceptsCode && state.replyToken != null) {
            val reply = actionIntent(AetherAdbPairingService.ACTION_REPLY, state)
            // Unique token in Intent identity prevents an old notification from submitting to a new
            // endpoint.
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val input =
                RemoteInput.Builder(CODE_KEY)
                    .setLabel("Wi-Fi pairing code")
                    .setAllowFreeFormInput(true)
                    .build()
            val pendingReply = PendingIntent.getForegroundService(context, 1, reply, flags)
            replyPendingIntent = pendingReply
            builder.addAction(
                NotificationCompat.Action.Builder(
                        0,
                        "PAIR",
                        pendingReply,
                    )
                    .addRemoteInput(input)
                    .setAuthenticationRequired(true)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build(),
            )
        }
        if (state.active) {
            val cancel =
                PendingIntent.getService(
                    context,
                    2,
                    actionIntent(AetherAdbPairingService.ACTION_CANCEL, state),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            builder.addAction(0, "Cancel", cancel).setDeleteIntent(cancel)
        }
        return builder.build()
    }

    fun settingsIntent(): Intent {
        createChannel()
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
    }

    private fun actionIntent(action: String, state: WirelessPairingState) =
        Intent(context, AetherAdbPairingService::class.java)
            .setPackage(context.packageName)
            .setAction(action)
            .setData(
                Uri.Builder()
                    .scheme("aether-pairing")
                    .authority(context.packageName)
                    .appendPath(state.sessionId ?: "none")
                    .appendPath(state.replyToken ?: "none")
                    .build(),
            )
            .putExtra(SESSION_KEY, state.sessionId)
            .putExtra(TOKEN_KEY, state.replyToken)

    companion object {
        const val CHANNEL = "aether_wireless_pairing"
        const val ID = 2001
        const val CODE_KEY = "aether_pairing_code"
        const val SESSION_KEY = "aether_pairing_session"
        const val TOKEN_KEY = "aether_pairing_token"

        /** Consume and remove SystemUI's secret payload. Never persist/redeliver this Intent. */
        fun consumeCode(intent: Intent): CharSequence? {
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CODE_KEY)
            intent.clipData = null
            return code
        }
    }
}
