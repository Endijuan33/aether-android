package id.mybase.aether.ui.pairing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import id.mybase.aether.adb.pairing.*
import kotlinx.coroutines.launch

class PairingActions(
    val start: (Boolean, Boolean) -> Unit,
    val cancel: () -> Unit,
    val openDeveloperOptions: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openAppSettings: () -> Unit = {},
)

/** Registered at the NavHost so a permission result survives navigation from the dashboard. */
@Composable
fun rememberPairingActions(controller: WirelessPairingController): PairingActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inApp by rememberSaveable { mutableStateOf(false) }
    var reconnect by rememberSaveable { mutableStateOf(false) }

    fun performStart() {
        if (
            Build.VERSION.SDK_INT >= 37 &&
                context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            controller.permissionRequired(
                "Local network access is required to discover Wireless Debugging. Allow it in Android app permissions; manual setup cannot bypass it.",
            )
            return
        }
        if (inApp) {
            controller.start(reconnect)
            return
        }
        val notifications = AetherPairingNotifications(context)
        notifications.createChannel()
        val reason = notifications.unavailableReason()
        if (reason != null) {
            controller.permissionRequired(reason)
            return
        }
        try {
            AetherAdbPairingService.start(context, reconnect)
        } catch (_: RuntimeException) {
            controller.reportStartFailure()
        }
    }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            performStart()
        }
    return PairingActions(
        start = { useInApp, reconnectOnly ->
            if (!controller.state.value.active && !controller.state.value.ready) {
                inApp = useInApp
                reconnect = reconnectOnly
                val missing = buildList {
                    if (
                        Build.VERSION.SDK_INT >= 37 &&
                            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                                PackageManager.PERMISSION_GRANTED
                    )
                        add(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    if (
                        !useInApp &&
                            Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                    )
                        add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (missing.isEmpty()) performStart()
                else {
                    controller.permissionRequired(
                        "Allow the requested permissions to start notification pairing. In-app discovery is available if notifications are denied.",
                    )
                    permissions.launch(missing.toTypedArray())
                }
            }
        },
        cancel = { scope.launch { controller.cancel() } },
        openDeveloperOptions = {
            openDeveloperSettings(context)
        },
        openNotificationSettings = {
            if (!openSettings(context, AetherPairingNotifications(context).settingsIntent())) {
                openSettings(
                    context,
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        },
        openAppSettings = {
            openSettings(
                context,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null)),
            )
        },
    )
}

private fun openSettings(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
