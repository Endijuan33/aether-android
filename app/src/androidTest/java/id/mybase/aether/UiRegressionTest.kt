package id.mybase.aether

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.mybase.aether.adb.*
import id.mybase.aether.domain.SafetyRating
import id.mybase.aether.persistence.AetherPreferences
import id.mybase.aether.ui.components.SafetyDot
import id.mybase.aether.ui.connection.ConnectionViewModel
import id.mybase.aether.ui.pairing.PairingDialog
import id.mybase.aether.ui.theme.AetherTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs real Compose editing, navigation and semantics. It does not prove adbd interoperability. */
@RunWith(AndroidJUnit4::class)
class UiRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pairingInputRequiresSixDigitsAndSuccessDismissesDialog() {
        val shell =
            object : AdbShell {
                override val state =
                    MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)

                override suspend fun connect(host: String, port: Int, timeoutMs: Long) =
                    Result.success(Unit)

                override suspend fun disconnect() = Unit

                override suspend fun exec(vararg args: String, timeoutMs: Long) =
                    Result.failure<String>(IllegalStateException("Disconnected"))
            }
        val codes = mutableListOf<String>()
        val pairing =
            object : AdbPairingClient {
                override suspend fun pair(
                    host: String,
                    port: Int,
                    pairingCode: String,
                    timeoutMs: Long,
                ): PairingResult {
                    codes.add(pairingCode)
                    return PairingResult.Success
                }
            }
        val vm = ConnectionViewModel(shell, pairing, AetherPreferences(compose.activity))
        vm.beginPairing("127.0.0.1", 37123)
        compose.setContent {
            AetherTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        Button(onClick = { nav.navigate("pair") }) { Text("Pair now") }
                    }
                    dialog("pair") { PairingDialog(vm, nav) }
                }
            }
        }
        compose.onNodeWithText("Pair now").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Six-digit pairing code").performTextInput("12345")
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Six-digit pairing code").performTextInput("6")
        compose.onNodeWithText("Confirm").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { vm.pairingSucceeded.value }
        compose.runOnIdle { assertEquals(listOf("123456"), codes) }
        compose.onNodeWithText("Pair now").assertIsDisplayed()
        compose.onNodeWithContentDescription("Six-digit pairing code").assertDoesNotExist()
    }

    @Test
    fun safetyClassificationHasAccessibleName() {
        compose.setContent { AetherTheme { SafetyDot(SafetyRating.UNKNOWN) } }
        compose.onNodeWithContentDescription("Safety: unknown").assertExists()
    }
}
