package id.mybase.aether

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.mybase.aether.ui.pairing.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeveloperSettingsPlatformTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun intentIsExternalAndLaunchExceptionsAreHandled() {
        var captured: Intent? = null
        val recording =
            object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) {
                    captured = intent
                }
            }
        assumeTrue(launchNativeSettings(recording, Settings.ACTION_SETTINGS))
        assertEquals(Settings.ACTION_SETTINGS, captured!!.action)
        assertNotEquals(context.packageName, captured!!.component!!.packageName)
        assertTrue(captured!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val failing =
                object : ContextWrapper(context) {
                    override fun startActivity(intent: Intent) {
                        throw failure
                    }
                }
            assertFalse(launchNativeSettings(failing, Settings.ACTION_SETTINGS))
        }
        assertFalse(launchNativeSettings(context, "id.mybase.aether.NO_SETTINGS_HANDLER"))
    }

    @Test
    fun actualSettingsActivityOpensOutsideAetherAndCanReturn() {
        val result = DeveloperSettingsNavigator { launchNativeSettings(context, it) }.open()
        assumeTrue(result != SettingsDestination.UNAVAILABLE)
        try {
            var external = false
            repeat(40) {
                if (!external) {
                    Thread.sleep(100)
                    val owner =
                        instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
                    external =
                        owner != null &&
                            owner != context.packageName &&
                            owner.contains("settings", ignoreCase = true)
                }
            }
            assertTrue("Native Settings window was not observed", external)
        } finally {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                    ),
            )
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun developerOptionsIndicatorMatchesPublicSettingWithoutChangingIt() {
        val expected =
            when (
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    -1,
                )
            ) {
                1 -> DeveloperOptionsState.ENABLED
                0 -> DeveloperOptionsState.DISABLED
                else -> DeveloperOptionsState.UNAVAILABLE
            }
        assertEquals(expected, developerOptionsState(context))
    }
}
