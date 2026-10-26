package id.mybase.aether.ui.pairing

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast

enum class DeveloperOptionsState {
    ENABLED,
    DISABLED,
    UNAVAILABLE,
}

enum class SettingsDestination {
    DEVELOPER_OPTIONS,
    SETTINGS,
    UNAVAILABLE,
}

/** No connection/controller dependency: leaving and resuming cannot restart pairing here. */
class DeveloperSettingsNavigator(private val launch: (String) -> Boolean) {
    fun open(): SettingsDestination =
        when {
            launch(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) ->
                SettingsDestination.DEVELOPER_OPTIONS
            launch(Settings.ACTION_SETTINGS) -> SettingsDestination.SETTINGS
            else -> SettingsDestination.UNAVAILABLE
        }
}

fun developerOptionsState(context: Context): DeveloperOptionsState =
    try {
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
    } catch (_: SecurityException) {
        DeveloperOptionsState.UNAVAILABLE
    } catch (_: RuntimeException) {
        DeveloperOptionsState.UNAVAILABLE
    }

/**
 * Resolve a public Settings action to an exported system activity, never a private OEM component.
 */
fun launchNativeSettings(context: Context, action: String): Boolean =
    try {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved =
            context.packageManager
                .resolveActivity(
                    intent,
                    PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY,
                )
                ?.activityInfo
        if (resolved == null || !resolved.exported || resolved.packageName == context.packageName)
            false
        else {
            intent.component = ComponentName(resolved.packageName, resolved.name)
            context.startActivity(intent)
            true
        }
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

fun openDeveloperSettings(context: Context): SettingsDestination {
    val result = DeveloperSettingsNavigator { launchNativeSettings(context, it) }.open()
    val message =
        when (result) {
            SettingsDestination.SETTINGS ->
                "Open Developer options in Settings. If hidden, open About phone and tap Build number (or OS version) seven times."
            SettingsDestination.UNAVAILABLE ->
                "Open Android Settings manually, then Developer options. If hidden, enable it from About phone."
            SettingsDestination.DEVELOPER_OPTIONS ->
                if (developerOptionsState(context) == DeveloperOptionsState.DISABLED)
                    "Enable Developer options manually in Settings → About phone → Build number (or OS version)."
                else null
        }
    message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    return result
}
