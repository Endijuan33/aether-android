package id.mybase.aether.ui.navigation

import android.net.Uri

/**
 * Navigation routes for the app.
 *
 * Full-screen destinations are plain routes; [PAIRING_DIALOG] and [PREVIEW_DIALOG] are dialog
 * destinations shown on top of the current screen.
 */
object Routes {
    const val DASHBOARD = "dashboard"
    const val ONBOARDING = "onboarding"
    const val SETUP_GUIDE = "setup_guide"
    const val ADVANCED_PAIRING = "advanced_pairing"
    const val DIAGNOSTICS = "diagnostics"
    const val APPS = "apps"
    const val APP_DETAILS = "app_details/{packageName}"
    const val DEBLOAT = "debloat"
    const val PROGRESS = "progress"
    const val HISTORY = "history"
    const val RECOVERY = "recovery"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    /** Dialog destinations. */
    const val PAIRING_DIALOG = "pairing"
    const val PREVIEW_DIALOG = "preview"

    const val ARG_PACKAGE_NAME = "packageName"

    fun appDetails(packageName: String): String = "app_details/${Uri.encode(packageName)}"
}
