package id.mybase.aether

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.RemoteInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.mybase.aether.adb.pairing.*
import id.mybase.aether.di.AppContainer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual foreground-service entry/cleanup with a stale reply, never a real discovery/pair attempt.
 */
@RunWith(AndroidJUnit4::class)
class PairingServicePlatformTest {
    @Test
    fun staleRemoteInputCannotRestartPairingAndServiceStops() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(ComponentActivity::class.java).use { activity ->
            activity.onActivity { foreground ->
                val controller = AppContainer.get(foreground).wirelessPairing
                assertEquals(PairingPhase.IDLE, controller.state.value.phase)
                val intent =
                    Intent(foreground, AetherAdbPairingService::class.java)
                        .setAction(AetherAdbPairingService.ACTION_REPLY)
                        .putExtra(AetherPairingNotifications.SESSION_KEY, "dead-session")
                        .putExtra(AetherPairingNotifications.TOKEN_KEY, "dead-token")
                RemoteInput.addResultsToIntent(
                    arrayOf(RemoteInput.Builder(AetherPairingNotifications.CODE_KEY).build()),
                    intent,
                    Bundle().apply {
                        putCharSequence(AetherPairingNotifications.CODE_KEY, "123456")
                    },
                )
                foreground.startForegroundService(intent)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            @Suppress("DEPRECATION")
            fun running(): Boolean =
                context.getSystemService(ActivityManager::class.java).getRunningServices(100).any {
                    it.service.className == AetherAdbPairingService::class.java.name
                }
            val deadline = System.currentTimeMillis() + 5_000
            while (running() && System.currentTimeMillis() < deadline) Thread.sleep(25)
            assertFalse("Stale reply must not leave a foreground service running", running())
            assertEquals(
                PairingPhase.IDLE,
                AppContainer.get(context).wirelessPairing.state.value.phase,
            )
        }
    }
}
