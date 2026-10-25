package id.mybase.aether

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.mybase.aether.adb.pairing.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android notification/RemoteInput/PendingIntent APIs, independent of Wi-Fi or adbd. */
@RunWith(AndroidJUnit4::class)
class PairingNotificationPlatformTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun waiting() =
        WirelessPairingState(
            phase = PairingPhase.WAITING_FOR_PAIRING_CODE,
            sessionId = "platform-session",
            replyToken = "platform-token",
            pairingEndpoint = DiscoveredAdbEndpoint("platform", "127.0.0.1", 37001),
        )

    @Test
    fun inlineReplyContainsNativeRemoteInputAndOnlyReplyIsMutable() {
        val notifications = AetherPairingNotifications(context)
        notifications.createChannel()
        val built = notifications.build(waiting())
        assertEquals(
            "AETHER — Pairing Service Found",
            built.extras.getString(android.app.Notification.EXTRA_TITLE),
        )
        val reply = requireNotNull(NotificationCompat.getAction(built, 0))
        assertEquals("PAIR", reply.title.toString())
        assertEquals(AetherPairingNotifications.CODE_KEY, reply.remoteInputs!!.single().resultKey)
        assertEquals("Wi-Fi pairing code", reply.remoteInputs!!.single().label.toString())
        assertFalse(requireNotNull(reply.actionIntent).isImmutable)
        assertTrue(requireNotNull(NotificationCompat.getAction(built, 1)?.actionIntent).isImmutable)
        assertTrue(built.contentIntent.isImmutable)
        assertTrue(built.deleteIntent.isImmutable)
    }

    @Test
    fun replyIsAvailableBeforeAnyEndpointHasBeenDiscovered() {
        val notifications = AetherPairingNotifications(context)
        notifications.createChannel()
        for (phase in listOf(PairingPhase.PREPARING, PairingPhase.WAITING_FOR_PAIRING_CODE)) {
            val notification =
                notifications.build(waiting().copy(phase = phase, pairingEndpoint = null))
            val reply = requireNotNull(NotificationCompat.getAction(notification, 0))
            assertEquals("PAIR", reply.title.toString())
            assertEquals(
                AetherPairingNotifications.CODE_KEY,
                reply.remoteInputs!!.single().resultKey,
            )
            assertFalse(requireNotNull(reply.actionIntent).isImmutable)
        }
    }

    @Test
    fun remoteInputExtractionConsumesPayloadAndValidatesStrictDigits() {
        val input = RemoteInput.Builder(AetherPairingNotifications.CODE_KEY).build()
        for (code in listOf("000001", "123 56", "", "1234567")) {
            val intent = Intent(context, AetherAdbPairingService::class.java)
            RemoteInput.addResultsToIntent(
                arrayOf(input),
                intent,
                Bundle().apply { putCharSequence(AetherPairingNotifications.CODE_KEY, code) },
            )
            val extracted = AetherPairingNotifications.consumeCode(intent)
            assertEquals(code, extracted.toString())
            assertEquals(code == "000001", PairingCode.isValid(extracted))
            assertNull(intent.clipData)
            assertNull(AetherPairingNotifications.consumeCode(intent))
        }
    }

    @Test
    fun missingInlineReplyHasNoCodeAndCannotBePaired() {
        assertNull(AetherPairingNotifications.consumeCode(Intent()))
        assertFalse(PairingCode.isValid(null))
    }

    @Test
    fun oldEndpointReplyPendingIntentIsDistinctFromNewEndpoint() {
        val notifications = AetherPairingNotifications(context)
        notifications.createChannel()
        val old =
            requireNotNull(
                NotificationCompat.getAction(notifications.build(waiting()), 0)?.actionIntent,
            )
        val newer =
            requireNotNull(
                NotificationCompat.getAction(
                        notifications.build(waiting().copy(replyToken = "new-token")),
                        0,
                    )
                    ?.actionIntent,
            )
        assertNotEquals(old, newer)
    }

    @Test
    fun nonWaitingStatesDoNotExposeCodeInput() {
        val notifications = AetherPairingNotifications(context)
        notifications.createChannel()
        for (phase in
            listOf(
                PairingPhase.SEARCHING_PAIRING_SERVICE,
                PairingPhase.PAIRING_IN_PROGRESS,
                PairingPhase.CONNECTING,
                PairingPhase.CONNECTION_FAILED,
            )) {
            val notification = notifications.build(WirelessPairingState(phase = phase))
            for (index in 0 until NotificationCompat.getActionCount(notification)) {
                assertNull(
                    requireNotNull(NotificationCompat.getAction(notification, index)).remoteInputs,
                )
            }
        }
    }

    @Test
    fun notificationReplacementRevokesObsoleteReply() {
        val notifications = AetherPairingNotifications(context)
        notifications.createChannel()
        val old =
            requireNotNull(
                NotificationCompat.getAction(notifications.build(waiting()), 0)?.actionIntent,
            )
        notifications.build(waiting().copy(replyToken = "replacement-token"))
        assertThrows(android.app.PendingIntent.CanceledException::class.java) { old.send() }
    }

    @Test
    fun connectedCompletionNotificationExpiresInsteadOfClaimingIndefiniteConnectivity() {
        val notifications = AetherPairingNotifications(context)
        notifications.createChannel()
        val state =
            WirelessPairingState(
                phase = PairingPhase.CONNECTED,
                transportVerified = true,
                shellUid = 2000,
            )
        val notification = notifications.build(state)
        assertEquals(8_000L, notification.timeoutAfter)
        assertTrue(
            notification.extras
                .getCharSequence(android.app.Notification.EXTRA_TEXT)
                .toString()
                .contains("verified"),
        )
    }

    @Test
    fun channelAndServiceUseSafePlatformConfiguration() {
        val notifications = AetherPairingNotifications(context)
        notifications.createChannel()
        val channel =
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(AetherPairingNotifications.CHANNEL)
        assertNotNull(channel)
        val service =
            context.packageManager.getServiceInfo(
                ComponentName(context, AetherAdbPairingService::class.java),
                0,
            )
        assertFalse(service.exported)
        if (Build.VERSION.SDK_INT >= 34)
            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
                service.foregroundServiceType,
            )
    }
}
