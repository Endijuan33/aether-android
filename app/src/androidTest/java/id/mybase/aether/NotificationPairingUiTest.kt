package id.mybase.aether

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.mybase.aether.adb.*
import id.mybase.aether.adb.pairing.*
import id.mybase.aether.ui.pairing.PairingActions
import id.mybase.aether.ui.setup.SetupGuideScreen
import id.mybase.aether.ui.theme.AetherTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPairingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val found = MutableStateFlow<DiscoveredAdbEndpoint?>(null)
    private var pairingCalls = 0
    private val pairer =
        object : AdbPairingClient {
            override suspend fun pair(
                host: String,
                port: Int,
                pairingCode: String,
                timeoutMs: Long,
            ): PairingResult {
                pairingCalls++
                awaitCancellation()
            }
        }
    private val shell =
        object : AdbShell {
            override val state =
                MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)

            override suspend fun connect(host: String, port: Int, timeoutMs: Long) =
                Result.failure<Unit>(Exception("unused"))

            override suspend fun disconnect() {
                state.value = AdbConnectionState.Disconnected
            }

            override suspend fun exec(vararg args: String, timeoutMs: Long) =
                Result.failure<String>(Exception("unused"))
        }
    private val discovery =
        object : AdbServiceDiscovery {
            override fun watch(type: AdbServiceType): Flow<DiscoveredAdbEndpoint?> = found
        }
    private val controller = WirelessPairingController(pairer, shell, discovery, scope)
    private val requested = mutableListOf<Pair<Boolean, Boolean>>()
    private val actions =
        PairingActions(
            start = { inApp, reconnect ->
                requested += inApp to reconnect
                controller.start(reconnect)
            },
            cancel = { scope.launch { controller.cancel() } },
            openDeveloperOptions = {},
            openNotificationSettings = {},
        )

    @After
    fun cleanup() {
        scope.cancel()
    }

    private fun showGuide() {
        compose.setContent {
            AetherTheme { SetupGuideScreen(controller, actions, rememberNavController()) }
        }
    }

    @Test
    fun primaryPairingRequestsNotificationFlowAndShowsNoManualPorts() {
        showGuide()
        compose.onNodeWithText("PAIR & CONNECT").performScrollTo().performClick()
        compose.onNodeWithText("Waiting for pairing code — discovery is running").assertExists()
        assertEquals(listOf(false to false), requested)
        compose.onNodeWithText("Device IP / host").assertDoesNotExist()
        compose.onNodeWithText("Pairing port").assertDoesNotExist()
        compose.onNodeWithText("ADB connection port").assertDoesNotExist()
    }

    @Test
    fun permissionFailureExplainsLimitationAndOffersInAppFallback() {
        compose.runOnUiThread {
            controller.permissionRequired("Notification permission denied. Use in-app discovery.")
        }
        showGuide()
        compose
            .onNodeWithText("Notification permission denied. Use in-app discovery.")
            .assertExists()
        compose.onNodeWithText("Start in-app discovery fallback").performScrollTo().performClick()
        assertEquals(listOf(true to false), requested)
        // The fallback accepts a code before discovery resolves an endpoint.
        compose.onNodeWithText("Waiting for pairing code — discovery is running").assertExists()
        compose.onNodeWithText("Six-digit pairing code").assertExists()
    }

    @Test
    fun discoveredFallbackInputRequiresSixDigitsAndPreventsDuplicateSubmission() {
        showGuide()
        compose.onNodeWithText("Start in-app discovery fallback").performScrollTo().performClick()
        compose.runOnUiThread { found.value = DiscoveredAdbEndpoint("local", "127.0.0.1", 37001) }
        compose.onNodeWithText("Six-digit pairing code").performScrollTo().performTextInput("12345")
        compose.onNodeWithText("Submit pairing code").assertIsNotEnabled()
        compose.onNodeWithText("Six-digit pairing code").performScrollTo().performTextInput("6")
        compose
            .onNodeWithText("Submit pairing code")
            .assertIsEnabled()
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Pairing authentication in progress…").assertExists()
        compose.onNodeWithText("Submit pairing code").assertDoesNotExist()
        assertEquals(1, pairingCalls)
    }

    @Test
    fun cancellationRestoresPrimaryPairingAndRemovesCodeEditor() {
        showGuide()
        compose.onNodeWithText("Start in-app discovery fallback").performScrollTo().performClick()
        compose.runOnUiThread { found.value = DiscoveredAdbEndpoint("local", "127.0.0.1", 37001) }
        compose.onNodeWithText("Cancel pairing").performScrollTo().performClick()
        compose.onNodeWithText("Pairing cancelled").assertExists()
        compose.onNodeWithText("PAIR & CONNECT").assertIsEnabled()
        compose.onNodeWithText("Six-digit pairing code").assertDoesNotExist()
    }

    @Test
    fun inAppCodeSubmissionCanPrecedeDiscovery() {
        showGuide()
        compose.onNodeWithText("Start in-app discovery fallback").performScrollTo().performClick()
        compose
            .onNodeWithText("Six-digit pairing code")
            .performScrollTo()
            .performTextInput("123456")
        compose
            .onNodeWithText("Submit pairing code")
            .assertIsEnabled()
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Searching for pairing service…").assertExists()
        assertEquals(0, pairingCalls)
        compose.runOnUiThread { found.value = DiscoveredAdbEndpoint("local", "127.0.0.1", 37001) }
        compose.onNodeWithText("Pairing authentication in progress…").assertExists()
        assertEquals(1, pairingCalls)
    }

    @Test
    fun activityRecreationKeepsDiscoverySessionAndClearsUnsubmittedCode() {
        showGuide()
        compose.onNodeWithText("Start in-app discovery fallback").performScrollTo().performClick()
        compose.onNodeWithText("Six-digit pairing code").performScrollTo().performTextInput("12345")
        val originalSession = controller.state.value.sessionId
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                AetherTheme {
                    SetupGuideScreen(controller, actions, rememberNavController())
                }
            }
        }
        compose.waitForIdle()
        assertEquals(originalSession, controller.state.value.sessionId)
        assertTrue(controller.state.value.active)
        compose.onNodeWithText("Six-digit pairing code").assertDoesNotExist()
        compose.onNodeWithText("Use in-app code input").performScrollTo().performClick()
        compose
            .onNodeWithText("Six-digit pairing code")
            .performScrollTo()
            .performTextInput("123456")
        compose
            .onNodeWithText("Submit pairing code")
            .assertIsEnabled()
            .performScrollTo()
            .performClick()
        assertEquals(0, pairingCalls)
        compose.runOnUiThread { found.value = DiscoveredAdbEndpoint("local", "127.0.0.1", 37001) }
        compose.onNodeWithText("Pairing authentication in progress…").assertExists()
        assertEquals(1, pairingCalls)
    }
}
